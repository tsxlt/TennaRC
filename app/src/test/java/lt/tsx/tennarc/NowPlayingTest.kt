package lt.tsx.tennarc

import lt.tsx.tennarc.tv.CastPhase
import lt.tsx.tennarc.tv.CastState
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.TvApps
import lt.tsx.tennarc.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingTest {
    private val onTv = "On TV"
    private val tv = TvState(host = "h", connection = Connection.Connected)

    @Test
    fun `the app in front is the title`() {
        val now = nowPlaying(tv.copy(foregroundAppId = TvApps.NETFLIX), "TV remote", onTv)
        assertEquals("Netflix", now.title)
        assertEquals("On TV", now.subtitle)
        assertEquals(TvApps.NETFLIX, now.appId)
        assertFalse(now.canSeek)
    }

    @Test
    fun `nothing in front says what the card is`() {
        val now = nowPlaying(tv, "TV remote", onTv)
        assertEquals("TV remote", now.title)
        assertEquals("", now.subtitle)
    }

    @Test
    fun `a paused guess shows as paused`() {
        assertFalse(nowPlaying(tv.copy(foregroundAppId = TvApps.YOUTUBE, playing = false), "TV remote", onTv).playing)
    }

    @Test
    fun `a cast brings its title and a progress bar`() {
        val cast = CastState("Big Buck Bunny", CastPhase.PAUSED, positionSeconds = 61, durationSeconds = 596)
        val now = nowPlaying(tv.copy(foregroundAppId = TvApps.PHOTO_VIDEO, cast = cast), "TV remote", onTv)
        assertEquals("Big Buck Bunny", now.title)
        assertFalse(now.playing)
        assertEquals(61_000L, now.positionMs)
        assertEquals(596_000L, now.durationMs)
        assertNull(now.appId)
    }

    @Test
    fun `a live stream has no bar to seek on`() {
        val cast = CastState("Radio", CastPhase.PLAYING, positionSeconds = 30, durationSeconds = 0)
        assertFalse(nowPlaying(tv.copy(cast = cast), "TV remote", onTv).canSeek)
    }
}
