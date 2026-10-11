package lt.tsx.tennarc

import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.TvState
import java.text.DateFormat
import java.util.Date

internal data class NowPlaying(
    val title: String,
    val subtitle: String,
    val playing: Boolean,
    val appId: String?,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
) {
    val canSeek: Boolean get() = durationMs != null
}

internal fun nowPlaying(state: TvState, idle: String, onTv: String): NowPlaying {
    val cast = state.cast
    if (cast != null) {
        return NowPlaying(
            title = cast.title,
            subtitle = onTv,
            playing = cast.phase != CastPhase.PAUSED,
            appId = null,
            positionMs = cast.positionSeconds * 1_000L,
            durationMs = cast.durationSeconds.takeIf { it > 0 }?.let { it * 1_000L },
        )
    }
    val programme = state.programme
    if (programme != null) {
        val channel = state.channels.firstOrNull { it.id == state.channelId }
        val name = channel?.let { listOf(it.number, it.name).filter(String::isNotBlank).joinToString(" ") }
        val clock = DateFormat.getTimeInstance(DateFormat.SHORT)
        val times = if (programme.startMs != null && programme.endMs != null) {
            "${clock.format(Date(programme.startMs))}–${clock.format(Date(programme.endMs))}"
        } else {
            null
        }
        return NowPlaying(
            title = programme.title,
            subtitle = listOfNotNull(name?.ifBlank { null }, times).joinToString(" · ").ifEmpty { onTv },
            playing = state.playing,
            appId = state.foregroundAppId,
        )
    }
    val app = state.appTitle(state.foregroundAppId)
    return NowPlaying(
        title = app ?: idle,
        subtitle = if (app != null) onTv else "",
        playing = state.playing,
        appId = state.foregroundAppId,
    )
}
