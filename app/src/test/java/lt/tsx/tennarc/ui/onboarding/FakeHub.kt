package lt.tsx.tennarc.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.NoRemoteActions
import lt.tsx.tennarc.tv.Notice
import lt.tsx.tennarc.tv.RemoteActions
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState

internal class FakeHub(
    private val answers: List<Pair<Long, FoundTv>> = emptyList(),
    private val endless: Boolean = false,
    private val passMs: Long = 2_500,
    private val answer: (TvKind) -> Connection = { kind ->
        when (kind.pairingStyle) {
            PairingStyle.APPROVE -> Connection.AwaitingApproval
            PairingStyle.CODE -> Connection.AwaitingCode()
            PairingStyle.NONE -> Connection.Connected
        }
    },
    private val notice: Notice? = null,
    start: Prefs = Prefs(tvChosen = false),
) : RemoteActions by NoRemoteActions {
    var prefs by mutableStateOf(start)
    var state by mutableStateOf(TvState(host = start.host, kind = start.kind, connection = Connection.NeedsPairing()))
    val codes = mutableListOf<String>()

    var pairs = 0
        private set
    private val said = MutableSharedFlow<Notice>(extraBufferCapacity = 8)

    override val notices: Flow<Notice> = said

    override fun updatePrefs(transform: (Prefs) -> Prefs) {
        prefs = transform(prefs)
        if (state.host != prefs.host || state.kind != prefs.kind) {
            state = TvState(host = prefs.host, kind = prefs.kind, connection = Connection.NeedsPairing())
        }
    }

    override fun pair() {
        pairs++
        state = state.copy(connection = answer(state.kind))
        notice?.let { said.tryEmit(it) }
    }

    override fun cancelPairing() {
        state = state.copy(connection = Connection.NeedsPairing())
    }

    override fun submitPairingCode(code: String) {
        codes += code
        state = state.copy(connection = if (code == GOOD_CODE) Connection.Connected else Connection.AwaitingCode(wrong = true))
    }

    fun accept() {
        state = state.copy(connection = Connection.Connected)
    }

    override suspend fun findTvs(): List<FoundTv> = emptyList()

    override fun scanTvs(): Flow<List<FoundTv>> = flow {
        var at = 0L
        val found = mutableListOf<FoundTv>()
        for ((ms, tv) in answers.sortedBy { it.first }) {
            delay(ms - at)
            at = ms
            found += tv
            emit(found.toList())
        }
        if (endless) awaitCancellation()
        delay((passMs - at).coerceAtLeast(0))
    }

    companion object {
        const val GOOD_CODE = "4F2A9C"
    }
}
