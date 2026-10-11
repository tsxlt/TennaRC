package lt.tsx.tennarc.tv.androidtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteProtocolTest {
    @Test
    fun `we ask for androidtvremote2's features`() {
        assertEquals(615, RemoteProtocol.REQUESTED)
    }

    @Test
    fun `the configure answer carries androidtvremote2's device info`() {
        assertArrayEquals(
            bytes(10, 28, 8, 231, 4, 18, 23, 24, 1, 34, 1, "1", 42, 9, "atvremote", 50, 5, "1.0.0"),
            RemoteProtocol.configure(615),
        )
    }

    @Test
    fun `set-active and ping answers`() {
        assertArrayEquals(bytes(18, 3, 8, 238, 4), RemoteProtocol.setActive(622))
        assertArrayEquals(bytes(74, 2, 8, 25), RemoteProtocol.pingResponse(25))
    }

    @Test
    fun `a key is one short press`() {
        assertArrayEquals(bytes(82, 4, 8, 24, 16, 3), RemoteProtocol.keyInject(KeyCode.VOLUME_UP))
        assertArrayEquals(bytes(82, 5, 8, 178, 1, 16, 3), RemoteProtocol.keyInject(KeyCode.TV_INPUT))
    }

    @Test
    fun `an app link is the URL in field 90`() {
        assertArrayEquals(
            bytes(210, 5, 33, 10, 31, "https://www.netflix.com/title.*"),
            RemoteProtocol.appLink("https://www.netflix.com/title.*"),
        )
    }

    @Test
    fun `text is one insert with the cursor on its last character`() {
        assertArrayEquals(bytes(170, 1, 9, 26, 7, 8, 1, 18, 3, 26, 1, "A"), RemoteProtocol.imeText(0, 0, "A"))
        assertArrayEquals(
            bytes(170, 1, 20, 8, 9, 16, 4, 26, 14, 8, 1, 18, 10, 8, 3, 16, 3, 26, 4, "abcd"),
            RemoteProtocol.imeText(9, 4, "abcd"),
        )
    }

    @Test
    fun `the TV's handshake messages`() {
        assertEquals(
            RemoteEvent.Configure(639, model = "Model", vendor = "Vendor"),
            RemoteProtocol.decode(bytes(10, 20, 8, 255, 4, 18, 15, 10, 5, "Model", 18, 6, "Vendor")),
        )
        assertEquals(RemoteEvent.SetActive, RemoteProtocol.decode(bytes(18, 0)))
        assertEquals(RemoteEvent.Ping(25), RemoteProtocol.decode(bytes(66, 4, 8, 25, 16, 7)))
    }

    @Test
    fun `power, app and volume reports`() {
        assertEquals(RemoteEvent.Started(true), RemoteProtocol.decode(bytes(194, 2, 2, 8, 1)))
        assertEquals(RemoteEvent.Started(false), RemoteProtocol.decode(bytes(194, 2, 0)))
        assertEquals(
            RemoteEvent.AppInfo("org.droidtv.playtv"),
            RemoteProtocol.decode(bytes(162, 1, 22, 10, 20, 98, 18, "org.droidtv.playtv")),
        )
        assertEquals(
            RemoteEvent.Volume(level = 0, max = 0, muted = false),
            RemoteProtocol.decode(bytes(146, 3, 15, 8, 9, 16, 10, 26, 7, "TPM171E", 32, 1)),
        )
        assertEquals(RemoteEvent.Volume(level = 12, max = 100, muted = true), RemoteProtocol.decode(bytes(146, 3, 6, 48, 100, 56, 12, 64, 1)))
    }

    @Test
    fun `input method messages`() {
        assertEquals(RemoteEvent.ImeCounters(ime = 9, field = 4), RemoteProtocol.decode(bytes(170, 1, 4, 8, 9, 16, 4)))
        assertEquals(RemoteEvent.ImeShow("hi"), RemoteProtocol.decode(bytes(178, 1, 6, 18, 4, 18, 2, "hi")))
    }

    @Test
    fun `errors and anything else`() {
        assertEquals(RemoteEvent.Error, RemoteProtocol.decode(bytes(26, 2, 8, 1)))
        assertEquals(RemoteEvent.Other, RemoteProtocol.decode(bytes(242, 1, 0)))
    }
}
