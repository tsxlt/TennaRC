package lt.tsx.tennarc.tv

import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.data.withAppUse
import lt.tsx.tennarc.data.withChannelUse
import lt.tsx.tennarc.icons.DiskIconCache
import lt.tsx.tennarc.icons.IconLoader
import lt.tsx.tennarc.icons.StoreIcons
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.PhoneFileException
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.StreamingService
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.link.TextEdit
import lt.tsx.tennarc.link.TitleFinder
import lt.tsx.tennarc.link.isPhoneFile
import lt.tsx.tennarc.link.service

class TvHub(
    private val settings: Settings,
    private val discovery: TvDiscovery,
    private val titles: TitleFinder? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    iconDir: File? = null,
    appsDir: File? = null,
    private val storeIcons: IconLoader? = null,
    private val phoneFiles: (suspend (LinkTarget.Media) -> LinkTarget.Media)? = null,
    private val create: (TvKind, Settings) -> TvBackend,
) : RemoteActions, AmbilightCommands {
    private var backend: TvBackend = backendFor(settings.prefs.value)
    private var mirror: Job? = null
    private var visibleScreens = 0
    private var holders = 0

    private val known = appsDir?.let { KnownApps(it, scope) }
    private val mutableState = MutableStateFlow(remembered(backend.state.value))
    val state: StateFlow<TvState> = mutableState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val notices: SharedFlow<Notice> = mutableNotices.asSharedFlow()

    private val current = object : IconLoader {
        override suspend fun load(url: String) = if (StoreIcons.isStoreUrl(url)) storeIcons?.load(url) else backend.iconLoader.load(url)

        override fun cached(url: String) = if (StoreIcons.isStoreUrl(url)) storeIcons?.cached(url) else backend.iconLoader.cached(url)
    }
    val iconLoader: IconLoader = iconDir?.let { dir -> DiskIconCache(dir) { current } } ?: current

    init {
        follow(backend)
        scope.launch {
            mutableState.map { it.foregroundAppId }.distinctUntilChanged().collectLatest { appId ->
                val service = appId?.let(StreamingService::byAppId) ?: return@collectLatest
                if (settings.prefs.value.demoTv || service in settings.prefs.value.watchedServices) return@collectLatest
                delay(WATCHED_AFTER_MS)
                settings.update { it.copy(watchedServices = it.watchedServices + service) }
            }
        }
        scope.launch {
            mutableState.map { it.foregroundAppId }.distinctUntilChanged().collectLatest { appId ->
                if (appId == null || settings.prefs.value.demoTv) return@collectLatest
                while (true) {
                    delay(APP_USE_MINUTE_MS)
                    settings.update { it.withAppUse(appId, System.currentTimeMillis()) }
                }
            }
        }
        scope.launch {
            mutableState.map { it.channelId }.distinctUntilChanged().collectLatest { channelId ->
                if (channelId == null || settings.prefs.value.demoTv) return@collectLatest
                while (true) {
                    delay(APP_USE_MINUTE_MS)
                    settings.update { it.withChannelUse(channelId, System.currentTimeMillis()) }
                }
            }
        }
        scope.launch {
            settings.prefs.map { Triple(it.kind, it.host, it.demoTv) }.distinctUntilChanged().drop(1).collect {
                val old = backend
                backend = backendFor(settings.prefs.value)
                old.close()
                follow(backend)
                repeat(visibleScreens) { backend.onScreenStarted() }
                repeat(holders) { backend.hold() }
            }
        }
    }

    private fun backendFor(prefs: Prefs): TvBackend = if (prefs.demoTv) DemoTv() else create(prefs.kind, settings)

    private fun follow(to: TvBackend) {
        mirror?.cancel()
        mirror = scope.launch {
            launch { to.state.collect { mutableState.value = remembered(it) } }
            launch { to.notices.collect { mutableNotices.tryEmit(it) } }
        }
    }

    private fun remembered(state: TvState): TvState {
        val known = known ?: return state
        if (state.apps.isNotEmpty()) {
            if (state.connection == Connection.Connected) known.put(state.host, KnownApps.Known(state.apps, state.inputs, state.channels))
            return state
        }
        val last = known.get(state.host) ?: return state
        return state.copy(
            apps = last.apps,
            inputs = state.inputs.ifEmpty { last.inputs },
            channels = state.channels.ifEmpty { last.channels },
            appsFromMemory = true,
        )
    }

    fun onScreenStarted() {
        visibleScreens++
        backend.onScreenStarted()
        if (mutableState.value.connection == Connection.Offline) backend.retry()
    }

    fun onScreenStopped() {
        if (visibleScreens == 0) return
        visibleScreens--
        backend.onScreenStopped()
    }

    fun hold() {
        holders++
        backend.hold()
    }

    fun release() {
        if (holders == 0) return
        holders--
        backend.release()
    }

    override fun playPause() = backend.playPause()

    fun setVolume(level: Int) = backend.setVolume(level)

    override fun updatePrefs(transform: (Prefs) -> Prefs) = settings.update(transform)

    override suspend fun findTvs(): List<FoundTv> = discovery.find()

    override fun scanTvs(): Flow<List<FoundTv>> = discovery.stream()

    override suspend fun findTitles(query: String): List<StreamingTitle> = keepToWatched(titles?.find(query).orEmpty())

    private fun keepToWatched(found: List<StreamingTitle>) = keepToWatched(found, settings.prefs.value.watchedServices)

    override suspend fun isReachable(host: String, kind: TvKind): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { it.connect(InetSocketAddress(host, kind.controlPort), REACH_TIMEOUT_MS) }
            true
        } catch (_: IOException) {
            false
        }
    }

    private var picks: Triple<Long, Set<StreamingService>, List<StreamingTitle>>? = null

    override suspend fun findPicks(): List<StreamingTitle> {
        val finder = titles ?: return emptyList()
        val watched = settings.prefs.value.watchedServices
        val services = StreamingService.entries.filterTo(LinkedHashSet()) {
            LinkAction.APP in backend.actionsFor(it.sampleLink) && (watched.isEmpty() || it in watched)
        }
        val now = System.currentTimeMillis()
        picks?.let { (at, forServices, found) -> if (forServices == services && now - at < PICKS_FOR_MS) return found }
        return keepToWatched(finder.picks(services)).also { found -> if (found.isNotEmpty()) picks = Triple(now, services, found) }
    }

    override fun pair() = backend.pair()
    override fun cancelPairing() = backend.cancelPairing()
    override fun submitPairingCode(code: String) = backend.submitPairingCode(code)
    override fun retry() = backend.retry()
    override fun wake() = backend.wake()
    override fun forgetPairing() = backend.forgetPairing()
    override fun press(button: TvButton) = backend.press(button)
    override fun pointerMove(dx: Float, dy: Float) = backend.pointerMove(dx, dy)
    override fun pointerScroll(dx: Float, dy: Float) = backend.pointerScroll(dx, dy)
    override fun pointerClick() = backend.pointerClick()
    override fun launch(appId: String) = backend.launch(appId)
    override fun actionsFor(target: LinkTarget): List<LinkAction> = when {
        !target.isPhoneFile -> backend.actionsFor(target)
        Feature.MEDIA_CAST !in mutableState.value.features -> emptyList()
        else -> backend.actionsFor(target).filter { it == LinkAction.PLAY_MEDIA }
    }

    override suspend fun open(target: LinkTarget, action: LinkAction): Boolean {
        if (target !is LinkTarget.Media || !target.isPhoneFile || settings.prefs.value.demoTv) return backend.open(target, action)
        val served = try {
            phoneFiles?.invoke(target) ?: return false
        } catch (e: PhoneFileException) {
            mutableNotices.tryEmit(e.notice)
            return false
        }
        return backend.open(served, action)
    }
    override suspend fun search(query: String, target: SearchTarget) = backend.search(query, target)
    override fun volumeUp() = backend.volumeUp()
    override fun volumeDown() = backend.volumeDown()
    override fun setMuted(muted: Boolean) = backend.setMuted(muted)
    override fun type(edit: TextEdit) = backend.type(edit)
    override fun enter() = backend.enter()
    override fun castPlayPause() = backend.castPlayPause()
    override fun castStop() = backend.castStop()
    override fun castSeek(seconds: Int) = backend.castSeek(seconds)
    override fun screenOff() = backend.screenOff()
    override fun screenOn() = backend.screenOn()
    override fun powerOff() = backend.powerOff()
    override fun openChannel(id: String) = backend.openChannel(id)
    override fun setSoundOutput(output: String) = backend.setSoundOutput(output)
    override fun setPictureMode(mode: String) = backend.setPictureMode(mode)
    override fun setBacklight(level: Int) = backend.setBacklight(level)
    override fun setUpDevLink() = backend.setUpDevLink()
    override fun turnOffDevLink() = backend.turnOffDevLink()
    override fun setAmbilight(style: AmbilightStyle) = backend.setAmbilight(style)
    override fun pairAmbilight() = backend.pairAmbilight()
    override fun submitAmbilightPin(pin: String) = backend.submitAmbilightPin(pin)
}

private const val PICKS_FOR_MS = 60 * 60 * 1000L
private const val WATCHED_AFTER_MS = 60_000L
private const val APP_USE_MINUTE_MS = 60_000L
private const val REACH_TIMEOUT_MS = 700

internal fun keepToWatched(found: List<StreamingTitle>, watched: Set<StreamingService>): List<StreamingTitle> {
    if (watched.isEmpty()) return found
    return found.mapNotNull { title ->
        if (title.video) return@mapNotNull title
        val ours = title.offers.filter { it.service in watched }
        if (ours.isEmpty()) null else title.copy(target = ours.first(), alternatives = ours.drop(1))
    }
}
