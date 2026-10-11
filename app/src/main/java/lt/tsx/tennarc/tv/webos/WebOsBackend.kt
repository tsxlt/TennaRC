package lt.tsx.tennarc.tv.webos
import android.util.Log
import java.io.IOException
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.rethrowIfCancelled
import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.ConnectionKeeper
import lt.tsx.tennarc.tv.DlnaClient
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.tv.LinkQuality
import lt.tsx.tennarc.tv.LinkVerdict
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.TvApp
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvBackend
import lt.tsx.tennarc.tv.TvButton
import lt.tsx.tennarc.tv.TvChannel
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.icons.TvIconLoader
import lt.tsx.tennarc.tv.TvKeyboard
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvPicture
import lt.tsx.tennarc.tv.TvPower
import lt.tsx.tennarc.tv.TvProgramme
import lt.tsx.tennarc.tv.TvState
import lt.tsx.tennarc.tv.afterFailures
import lt.tsx.tennarc.tv.UpnpDevice
import lt.tsx.tennarc.tv.WEBOS_FEATURES
import lt.tsx.tennarc.tv.WakeOnLan
import lt.tsx.tennarc.tv.acceptsType
import lt.tsx.tennarc.tv.learnMacs
import lt.tsx.tennarc.tv.retryDelay
import lt.tsx.tennarc.tv.retryJitter
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

internal fun parsePower(state: String, processing: String): TvPower? = when {
    processing.contains("Power Off", ignoreCase = true) || processing.contains("Suspend", ignoreCase = true) ->
        TvPower.STANDBY
    state == "Active" || state == "Screen Saver" -> TvPower.ON
    state == "Screen Off" -> TvPower.SCREEN_OFF
    state == "Active Standby" || state == "Suspend" || state == "Power Off" -> TvPower.STANDBY
    else -> null
}

internal fun macsFrom(payload: JSONObject): List<String> =
    listOf("wiredInfo", "wifiInfo").mapNotNull { payload.optJSONObject(it)?.optString("macAddress")?.ifEmpty { null } }

internal fun playingFrom(playState: String?): Boolean? = when (playState) {
    "playing" -> true
    "paused", "unloaded" -> false
    else -> null
}

internal fun programmeFrom(payload: JSONObject, nowMs: Long): TvProgramme? {
    val list = payload.optJSONArray("programList") ?: return null
    val programmes = (0 until list.length()).mapNotNull { list.optJSONObject(it) }
    fun start(programme: JSONObject) = guideTime(programme, "localStartTime", "startTime")
    fun end(programme: JSONObject) = guideTime(programme, "localEndTime", "endTime")
    val on = programmes.firstOrNull { programme ->
        val from = start(programme) ?: return@firstOrNull false
        val to = end(programme) ?: return@firstOrNull false
        nowMs in from until to
    } ?: programmes.firstOrNull() ?: return null
    val title = on.optString("programName").ifEmpty { on.optString("title") }.ifBlank { return null }
    return TvProgramme(title.trim(), start(on), end(on))
}

private fun guideTime(programme: JSONObject, local: String, utc: String): Long? =
    guideTime(programme.optString(local), java.time.ZoneId.systemDefault()) ?: guideTime(programme.optString(utc), java.time.ZoneOffset.UTC)

private fun guideTime(text: String, zone: java.time.ZoneId): Long? {
    val parts = text.split(',').map { it.trim().toIntOrNull() ?: return null }
    if (parts.size < 5) return null
    return try {
        java.time.LocalDateTime.of(parts[0], parts[1], parts[2], parts[3], parts[4], parts.getOrElse(5) { 0 })
            .atZone(zone).toInstant().toEpochMilli()
    } catch (_: java.time.DateTimeException) {
        null
    }
}

class WebOsBackend(
    private val settings: Settings,
    private val tvHttp: OkHttpClient = TvHttp.tvClient(),
    lanHttp: OkHttpClient = TvHttp.lanClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : TvBackend {

    override val kind = TvKind.WEBOS
    override val features = WEBOS_FEATURES

    private val mutableState = MutableStateFlow(TvState(host = settings.prefs.value.host))
    override val state: StateFlow<TvState> = mutableState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<Notice> = mutableNotices.asSharedFlow()

    override val iconLoader: IconLoader = TvIconLoader(tvHttp) { mutableState.value.host }

    private val dlna = DlnaClient(lanHttp)
    private val finder = WebOsFinder(dlna)

    private var session: WebOsSession? = null

    private val imeLock = Mutex()
    private var moveX = 0f
    private var moveY = 0f
    private var scrollX = 0f
    private var scrollY = 0f
    private var pointerFlush: Job? = null

    private var renderer: UpnpDevice? = null
    private var castWatch: Job? = null
    private var castHasPlayed = false
    private var castStill = false
    private var lastRelocation = Long.MIN_VALUE / 2
    private var wokenAt = Long.MIN_VALUE / 2
    private val linkQuality = LinkQuality()
    @Volatile private var inputIds = emptyMap<String, String>()

    @Volatile private var playStates = emptyMap<String, String>()

    private val keeper = ConnectionKeeper(scope, busy = { mutableState.first { it.connection != Connection.AwaitingApproval } }) { allowPairing -> stayConnected(allowPairing) }

    override fun close() {
        keeper.reset()
        scope.coroutineContext.cancelChildren()
        session?.close()
        session = null
    }

    override fun onScreenStarted() {
        keeper.screenStarted()
        hold()
    }

    override fun onScreenStopped() {
        keeper.screenStopped()
        release()
    }

    override fun hold() {
        keeper.hold()
        if (mutableState.value.cast != null) watchCast()
    }

    override fun release() {
        if (keeper.release()) castWatch?.cancel()
    }

    override fun pair() = connect(allowPairing = true)

    override fun cancelPairing() {
        keeper.cancel()
        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
    }

    override fun retry() {
        mutableState.update { if (it.connection == Connection.Offline) it else it.copy(connection = Connection.Connecting) }
        connect(allowPairing = false)
    }

    override fun forgetPairing() {
        settings.setClientKey(mutableState.value.host, null)
        keeper.cancel()
        mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
    }

    private fun connect(allowPairing: Boolean) = keeper.connect(allowPairing)

    private suspend fun stayConnected(allowPairing: Boolean) {
        var failures = 0
        while (true) {
            val host = settings.prefs.value.host
            val key = settings.clientKey(host)
            if (key == null && !allowPairing) {
                mutableState.update { it.copy(connection = Connection.NeedsPairing()) }
                return
            }
            mutableState.update { if (it.connection == Connection.Offline) it else it.copy(connection = Connection.Connecting) }
            try {
                val opened = openSession(host, key, allowPrompt = allowPairing)
                settings.setClientKey(host, opened.key)
                session = opened
                failures = 0
                Log.i(TAG, "connected to $host")
                val features = if (opened.signed) WEBOS_FEATURES else WEBOS_FEATURES - Feature.PICTURE
                mutableState.update { it.copy(connection = Connection.Connected, features = features) }
                coroutineScope {
                    launch { quietly { opened.pointer() } }
                    launch { watchPower(opened) }
                    launch { watchForegroundApp(opened) }
                    launch { watchPlayState(opened) }
                    launch { watchVolume(opened) }
                    launch { watchKeyboard(opened) }
                    launch { loadApps(opened) }
                    launch { watchInputs(opened) }
                    launch { watchSoundOutput(opened) }
                    launch { watchPicture(opened) }
                    launch { watchChannels(opened) }
                    launch { learnIdentity(host) }
                    launch { learnMacs(opened, host) }
                    launch { watchLink(opened) }
                    opened.awaitClosed()
                    coroutineContext.cancelChildren()
                }
            } catch (_: PairingForgottenException) {
                Log.w(TAG, "$host no longer knows this phone")
                settings.setClientKey(host, null)
                mutableState.update { it.copy(connection = Connection.NeedsPairing(forgotten = true)) }
                return
            } catch (_: PairingTimeoutException) {
                Log.w(TAG, "nobody answered the pairing prompt on $host")
                mutableState.update { it.copy(connection = Connection.NeedsPairing(timedOut = true)) }
                return
            } catch (e: PairingDeclinedException) {
                Log.w(TAG, "pairing with $host declined: ${e.message}")
                mutableState.update { it.copy(connection = Connection.NeedsPairing(declined = true)) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "connection to $host failed or dropped", e)
            } finally {
                session?.close()
                session = null
                linkQuality.clear()
                mutableState.update { it.copy(power = null, foregroundAppId = null, keyboard = null, link = null) }
            }
            failures++
            val waking = now() - wokenAt < WAKE_GRACE_MS
            if (waking) failures = min(failures, 2)
            mutableState.update { it.afterFailures(if (waking) 1 else failures) { copy(connection = Connection.Offline, volume = null, muted = false) } }
            if (failures >= 2 && !waking && relocate(host)) return
            delay(if (waking) WAKING_RETRY_MS + retryJitter() else retryDelay(failures, MAX_RETRY_DELAY_MS))
        }
    }

    private suspend fun openSession(host: String, key: String?, allowPrompt: Boolean): WebOsSession {
        try {
            return register("wss://$host:3001/", key, allowPrompt)
        } catch (e: IOException) {
            if (e is PairingDeclinedException || !e.isRefused()) throw e
        }
        Log.i(TAG, "$host refuses :3001, trying :3000")
        return register("ws://$host:3000/", key, allowPrompt)
    }

    private suspend fun register(url: String, key: String?, allowPrompt: Boolean) =
        WebOsSession.open(tvHttp, url, key, DEVICE_NAME, allowPrompt) {
            mutableState.update { it.copy(connection = Connection.AwaitingApproval) }
        }

    private fun Throwable.isRefused(): Boolean = generateSequence(this) { it.cause }.any {
        it.message?.contains("ECONNREFUSED") == true || it.message?.contains("Connection refused", ignoreCase = true) == true
    }

    private suspend fun watchLink(session: WebOsSession) {
        while (true) {
            delay(LINK_PROBE_MS + Random.nextLong(-LINK_PROBE_JITTER_MS, LINK_PROBE_JITTER_MS))
            if (keeper.visibleScreens == 0) continue
            val started = now()
            val answered = try {
                session.request(LINK_PROBE_URI)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("WebOsBackend.watchLink", e)
                false
            }
            if (!answered) continue
            linkQuality.add(now() - started)
            val report = linkQuality.report() ?: continue
            mutableState.update {
                if (report.verdict == LinkVerdict.GOOD && it.link?.verdict == LinkVerdict.GOOD) it else it.copy(link = report)
            }
        }
    }

    private suspend fun learnIdentity(host: String) = quietly {
        if (settings.tvId(host) != null) return@quietly
        finder.find().firstOrNull { it.host == host }?.id?.let { settings.setTvId(host, it) }
    }

    private suspend fun learnMacs(session: WebOsSession, host: String) = quietly {
        settings.learnMacs(host, macsFrom(session.request(CONNECTION_INFO_URI)))
    }

    private suspend fun relocate(host: String): Boolean {
        val id = settings.tvId(host) ?: return false
        if (now() - lastRelocation < RELOCATE_INTERVAL_MS) return false
        lastRelocation = now()
        val moved = findTvs().firstOrNull { it.id == id && it.host != host } ?: return false
        settings.moveTv(from = host, to = moved.host)
        mutableNotices.tryEmit(Notice.TV_MOVED)
        return true
    }

    private suspend fun watchPower(session: WebOsSession) = quietly {
        session.subscribe(
            "ssap://com.webos.service.tvpower/power/getPowerState",
            JSONObject().put("subscribe", true),
        ).collect { payload ->
            val power = parsePower(payload.optString("state"), payload.optString("processing"))
            if (power != null) mutableState.update { it.copy(power = power) }
        }
    }

    private suspend fun watchForegroundApp(session: WebOsSession) = quietly {
        session.subscribe("ssap://com.webos.applicationManager/getForegroundAppInfo").collect { payload ->
            val appId = payload.optString("appId").ifEmpty { null }
            mutableState.update {
                if (it.foregroundAppId == appId) it else it.copy(foregroundAppId = appId, playing = playingFrom(playStates[appId]) ?: true)
            }
        }
    }

    private suspend fun watchPlayState(session: WebOsSession) = quietly {
        session.subscribe(MEDIA_URI, JSONObject().put("subscribe", true)).collect { payload ->
            Log.d(MEDIA_TAG, "play state: ${payload.toString().take(LOGGED_PAYLOAD)}")
            val media = payload.optJSONArray("foregroundAppInfo") ?: return@collect
            val states = (0 until media.length()).mapNotNull { index ->
                val info = media.optJSONObject(index) ?: return@mapNotNull null
                val appId = info.optString("appId").ifEmpty { return@mapNotNull null }
                appId to info.optString("playState")
            }.toMap()
            playStates = states
            mutableState.update { state ->
                val playing = playingFrom(states[state.foregroundAppId]) ?: return@update state
                if (state.playing == playing) state else state.copy(playing = playing)
            }
        }
    }

    private suspend fun watchVolume(session: WebOsSession) = quietly {
        session.subscribe("ssap://audio/getVolume").collect { payload ->
            val nested = payload.optJSONObject("volumeStatus")
            val source = nested ?: payload
            mutableState.update { state ->
                state.copy(
                    volume = if (source.has("volume")) source.optInt("volume", -1).takeIf { it >= 0 } else state.volume,
                    muted = when {
                        nested?.has("muteStatus") == true -> nested.optBoolean("muteStatus")
                        payload.has("muted") -> payload.optBoolean("muted")
                        payload.has("mute") -> payload.optBoolean("mute")
                        else -> state.muted
                    },
                )
            }
        }
    }

    private suspend fun watchKeyboard(session: WebOsSession) = quietly {
        session.subscribe("ssap://com.webos.service.ime/registerRemoteKeyboard").collect { payload ->
            val widget = payload.optJSONObject("currentWidget") ?: return@collect
            val keyboard = if (widget.optBoolean("focus")) TvKeyboard(widget.optInt("surroundingTextLength")) else null
            mutableState.update { it.copy(keyboard = keyboard) }
        }
    }

    private suspend fun watchInputs(session: WebOsSession) = quietly {
        try {
            session.subscribe(INPUTS_URI, JSONObject().put("subscribe", true)).collect(::showInputs)
        } catch (_: WebOsException) {
            showInputs(session.request(INPUTS_URI))
        }
    }

    private fun showInputs(payload: JSONObject) {
        val devices = payload.optJSONArray("devices") ?: return
        val found = (0 until devices.length()).mapNotNull { index ->
            val device = devices.optJSONObject(index) ?: return@mapNotNull null
            val appId = device.optString("appId").ifEmpty { return@mapNotNull null }
            val id = device.optString("id").ifEmpty { return@mapNotNull null }
            id to TvApp(appId, device.optString("label").ifEmpty { id }, input = true, plugged = device.optBoolean("connected"))
        }.distinctBy { (_, input) -> input.id }
        inputIds = found.associate { (id, input) -> input.id to id }
        mutableState.update { it.copy(inputs = found.map { (_, input) -> input }) }
    }

    private suspend fun watchSoundOutput(session: WebOsSession) = quietly {
        session.subscribe(SOUND_OUTPUT_URI, JSONObject().put("subscribe", true)).collect { payload ->
            payload.optString("soundOutput").ifEmpty { null }?.let { output -> mutableState.update { it.copy(soundOutput = output) } }
        }
    }

    private suspend fun watchPicture(session: WebOsSession) = quietly {
        try {
            session.subscribe(SETTINGS_URI, JSONObject(PICTURE_KEYS.toString()).put("subscribe", true)).collect(::showPicture)
        } catch (_: WebOsException) {
            showPicture(session.request(SETTINGS_URI, PICTURE_KEYS))
        }
    }

    private fun showPicture(payload: JSONObject) {
        val settings = payload.optJSONObject("settings") ?: return
        mutableState.update { state ->
            val old = state.picture
            val mode = settings.optString("pictureMode").ifEmpty { old?.mode }
            val backlight = settings.optString("backlight").toIntOrNull() ?: old?.backlight
            state.copy(picture = TvPicture(mode, backlight))
        }
    }

    private suspend fun watchChannels(session: WebOsSession) = quietly {
        val list = session.request("ssap://tv/getChannelList").optJSONArray("channelList") ?: return@quietly
        val channels = (0 until list.length()).mapNotNull { index ->
            val channel = list.optJSONObject(index) ?: return@mapNotNull null
            val id = channel.optString("channelId").ifEmpty { return@mapNotNull null }
            TvChannel(id, channel.optString("channelNumber"), channel.optString("channelName"))
        }.distinctBy { it.id }
        mutableState.update { it.copy(channels = channels) }
        if (channels.isEmpty()) return@quietly
        mutableState.map { it.foregroundAppId == LIVE_TV }.distinctUntilChanged().collectLatest { live ->
            if (!live) {
                mutableState.update { it.copy(channelId = null, programme = null) }
                return@collectLatest
            }
            coroutineScope {
                launch { watchProgramme(session) }
                quietly {
                    session.subscribe("ssap://tv/getCurrentChannel", JSONObject().put("subscribe", true)).collect { payload ->
                        mutableState.update { it.copy(channelId = payload.optString("channelId").ifEmpty { null }) }
                    }
                }
            }
        }
    }

    private suspend fun watchProgramme(session: WebOsSession) {
        while (true) {
            quietly {
                session.subscribe(PROGRAMME_URI, JSONObject().put("subscribe", true)).collect { payload ->
                    Log.d(MEDIA_TAG, "programme: ${payload.toString().take(LOGGED_PAYLOAD)}")
                    val programme = programmeFrom(payload, System.currentTimeMillis())
                    mutableState.update { if (it.programme == programme) it else it.copy(programme = programme) }
                }
            }
            delay(PROGRAMME_RETRY_MS)
        }
    }

    private suspend fun luna(session: WebOsSession, uri: String, params: JSONObject) {
        val call = JSONObject().put("uri", "luna://$uri").put("params", params)
        val alert = JSONObject()
            .put("message", " ")
            .put("buttons", JSONArray().put(JSONObject().put("label", "").put("onClick", "luna://$uri").put("params", params)))
            .put("onclose", call)
            .put("onfail", call)
        val id = session.request("ssap://system.notifications/createAlert", alert).optString("alertId")
            .ifEmpty { throw WebOsException("The TV didn't open the alert") }
        session.request("ssap://system.notifications/closeAlert", JSONObject().put("alertId", id))
    }

    private suspend fun setPicture(session: WebOsSession, settings: JSONObject) =
        luna(session, "com.webos.settingsservice/setSystemSettings", JSONObject().put("category", "picture").put("settings", settings))

    private suspend fun loadApps(session: WebOsSession) = quietly {
        val points = session.request("ssap://com.webos.applicationManager/listLaunchPoints")
            .optJSONArray("launchPoints") ?: return@quietly
        val apps = (0 until points.length()).mapNotNull { index ->
            val point = points.optJSONObject(index) ?: return@mapNotNull null
            val id = point.optString("id").ifEmpty { return@mapNotNull null }
            TvApp(
                id = id,
                title = point.optString("title").ifEmpty { TvApps.knownTitles[id] ?: id },
                iconUrl = point.optString("largeIcon").ifEmpty { point.optString("icon") }.ifEmpty { null },
                color = parseColor(point.optString("bgColor")),
                searchable = TvApps.isSearchable(id),
            )
        }
        mutableState.update { it.copy(apps = apps) }
    }

    override fun press(button: TvButton) {
        when (button) {
            TvButton.PLAY -> mutableState.update { it.copy(playing = true) }
            TvButton.PAUSE, TvButton.STOP -> mutableState.update { it.copy(playing = false) }
            else -> Unit
        }
        command { it.pointer().button(button.wire) }
    }

    override fun playPause() {
        val state = mutableState.value
        when {
            state.cast != null -> castPlayPause()
            state.playing -> press(TvButton.PAUSE)
            else -> press(TvButton.PLAY)
        }
    }

    override fun pointerMove(dx: Float, dy: Float) {
        moveX += dx
        moveY += dy
        schedulePointerFlush()
    }

    override fun pointerScroll(dx: Float, dy: Float) {
        scrollX = (scrollX + dx).coerceIn(-MAX_PENDING_SCROLL, MAX_PENDING_SCROLL)
        scrollY = (scrollY + dy).coerceIn(-MAX_PENDING_SCROLL, MAX_PENDING_SCROLL)
        schedulePointerFlush()
    }

    override fun pointerClick() = command { session ->
        val pointer = session.pointer()
        flushPointer(pointer)
        pointer.click()
    }

    private fun schedulePointerFlush() {
        val current = session
        if (current == null) {
            moveX = 0f; moveY = 0f; scrollX = 0f; scrollY = 0f
            return
        }
        if (pointerFlush?.isActive == true) return
        pointerFlush = scope.launch {
            delay(POINTER_FRAME_MS)
            quietly {
                val pointer = current.pointer()
                while (true) {
                    var waited = 0
                    while (pointer.isOpen && pointer.backlog > 0 && waited++ < MAX_BACKLOG_FRAMES) delay(POINTER_FRAME_MS)
                    flushPointer(pointer)
                    if (!pointer.isOpen || (abs(scrollX) < 1f && abs(scrollY) < 1f)) break
                    delay(POINTER_FRAME_MS)
                }
            }
        }
    }

    private fun flushPointer(pointer: PointerSocket) {
        val dx = moveX.toInt()
        val dy = moveY.toInt()
        if (dx != 0 || dy != 0) {
            moveX -= dx
            moveY -= dy
            pointer.move(dx, dy)
        }
        val sx = scrollX.toInt().coerceIn(-MAX_SCROLL_PER_FRAME, MAX_SCROLL_PER_FRAME)
        val sy = scrollY.toInt().coerceIn(-MAX_SCROLL_PER_FRAME, MAX_SCROLL_PER_FRAME)
        if (sx != 0 || sy != 0) {
            scrollX -= sx
            scrollY -= sy
            pointer.scroll(sx * WEBOS_SCROLL_UNIT, sy * WEBOS_SCROLL_UNIT)
        }
    }

    override fun launch(appId: String) = command(Notice.LAUNCH_FAILED) { session ->
        val input = inputIds[appId]
        if (input != null) session.request("ssap://tv/switchInput", JSONObject().put("inputId", input)) else session.launchApp(appId)
    }

    override fun openChannel(id: String) = command { it.request("ssap://tv/openChannel", JSONObject().put("channelId", id)) }

    override fun setSoundOutput(output: String) {
        val before = mutableState.value.soundOutput
        mutableState.update { it.copy(soundOutput = output) }
        command { session ->
            undoOnFailure({ mutableState.update { it.copy(soundOutput = before) } }) {
                session.request("ssap://com.webos.service.apiadapter/audio/changeSoundOutput", JSONObject().put("output", output))
            }
        }
    }

    override fun setPictureMode(mode: String) = changePicture(JSONObject().put("pictureMode", mode)) { TvPicture(mode, it?.backlight) }

    override fun setBacklight(level: Int) = level.coerceIn(0, 100).let { clamped ->
        changePicture(JSONObject().put("backlight", clamped)) { TvPicture(it?.mode, clamped) }
    }

    private fun changePicture(settings: JSONObject, shown: (TvPicture?) -> TvPicture) {
        val before = mutableState.value.picture
        mutableState.update { it.copy(picture = shown(it.picture)) }
        command { session ->
            undoOnFailure({ mutableState.update { it.copy(picture = before) } }) { setPicture(session, settings) }
            delay(PICTURE_SETTLE_MS)
            quietly { showPicture(session.request(SETTINGS_URI, PICTURE_KEYS)) }
        }
    }

    private suspend fun undoOnFailure(undo: () -> Unit, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            undo()
            throw e
        }
    }

    override fun volumeUp() = command { it.request("ssap://audio/volumeUp") }

    override fun volumeDown() = command { it.request("ssap://audio/volumeDown") }

    override fun setVolume(level: Int) = command { it.request("ssap://audio/setVolume", JSONObject().put("volume", level.coerceIn(0, 100))) }

    override fun setMuted(muted: Boolean) {
        if (session != null) mutableState.update { it.copy(muted = muted) }
        command { it.request("ssap://audio/setMute", JSONObject().put("mute", muted)) }
    }

    override fun type(edit: TextEdit) = command { session ->
        imeLock.withLock {
            if (edit.delete > 0) {
                session.request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", edit.delete))
            }
            if (edit.insert.isNotEmpty()) {
                session.request(
                    "ssap://com.webos.service.ime/insertText",
                    JSONObject().put("text", edit.insert).put("replace", false),
                )
            }
        }
    }

    override fun enter() = command { session ->
        imeLock.withLock { session.request("ssap://com.webos.service.ime/sendEnterKey") }
    }

    override fun screenOff() = command {
        it.request("ssap://com.webos.service.tvpower/power/turnOffScreen", JSONObject().put("standbyMode", "active"))
    }

    override fun screenOn() = command {
        it.request("ssap://com.webos.service.tvpower/power/turnOnScreen", JSONObject().put("standbyMode", "active"))
    }

    override fun powerOff() = command { session ->
        try {
            session.request("ssap://system/turnOff")
        } catch (_: IOException) {
        }
    }

    override fun wake() {
        val prefs = settings.prefs.value
        scope.launch {
            try {
                WakeOnLan.wake(prefs.mac, prefs.host)
                mutableNotices.tryEmit(Notice.WAKE_SENT)
                wokenAt = now()
                if (mutableState.value.connection == Connection.Offline) retry()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("WebOsBackend.wake", e)
                mutableNotices.tryEmit(Notice.WAKE_FAILED)
            }
        }
    }

    override fun actionsFor(target: LinkTarget): List<LinkAction> {
        val apps = mutableState.value.apps
        fun has(appId: String) = apps.isEmpty() || apps.any { it.id == appId }
        val inApp = LinkAction.APP.takeIf { WebOsDeepLinks.launchFor(target)?.let { has(it.appId) } == true }
        return when (target) {
            is LinkTarget.YouTube -> listOfNotNull(inApp, LinkAction.BROWSER)
            is LinkTarget.Netflix, is LinkTarget.Streaming -> listOfNotNull(inApp)
            is LinkTarget.Media, is LinkTarget.Web -> target.possibleActions
        }
    }

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean =
        scope.async { openNow(target, action) }.await()

    private suspend fun openNow(target: LinkTarget, action: LinkAction): Boolean {
        return try {
            when {
                action == LinkAction.PLAY_MEDIA && target is LinkTarget.Media -> cast(target)
                else -> {
                    val session = awaitSession() ?: run {
                        mutableNotices.tryEmit(Notice.NOT_CONNECTED)
                        return false
                    }
                    when {
                        action == LinkAction.BROWSER ->
                            session.launchApp(TvApps.BROWSER, JSONObject().put("target", target.url))
                        action == LinkAction.APP -> {
                            val launch = WebOsDeepLinks.launchFor(target) ?: throw IllegalArgumentException("No webOS app takes $target")
                            session.launchApp(launch.appId, JSONObject().put("contentTarget", launch.contentTarget))
                        }
                        else -> throw IllegalArgumentException("$action can't open $target")
                    }
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoRendererException) {
            mutableNotices.tryEmit(Notice.NO_RENDERER)
            false
        } catch (_: UnsupportedMediaException) {
            mutableNotices.tryEmit(Notice.CAST_UNSUPPORTED)
            false
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            logFailure("WebOsBackend.openNow", e)
            mutableNotices.tryEmit(if (action == LinkAction.PLAY_MEDIA) Notice.CAST_FAILED else Notice.OPEN_FAILED)
            false
        }
    }

    override suspend fun search(query: String, target: SearchTarget): Boolean =
        scope.async { searchNow(query, target) }.await()

    private suspend fun searchNow(query: String, target: SearchTarget): Boolean {
        val session = awaitSession() ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return false
        }
        return try {
            when (target) {
                is SearchTarget.InApp -> {
                    val contentTarget = TvApps.searchTarget(target.appId, query)
                        ?: throw IllegalArgumentException("${target.appId} takes no search")
                    session.launchApp(target.appId, JSONObject().put("contentTarget", contentTarget))
                }
                is SearchTarget.Web -> session.launchApp(TvApps.BROWSER, JSONObject().put("target", target.engine.url(query)))
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("WebOsBackend.searchNow", e)
            mutableNotices.tryEmit(Notice.OPEN_FAILED)
            false
        }
    }

    private suspend fun awaitSession(): WebOsSession? {
        session?.let { return it }
        keeper.ensureConnected()
        return withTimeoutOrNull(CONNECT_WAIT_MS) {
            state.first { it.connection == Connection.Connected || it.connection is Connection.NeedsPairing }
            session
        }
    }

    private suspend fun cast(media: LinkTarget.Media) {
        val host = mutableState.value.host
        val remembered = renderer?.takeIf { it.host == host }
        val used = try {
            val device = remembered ?: findRenderer(host)
            play(device, media)
            device
        } catch (e: IOException) {
            if (remembered == null || e is UnsupportedMediaException) throw e
            findRenderer(host).also { play(it, media) }
        }
        renderer = used
        castHasPlayed = false
        castStill = media.mime.startsWith("image/")
        mutableState.update { it.copy(cast = CastState(media.title, CastPhase.LOADING, url = media.url)) }
        watchCast()
    }

    private suspend fun play(device: UpnpDevice, media: LinkTarget.Media) {
        if (media.mime.startsWith("image/")) {
            val sinks = dlna.sinkProtocols(device)
            if (sinks.isNotEmpty() && !acceptsType(sinks, media.mime)) throw UnsupportedMediaException()
        }
        dlna.play(device, media.url, media.title, media.mime)
    }

    private suspend fun findRenderer(host: String) = dlna.findRenderer(host) ?: throw NoRendererException()

    private fun watchCast() {
        castWatch?.cancel()
        val device = renderer ?: return
        castWatch = scope.launch {
            val started = now()
            var lastPosition = -1
            var lastMove = started
            var misses = 0
            while (mutableState.value.cast != null) {
                val status = try {
                    dlna.status(device)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logFailure("WebOsBackend.watchCast", e)
                    null
                }
                misses = if (status == null) misses + 1 else 0
                if (misses >= CAST_LOST_POLLS) {
                    mutableState.update { it.copy(cast = null) }
                    return@launch
                }
                if (status != null) {
                    val phase = when (status.state) {
                        "PLAYING" -> CastPhase.PLAYING
                        "PAUSED_PLAYBACK" -> CastPhase.PAUSED
                        "STOPPED", "NO_MEDIA_PRESENT" -> null
                        else -> CastPhase.LOADING
                    }
                    if (phase == CastPhase.PLAYING) castHasPlayed = true
                    if (phase == null && (castHasPlayed || now() - started > CAST_START_TIMEOUT_MS)) {
                        mutableState.update { it.copy(cast = null) }
                        if (!castHasPlayed && !castStill) mutableNotices.tryEmit(Notice.CAST_FAILED)
                        return@launch
                    }
                    if (status.positionSeconds != lastPosition || phase != CastPhase.PLAYING) {
                        lastPosition = status.positionSeconds
                        lastMove = now()
                    }
                    val stalled = !castStill && phase == CastPhase.PLAYING && now() - lastMove > STALL_MS
                    mutableState.update { state ->
                        val cast = state.cast ?: return@update state
                        state.copy(
                            cast = cast.copy(
                                phase = phase ?: cast.phase,
                                positionSeconds = status.positionSeconds,
                                durationSeconds = status.durationSeconds,
                                stalled = stalled,
                            ),
                        )
                    }
                }
                delay(1_000)
            }
        }
    }

    override fun castPlayPause() {
        val cast = mutableState.value.cast ?: return
        val playing = cast.phase == CastPhase.PLAYING
        mutableState.update { state ->
            state.copy(cast = state.cast?.copy(phase = if (playing) CastPhase.PAUSED else CastPhase.PLAYING))
        }
        castCommand { if (playing) dlna.pause(it) else dlna.resume(it) }
    }

    override fun castStop() {
        castWatch?.cancel()
        mutableState.update { it.copy(cast = null) }
        castCommand { dlna.stop(it) }
    }

    override fun castSeek(seconds: Int) {
        mutableState.update { state -> state.copy(cast = state.cast?.copy(positionSeconds = seconds)) }
        castCommand { dlna.seek(it, seconds) }
    }

    private fun castCommand(block: suspend (UpnpDevice) -> Unit) {
        val device = renderer ?: return
        scope.launch {
            try {
                block(device)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("WebOsBackend.castCommand", e)
                mutableNotices.tryEmit(Notice.COMMAND_FAILED)
            }
        }
    }

    suspend fun findTvs(): List<FoundTv> = try {
        finder.find()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure("WebOsBackend.findTvs", e)
        emptyList()
    }

    private fun command(failure: Notice = Notice.COMMAND_FAILED, block: suspend (WebOsSession) -> Unit) {
        val current = session ?: run {
            mutableNotices.tryEmit(Notice.NOT_CONNECTED)
            return
        }
        scope.launch {
            try {
                block(current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "command failed", e)
                mutableNotices.tryEmit(failure)
            }
        }
    }

    private suspend fun WebOsSession.launchApp(id: String, params: JSONObject? = null) {
        val payload = JSONObject().put("id", id)
        if (params != null) payload.put("params", params)
        request("ssap://system.launcher/launch", payload)
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "optional request failed: ${e.message}")
        }
    }

    private fun now() = System.nanoTime() / 1_000_000

    private class NoRendererException : IOException("Tenna's media renderer did not answer")

    private class UnsupportedMediaException : IOException("The renderer doesn't list this type")

    private companion object {
        const val TAG = "Tenna"
        const val DEVICE_NAME = "Tenna RC"
        const val MAX_RETRY_DELAY_MS = 5_000L
        const val LINK_PROBE_URI = "ssap://audio/getVolume"
        const val LINK_PROBE_MS = 1_000L
        const val LINK_PROBE_JITTER_MS = 350L
        const val CONNECT_WAIT_MS = 10_000L
        const val POINTER_FRAME_MS = 16L
        const val MAX_BACKLOG_FRAMES = 60
        const val WEBOS_SCROLL_UNIT = 11
        const val MAX_SCROLL_PER_FRAME = 3
        const val MAX_PENDING_SCROLL = 12f
        const val CAST_START_TIMEOUT_MS = 25_000L
        const val CAST_LOST_POLLS = 10
        const val STALL_MS = 6_000L
        const val RELOCATE_INTERVAL_MS = 60_000L
        const val INPUTS_URI = "ssap://tv/getExternalInputList"
        const val SOUND_OUTPUT_URI = "ssap://com.webos.service.apiadapter/audio/getSoundOutput"
        const val SETTINGS_URI = "ssap://settings/getSystemSettings"
        const val CONNECTION_INFO_URI = "ssap://com.webos.service.connectionmanager/getinfo"
        val PICTURE_KEYS: JSONObject get() = JSONObject().put("category", "picture").put("keys", JSONArray().put("pictureMode").put("backlight"))
        const val PICTURE_SETTLE_MS = 800L
        const val LIVE_TV = "com.webos.app.livetv"
        const val MEDIA_URI = "ssap://com.webos.media/getForegroundAppInfo"
        const val PROGRAMME_URI = "ssap://tv/getChannelProgramInfo"
        const val PROGRAMME_RETRY_MS = 15_000L
        const val LOGGED_PAYLOAD = 4_000
        const val MEDIA_TAG = "TennaMedia"
        const val WAKE_GRACE_MS = 30_000L
        const val WAKING_RETRY_MS = 1_000L

        fun parseColor(text: String): Long? {
            val hex = text.removePrefix("#")
            if (hex.length != 6) return null
            return hex.toLongOrNull(16)?.let { 0xFF000000 or it }
        }
    }
}
