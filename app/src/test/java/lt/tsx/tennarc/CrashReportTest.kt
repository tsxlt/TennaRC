package lt.tsx.tennarc

import lt.tsx.tennarc.data.Prefs
import lt.tsx.tennarc.data.SavedTv
import lt.tsx.tennarc.tv.Connection
import lt.tsx.tennarc.tv.Feature
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReportTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val header = CrashHeader("0.2.0-3-gabc1234", 43, "release", "15", 35, "Google Pixel 8")

    private val prefs = Prefs(
        tvName = "Living Room",
        host = "192.168.1.70",
        mac = "02:00:00:00:00:01",
        otherTvs = listOf(SavedTv("Bedroom Telly", "tv-upstairs.home.arpa", TvKind.ROKU, "")),
        recent = listOf("breaking bad", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
    )
    private val state = TvState(
        host = "192.168.1.70",
        kind = TvKind.ANDROID_TV,
        features = setOf(Feature.POINTER, Feature.TEXT_INPUT),
        connection = Connection.Connected,
    )
    private val scene = CrashScene.of(state, prefs)

    private fun boom(): Nothing = throw IllegalStateException(
        "failed to connect to /192.168.1.70 (port 6466) from /192.168.1.50 (port 41234)\n" +
            "GET http://192.168.1.70:8060/query/apps?x=1 for Living Room, wake 40-2F-86-2C-0B-1D\n" +
            "via fe80::1c2b:3cff:fe4d:5e6f%wlan0 and [2001:db8::1]:8002, mapped ::ffff:10.0.0.7\n" +
            "bare 402f862c0b1e, searched for \"some typed text\" on www.example.org\n" +
            "mail me@example.com, host LGwebOSTV.local, other bedroom telly at tv-upstairs.home.arpa",
    )

    private fun report(thread: String = "main"): String {
        val error = try {
            try {
                boom()
            } catch (e: IllegalStateException) {
                throw RuntimeException("wrapped: ws://10.1.2.3:3001/ and breaking bad", e)
            }
        } catch (e: RuntimeException) {
            e
        }
        return CrashReport.build(error, thread, header, scene)
    }

    @Test
    fun `the header says which build, phone and kind of TV`() {
        val text = report()
        assertTrue(text.startsWith("TennaRC 0.2.0-3-gabc1234 (43, release)\n"))
        assertTrue("Android 15 (API 35), Google Pixel 8" in text)
        assertTrue("TV: Android TV, connected" in text)
        assertTrue("Features: POINTER, TEXT_INPUT" in text)
        assertTrue("Thread: main" in text)
    }

    @Test
    fun `no address, name, search or typed text gets into the report`() {
        val text = report(thread = "OkHttp http://192.168.1.70:8060/keypress/Home")
        listOf(
            "192.168", "10.0.0.7", "10.1.2.3", "fe80", "2001:db8", "ffff:", "5e6f", "wlan0",
            "40:2f", "40-2F", "402f862c0b1e", "http", "ws://", "www.", "example", "youtube", "dQw4w9WgXcQ",
            "Living Room", "Bedroom Telly", "bedroom telly", "tv-upstairs", "LGwebOSTV", "breaking bad",
            "some typed text", "me@",
        ).forEach { leak -> assertFalse("report has \"$leak\":\n$text", leak in text) }
        listOf("<ip>", "<mac>", "<url>", "<email>", "<host>", "<private>", "\"…\"").forEach { mark ->
            assertTrue("report lacks $mark:\n$text", mark in text)
        }
    }

    @Test
    fun `the stack trace itself survives the scrubbing`() {
        val text = report()
        assertTrue("java.lang.RuntimeException: wrapped:" in text)
        assertTrue("Caused by: java.lang.IllegalStateException: failed to connect" in text)
        assertTrue("at lt.tsx.tennarc.CrashReportTest.boom(CrashReportTest.kt:" in text)
        assertTrue("(port 6466)" in text)
    }

    @Test
    fun `times and frames are not mistaken for addresses`() {
        val scrubber = Scrubber()
        assertEquals("at 12:34:56 in Foo::bar (Foo.kt:120)", scrubber.scrub("at 12:34:56 in Foo::bar (Foo.kt:120)"))
        assertEquals("lt.tsx.tennarc.TennaApp.onCreate(TennaApp.kt:42)", Scrubber(listOf("Tenna")).scrub("lt.tsx.tennarc.TennaApp.onCreate(TennaApp.kt:42)"))
    }

    @Test
    fun `each address form is caught`() {
        val scrubber = Scrubber()
        assertEquals("<ip>:3001", scrubber.scrub("192.168.1.70:3001"))
        assertEquals("[<ip>]:8002", scrubber.scrub("[2001:db8:85a3:0:0:8a2e:370:7334]:8002"))
        assertEquals("<ip>", scrubber.scrub("fe80::1%wlan0"))
        assertEquals("addr:<ip>", scrubber.scrub("addr:fe80::1"))
        assertEquals("<mac>", scrubber.scrub("02:00:00:00:00:01"))
        assertEquals("mac:<mac>:", scrubber.scrub("mac:02:00:00:00:00:01:"))
        assertEquals("<url>", scrubber.scrub("content://media/external/video/42"))
        assertEquals("<url>", scrubber.scrub("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals("GET <url> failed", Scrubber(listOf("192.168.1.70")).scrub("GET http://192.168.1.70:8060/query?q=x failed"))
    }

    @Test
    fun `a huge error is cut to 64 KB and keeps its header`() {
        val error = IllegalStateException("x".repeat(500_000) + "\n" + "line 192.168.0.1\n".repeat(20_000))
        val text = CrashReport.build(error, "main", header, scene)
        assertTrue(text.encodeToByteArray().size <= CrashReport.MAX_BYTES)
        assertTrue(text.startsWith("TennaRC 0.2.0-3-gabc1234"))
        assertTrue(text.endsWith("[cut to 64 KB]\n"))
        assertFalse("192.168" in text)
    }

    @Test
    fun `the version comes back out of a report`() {
        assertEquals("0.2.0-3-gabc1234", CrashReport.versionOf(report()))
        assertNull(CrashReport.versionOf("something else"))
    }

    @Test
    fun `a crash before the app set up still makes a report`() {
        val text = CrashReport.build(IllegalStateException("early"), "main", header, CrashScene.of(null, null))
        assertTrue("IllegalStateException: early" in text)
        assertFalse("TV:" in text)
    }

    @Test
    fun `the handler keeps one report, then hands the crash on`() {
        val dir = temp.newFolder()
        var handedOn: Throwable? = null
        val recorder = CrashRecorder(dir, header, { _, e -> handedOn = e }) { scene }
        val first = IllegalStateException("first-crash")
        recorder.uncaughtException(Thread.currentThread(), first)
        assertSame(first, handedOn)
        recorder.uncaughtException(Thread.currentThread(), IllegalArgumentException("second-crash"))

        val pending = CrashReports.pending(dir)!!
        assertTrue("IllegalArgumentException: second-crash" in pending)
        assertFalse("first-crash" in pending)
        assertEquals(1, dir.listFiles()!!.size)

        CrashReports.consume(dir)
        assertNull(CrashReports.pending(dir))
    }

    @Test
    fun `a failing scene still records the crash`() {
        val dir = temp.newFolder()
        val recorder = CrashRecorder(dir, header, { _, _ -> }) { error("not set up") }
        recorder.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        assertTrue("IllegalStateException: boom" in CrashReports.pending(dir)!!)
    }
}
