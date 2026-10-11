package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import lt.tsx.tennarc.data.Settings
import lt.tsx.tennarc.tv.DevLink
import lt.tsx.tennarc.tv.DevProblem
import lt.tsx.tennarc.tv.firetv.AdbPortClosedException
import lt.tsx.tennarc.tv.firetv.AdbShell
import lt.tsx.tennarc.tv.firetv.AdbTlsRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdbLinkTest {
    private lateinit var settings: Settings

    @Before
    fun setUp() {
        settings = Settings(RuntimeEnvironment.getApplication())
        settings.setClientKey(adbKey(HOST), null)
    }

    private class FakeAdbd {
        var trusts = true
        var refuses = false
        var tls = false
        var unreachable = false
        var hangs = false
        var listening = AdbLink.ADB_PORT
        val opened = mutableListOf<Int>()
        val commands = mutableListOf<String>()
        var connects = 0
        val approval = CompletableDeferred<Unit>()

        fun shell(port: Int) = object : AdbShell {
            private var closed = false

            override suspend fun trusted(): Boolean {
                opened += port
                if (unreachable) throw IOException("No route to host")
                if (tls) throw AdbTlsRequiredException()
                if (refuses || port != listening) throw AdbPortClosedException(HOST)
                return trusts
            }

            override suspend fun connect() {
                connects++
                if (!trusts) {
                    approval.await()
                    trusts = true
                }
            }

            override suspend fun shell(command: String): String {
                if (closed || unreachable) throw IOException("Connection reset")
                commands += command
                if (hangs) awaitCancellation()
                return "ok"
            }

            override fun close() {
                closed = true
            }
        }
    }

    private fun TestScope.link(tv: FakeAdbd, ports: AdbPorts = AdbPorts.None, setUp: Boolean = true): AdbLink {
        if (setUp) settings.setClientKey(adbKey(HOST), AdbLink.ADB_PORT.toString())
        return AdbLink(settings, HOST, backgroundScope, { _, port -> tv.shell(port) }, ports, Random(1))
    }

    private fun TestScope.up(link: AdbLink) {
        link.remoteUp(HOST)
        advanceTimeBy(AdbLink.START_DELAY_MS + 1)
        runCurrent()
    }

    @Test
    fun `not set up, it stays off and never knocks`() = runTest {
        val tv = FakeAdbd()
        val link = link(tv, setUp = false)
        assertEquals(DevLink.Off, link.status.value)
        up(link)
        advanceTimeBy(600_000)
        assertEquals(DevLink.Off, link.status.value)
        assertEquals(emptyList<Int>(), tv.opened)
        assertNull(link.call("echo"))
    }

    @Test
    fun `set up, it connects a moment after the remote and runs commands`() = runTest {
        val tv = FakeAdbd()
        val link = link(tv)
        assertEquals(DevLink.Down(), link.status.value)
        link.remoteUp(HOST)
        assertEquals(DevLink.Connecting, link.status.value)
        runCurrent()
        assertEquals(emptyList<Int>(), tv.opened)
        advanceTimeBy(AdbLink.START_DELAY_MS + 1)
        assertEquals(DevLink.Up, link.status.value)
        assertEquals(1, link.connections.value)
        assertEquals("ok", link.call("dumpsys tv_input"))
        assertEquals(listOf("dumpsys tv_input"), tv.commands)
    }

    @Test
    fun `setting up waits for the TV's prompt, then remembers the TV`() = runTest {
        val tv = FakeAdbd().apply { trusts = false }
        val link = link(tv, setUp = false)
        link.setUp(HOST)
        runCurrent()
        assertEquals(DevLink.AwaitingApproval, link.status.value)
        tv.approval.complete(Unit)
        runCurrent()
        assertEquals(DevLink.Up, link.status.value)
        assertEquals("5555", settings.clientKey(adbKey(HOST)))
    }

    @Test
    fun `nobody answering the prompt ends setting up, and nothing is remembered`() = runTest {
        val tv = FakeAdbd().apply { trusts = false }
        val link = link(tv, setUp = false)
        link.setUp(HOST)
        advanceTimeBy(AdbLink.APPROVAL_TIMEOUT_MS + 1)
        assertEquals(DevLink.Failed(DevProblem.NOT_ALLOWED), link.status.value)
        assertNull(settings.clientKey(adbKey(HOST)))
        up(link)
        advanceTimeBy(600_000)
        assertEquals(1, tv.connects)
    }

    @Test
    fun `setting up tells debugging off from Wireless debugging only`() = runTest {
        val off = FakeAdbd().apply { refuses = true }
        link(off, setUp = false).also { link ->
            link.setUp(HOST)
            runCurrent()
            assertEquals(DevLink.Failed(DevProblem.DEBUGGING_OFF), link.status.value)
        }
        val tls = FakeAdbd().apply { tls = true }
        link(tls, setUp = false).also { link ->
            link.setUp(HOST)
            runCurrent()
            assertEquals(DevLink.Failed(DevProblem.WIRELESS_ONLY), link.status.value)
        }
        val wireless = FakeAdbd().apply { refuses = true }
        link(wireless, AdbPorts { AdbPorts.Announced(tls = 37_215) }, setUp = false).also { link ->
            link.setUp(HOST)
            runCurrent()
            assertEquals(DevLink.Failed(DevProblem.WIRELESS_ONLY), link.status.value)
        }
    }

    @Test
    fun `a TV that forgot this phone isn't asked again unasked`() = runTest {
        val tv = FakeAdbd().apply { trusts = false }
        val link = link(tv)
        up(link)
        assertEquals(DevLink.Down(DevProblem.NOT_ALLOWED), link.status.value)
        assertEquals(0, tv.connects)
        advanceTimeBy(600_000)
        assertEquals(0, tv.connects)
    }

    @Test
    fun `a dropped connection comes back, quietly the first time`() = runTest {
        val tv = FakeAdbd()
        val link = link(tv)
        up(link)
        assertEquals(DevLink.Up, link.status.value)
        tv.unreachable = true
        assertNull(link.call("echo"))
        runCurrent()
        assertEquals(DevLink.Up, link.status.value)
        advanceTimeBy(AdbLink.FIRST_RETRY_MS + AdbLink.RETRY_JITTER_MS)
        assertEquals(DevLink.Down(), link.status.value)
        tv.unreachable = false
        advanceTimeBy(AdbLink.FIRST_RETRY_MS * 2 + AdbLink.RETRY_JITTER_MS)
        assertEquals(DevLink.Up, link.status.value)
        assertEquals(2, link.connections.value)
    }

    @Test
    fun `retries slow down, and something that needs the link brings it at once`() = runTest {
        val tv = FakeAdbd().apply { unreachable = true }
        val link = link(tv)
        up(link)
        advanceTimeBy(10 * 60_000)
        val tries = tv.opened.size
        assertTrue("$tries tries", tries in 8..10)
        advanceTimeBy(10 * 60_000)
        assertTrue(tv.opened.size - tries in 4..6)
        tv.unreachable = false
        val called = async { link.call("echo") }
        runCurrent()
        assertEquals("ok", called.await())
        assertEquals(DevLink.Up, link.status.value)
    }

    @Test
    fun `it doesn't knock while the remote can't reach the TV either`() = runTest {
        val tv = FakeAdbd().apply { unreachable = true }
        val link = link(tv)
        up(link)
        link.remoteDown()
        val tries = tv.opened.size
        advanceTimeBy(AdbLink.LINGER_MS - 1)
        advanceTimeBy(10 * 60_000)
        assertEquals(tries, tv.opened.size)
        tv.unreachable = false
        up(link)
        assertEquals(DevLink.Up, link.status.value)
    }

    @Test
    fun `it outlasts a blip on the remote, and lets go once the remote has for a while`() = runTest {
        val tv = FakeAdbd()
        val link = link(tv)
        up(link)
        link.remoteDown()
        advanceTimeBy(AdbLink.LINGER_MS / 2)
        link.remoteUp(HOST)
        advanceTimeBy(AdbLink.LINGER_MS)
        assertEquals(DevLink.Up, link.status.value)
        assertEquals(1, link.connections.value)
        link.remoteDown()
        advanceTimeBy(AdbLink.LINGER_MS + 1)
        assertEquals(DevLink.Down(), link.status.value)
        assertNull(link.call("echo"))
    }

    @Test
    fun `a hung adbd costs one call and the connection, nothing more`() = runTest {
        val tv = FakeAdbd().apply { hangs = true }
        val link = link(tv)
        up(link)
        val started = currentTime
        assertNull(link.call("dumpsys tv_input"))
        assertEquals(AdbLink.CALL_TIMEOUT_MS, currentTime - started)
        tv.hangs = false
        advanceTimeBy(AdbLink.FIRST_RETRY_MS + AdbLink.RETRY_JITTER_MS)
        assertEquals("ok", link.call("echo"))
    }

    @Test
    fun `a port that moved is found again over mDNS`() = runTest {
        val tv = FakeAdbd().apply { listening = 40_123 }
        val link = link(tv, AdbPorts { AdbPorts.Announced(plain = 40_123) })
        up(link)
        assertEquals(DevLink.Up, link.status.value)
        assertEquals(listOf(5555, 40_123), tv.opened)
        assertEquals("40123", settings.clientKey(adbKey(HOST)))
    }

    @Test
    fun `while the remote shows, the connection is checked now and then`() = runTest {
        val tv = FakeAdbd()
        val link = link(tv)
        up(link)
        advanceTimeBy(5 * 60_000)
        assertEquals(emptyList<String>(), tv.commands)
        link.watch(true)
        advanceTimeBy(AdbLink.PING_MS + AdbLink.PING_JITTER_MS)
        assertEquals(listOf("echo"), tv.commands)
        tv.unreachable = true
        advanceTimeBy(AdbLink.PING_MS + AdbLink.PING_JITTER_MS)
        advanceTimeBy(AdbLink.FIRST_RETRY_MS + AdbLink.RETRY_JITTER_MS)
        assertEquals(DevLink.Down(), link.status.value)
    }

    @Test
    fun `turning it off forgets the TV and closes the connection`() = runTest {
        val tv = FakeAdbd()
        val link = link(tv)
        up(link)
        link.turnOff()
        assertEquals(DevLink.Off, link.status.value)
        assertNull(settings.clientKey(adbKey(HOST)))
        assertNull(link.call("echo"))
        advanceTimeBy(600_000)
        assertEquals(1, tv.opened.size)
    }

    private companion object {
        const val HOST = "192.0.2.10"
    }
}
