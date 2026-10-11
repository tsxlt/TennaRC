package lt.tsx.tennarc.tv

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.link.LinkAction
import lt.tsx.tennarc.link.LinkTarget
import lt.tsx.tennarc.link.SearchTarget
import lt.tsx.tennarc.link.possibleActions
import lt.tsx.tennarc.link.StreamingTitle
import lt.tsx.tennarc.link.TextEdit

interface TvCommands : AmbilightCommands {
    fun pair()
    fun cancelPairing()

    fun submitPairingCode(code: String) = Unit
    fun retry()
    fun wake()
    fun forgetPairing()

    fun press(button: TvButton)
    fun pointerMove(dx: Float, dy: Float)

    fun pointerScroll(dx: Float, dy: Float)
    fun pointerClick()

    fun launch(appId: String)

    fun actionsFor(target: LinkTarget): List<LinkAction> = target.possibleActions

    suspend fun open(target: LinkTarget, action: LinkAction): Boolean

    suspend fun search(query: String, target: SearchTarget): Boolean = false

    fun playPause()

    fun volumeUp()
    fun volumeDown()
    fun setMuted(muted: Boolean)

    fun type(edit: TextEdit)
    fun enter()

    fun castPlayPause()
    fun castStop()
    fun castSeek(seconds: Int)

    fun screenOff()
    fun screenOn()
    fun powerOff()

    fun openChannel(id: String) = Unit

    fun setSoundOutput(output: String) = Unit

    fun setPictureMode(mode: String) = Unit
    fun setBacklight(level: Int) = Unit

    fun setUpDevLink() = Unit
    fun turnOffDevLink() = Unit
}

interface RemoteActions : TvCommands {
    fun updatePrefs(transform: (Prefs) -> Prefs)

    suspend fun findTvs(): List<FoundTv>

    fun scanTvs(): Flow<List<FoundTv>> = flow { emit(findTvs()) }

    val notices: Flow<Notice> get() = emptyFlow()

    suspend fun findTitles(query: String): List<StreamingTitle> = emptyList()

    suspend fun isReachable(host: String, kind: TvKind): Boolean = false

    suspend fun findPicks(): List<StreamingTitle> = emptyList()
}

object NoRemoteActions : RemoteActions {
    override fun pair() = Unit
    override fun cancelPairing() = Unit
    override fun retry() = Unit
    override fun wake() = Unit
    override fun forgetPairing() = Unit
    override fun press(button: TvButton) = Unit
    override fun pointerMove(dx: Float, dy: Float) = Unit
    override fun pointerScroll(dx: Float, dy: Float) = Unit
    override fun pointerClick() = Unit
    override fun launch(appId: String) = Unit
    override suspend fun open(target: LinkTarget, action: LinkAction) = true
    override fun playPause() = Unit
    override fun volumeUp() = Unit
    override fun volumeDown() = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun type(edit: TextEdit) = Unit
    override fun enter() = Unit
    override fun castPlayPause() = Unit
    override fun castStop() = Unit
    override fun castSeek(seconds: Int) = Unit
    override fun screenOff() = Unit
    override fun screenOn() = Unit
    override fun powerOff() = Unit
    override fun updatePrefs(transform: (Prefs) -> Prefs) = Unit
    override suspend fun findTvs() = emptyList<FoundTv>()
}
