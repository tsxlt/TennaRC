package lt.tsx.tennarc.tv.androidtv

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.KnownApps
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvPicture

internal class AndroidTvAdb(
    private val link: AdbLink,
    private val scope: CoroutineScope,
    private val host: String,
    private val known: KnownApps? = null,
    private val notify: (Notice) -> Unit = {},
) {
    data class Reading(
        val link: DevLink = DevLink.Off,
        val apps: List<TvApp>? = null,
        val inputs: List<TvApp>? = null,
        val picture: TvPicture? = null,
    ) {
        private val live: Boolean get() = link == DevLink.Up || link == DevLink.Connecting

        val features: Set<Feature>
            get() = buildSet {
                if (!live) return@buildSet
                if (apps != null) add(Feature.APP_LIST)
                if (picture != null) add(Feature.PICTURE)
            }
    }

    private val mutableState = MutableStateFlow(Reading(link.status.value).withKnown())
    val state: StateFlow<Reading> = mutableState.asStateFlow()

    private var handler: String? = null
    private var handlerAsked = false

    var onlyHandler = false
        private set

    private var backlight: TvBacklight? = null
    private var backlightAsked = false

    private val reading = Mutex()

    init {
        scope.launch {
            link.status.collect { status ->
                mutableState.update { reading ->
                    when (status) {
                        DevLink.Off -> Reading(status).also { forget() }
                        is DevLink.Failed -> Reading(status, apps = reading.apps, inputs = reading.inputs)
                        is DevLink.Down -> if (status.problem != null) reading.copy(link = status, picture = null) else reading.copy(link = status)
                        else -> reading.copy(link = status)
                    }
                }
            }
        }
        scope.launch {
            link.connections.drop(1).collect {
                handlerAsked = false
                backlightAsked = false
                read(apps = true)
            }
        }
    }

    fun refresh() {
        if (link.status.value == DevLink.Up) scope.launch { read(apps = false) }
    }

    suspend fun switchInput(id: String): Boolean? {
        val inputId = inputIdOf(id) ?: return false
        if (!handlerAsked) {
            val handlers = inputHandlers(link.call(INPUT_HANDLERS_COMMAND) ?: return null)
            handler = handlers.firstOrNull()
            onlyHandler = handlers.size == 1
            handlerAsked = true
        }
        val output = link.call(passthroughCommand(inputId, handler)) ?: return null
        return started(output).also { if (!it) Log.w(TAG, "the TV didn't switch to $inputId: $output") }
    }

    fun setBacklight(level: Int) {
        val keys = backlight ?: return notify(Notice.COMMAND_FAILED)
        val clamped = level.coerceIn(0, 100)
        val before = mutableState.value.picture
        mutableState.update { it.copy(picture = TvPicture(null, clamped)) }
        scope.launch {
            val output = link.call(keys.command(clamped))
            if (output == null || !keys.took(output)) {
                Log.w(TAG, "the TV didn't take backlight $clamped: $output")
                notify(Notice.COMMAND_FAILED)
                mutableState.update { it.copy(picture = before) }
            }
            reading.withLock { readBacklight(keys) }
        }
    }

    private suspend fun read(apps: Boolean) = reading.withLock {
        try {
            readInputs()
            if (!backlightAsked) findBacklight()
            backlight?.let { readBacklight(it) }
            if (apps) readApps()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "reading the TV over ADB failed", e)
        }
    }

    private suspend fun readInputs() {
        val output = link.call(INPUTS_COMMAND) ?: return
        mutableState.update { it.copy(inputs = parseTvInputs(output).map(TvInput::app).ifEmpty { null }) }
        remember()
    }

    private suspend fun readApps() {
        val output = link.call(APPS_COMMAND, APPS_TIMEOUT_MS) ?: return
        val apps = parseTvApps(output).ifEmpty { return }
        mutableState.update { it.copy(apps = apps) }
        remember()
    }

    private suspend fun findBacklight() {
        val output = link.call(TvBacklight.PROBE) ?: return
        backlightAsked = true
        backlight = TvBacklight.find(output)
    }

    private suspend fun readBacklight(keys: TvBacklight) {
        val output = link.call(keys.readCommand) ?: return
        val level = keys.parse(output)
        if (level == null) backlight = null
        mutableState.update { it.copy(picture = level?.let { TvPicture(null, it) }) }
    }

    private fun remember() {
        val reading = mutableState.value
        known?.put(host, KnownApps.Known(reading.apps.orEmpty(), reading.inputs.orEmpty(), emptyList()))
    }

    private fun forget() {
        handlerAsked = false
        onlyHandler = false
        backlightAsked = false
        backlight = null
        val last = known?.get(host) ?: return
        if (last.apps.isNotEmpty() || last.inputs.isNotEmpty()) known.put(host, KnownApps.Known(emptyList(), emptyList(), emptyList()))
    }

    private fun Reading.withKnown(): Reading {
        if (link == DevLink.Off) return this
        val last = known?.get(host) ?: return this
        return copy(apps = last.apps.ifEmpty { null }, inputs = last.inputs.ifEmpty { null })
    }

    private companion object {
        const val TAG = "Tenna"

        const val APPS_TIMEOUT_MS = 10_000L
    }
}
