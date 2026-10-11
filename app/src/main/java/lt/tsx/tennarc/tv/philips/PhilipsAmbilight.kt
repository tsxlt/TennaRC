package lt.tsx.tennarc.tv.philips

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.rethrowIfCancelled
import lt.tsx.tennarc.tv.Ambilight
import lt.tsx.tennarc.tv.AmbilightAccess
import lt.tsx.tennarc.tv.AmbilightStyle
import lt.tsx.tennarc.tv.Notice
import okhttp3.OkHttpClient
import java.security.SecureRandom

class PhilipsAmbilight(
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val ports: JointSpacePorts = JointSpacePorts(),
    private val notify: (Notice) -> Unit = {},
    http: () -> OkHttpClient = JointSpace::client,
) {
    private val mutableState = MutableStateFlow<Ambilight?>(null)
    val state: StateFlow<Ambilight?> = mutableState.asStateFlow()

    private val http by lazy(http)
    private val calls = Mutex()
    private var host: String? = null
    private var api: JointSpace? = null
    private var probing: Job? = null

    private var lightless = false

    private var asked: Pair<JointSpaceDevice, PairingOffer>? = null
    private var pinTimeout: Job? = null

    private val lastSettings = mutableMapOf<String, String>()

    fun connected(host: String) {
        this.host = host
        refresh()
    }

    fun refresh() {
        val api = api
        if (api == null) {
            val host = host ?: return
            if (probing?.isActive != true && !lightless) probing = scope.launch { calls.withLock { probe(host) } }
            return
        }
        if (mutableState.value?.access != AmbilightAccess.Paired) return
        scope.launch { calls.withLock { read(api) } }
    }

    fun set(style: AmbilightStyle) {
        val api = api ?: return
        if (mutableState.value?.access != AmbilightAccess.Paired) return
        val before = mutableState.value
        mutableState.update { light -> light?.copy(on = style != AmbilightStyle.OFF, style = if (style == AmbilightStyle.OFF) light.style else style) }
        scope.launch {
            calls.withLock {
                if (guarded { apply(api, style) } != true) {
                    notify(Notice.COMMAND_FAILED)
                    mutableState.update { light -> if (light?.access == AmbilightAccess.Paired) light.copy(on = before?.on, style = before?.style) else light }
                }
                if (mutableState.value?.access == AmbilightAccess.Paired) read(api)
            }
        }
    }

    fun pair() {
        val api = api ?: return
        scope.launch { calls.withLock { askForPin(api, wrong = false) } }
    }

    fun submitPin(pin: String) {
        val api = api ?: return
        val host = host ?: return
        val (device, offer) = asked ?: return
        scope.launch {
            calls.withLock {
                val login = try {
                    api.pairGrant(device, offer, pin.trim())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "JointSPACE pair/grant failed", e)
                    null
                }
                pinTimeout?.cancel()
                asked = null
                if (login == null) return@withLock askForPin(api, wrong = true)
                settings.setClientKey(loginKey(host), login.encode())
                api.login = login
                mutableState.value = Ambilight(AmbilightAccess.Paired)
                read(api)
            }
        }
    }

    fun forget(host: String) {
        settings.setClientKey(loginKey(host), null)
        val api = api ?: return
        api.login = null
        if (api.system?.needsPairing == true) mutableState.value = Ambilight(AmbilightAccess.NeedsPairing)
    }

    private suspend fun probe(host: String) {
        val login = JointSpaceLogin.decode(settings.clientKey(loginKey(host)))
        val found = JointSpace.probe(http, host, ports, login) ?: return
        val system = found.system ?: return
        if (!system.ambilight) {
            lightless = true
            return
        }
        Log.i(TAG, "JointSPACE ${system.apiVersion} at ${found.base}, ${system.osType ?: "unknown OS"}")
        api = found
        if (system.needsPairing && login == null) {
            mutableState.value = Ambilight(AmbilightAccess.NeedsPairing)
            return
        }
        mutableState.value = Ambilight(AmbilightAccess.Paired)
        read(found)
    }

    private suspend fun read(api: JointSpace) {
        val reading = guarded { api.ambilight() } ?: return
        reading.config?.let { config -> config.menuSetting?.let { lastSettings[config.styleName] = it } }
        mutableState.update { light ->
            light?.copy(on = reading.on ?: light.on, style = reading.config?.let(::ambilightStyle) ?: light.style)
        }
    }

    private suspend fun apply(api: JointSpace, style: AmbilightStyle): Boolean {
        if (style == AmbilightStyle.OFF) return api.setAmbilightPower(false)
        if (!api.setAmbilightPower(true)) return false
        return ambilightConfigs(style).any { config ->
            api.setAmbilightConfig(config.copy(menuSetting = lastSettings[config.styleName] ?: config.menuSetting))
        }
    }

    private suspend fun askForPin(api: JointSpace, wrong: Boolean) {
        val device = JointSpaceDevice(newDeviceId(), Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android")
        val offer = try {
            api.pairRequest(device)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "JointSPACE pair/request failed", e)
            mutableState.value = Ambilight(AmbilightAccess.NeedsPairing)
            notify(Notice.COMMAND_FAILED)
            return
        }
        asked = device to offer
        mutableState.value = Ambilight(AmbilightAccess.AwaitingPin(wrong))
        pinTimeout?.cancel()
        pinTimeout = scope.launch {
            delay(offer.timeoutSeconds * 1000L)
            asked = null
            mutableState.update { if (it?.access is AmbilightAccess.AwaitingPin) Ambilight(AmbilightAccess.NeedsPairing) else it }
        }
    }

    private suspend fun <T> guarded(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: JointSpaceUnauthorized) {
        Log.w(TAG, "the TV no longer takes the JointSPACE login")
        host?.let(::forget)
        mutableState.value = Ambilight(AmbilightAccess.NeedsPairing)
        null
    } catch (e: Exception) {
        e.rethrowIfCancelled()
        Log.w(TAG, "JointSPACE call failed", e)
        null
    }

    private companion object {
        const val TAG = "Tenna"
    }
}

internal fun loginKey(host: String) = "jointspace@$host"

private val random = SecureRandom()
private const val ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

private fun newDeviceId(): String = String(CharArray(16) { ID_CHARS[random.nextInt(ID_CHARS.length)] })
