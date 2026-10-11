package lt.tsx.tennarc

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashUploadTest {
    private val server = MockWebServer()

    @Before fun start() = server.start()

    @After fun stop() = server.close()

    @Test
    fun `a report goes to the site as plain text`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("""{"ok":true}""").build())
        assertTrue(CrashReports.upload("TennaRC 1.0\nboom", server.url("/api/crash").toString(), ReportEnvelopeTest.TEST_PUBLIC))
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/crash", request.url.encodedPath)
        assertEquals("text/plain; charset=utf-8", request.headers["Content-Type"])
        val body = request.body!!.utf8()
        assertTrue(body.startsWith(ReportEnvelope.PREFIX))
        assertEquals("TennaRC 1.0\nboom", ReportEnvelopeTest.open(body, ReportEnvelopeTest.TEST_SECRET).decodeToString())
    }

    @Test
    fun `a refusal or no answer counts as not sent`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(429).build())
        assertFalse(CrashReports.upload("boom", server.url("/api/crash").toString(), ReportEnvelopeTest.TEST_PUBLIC))
        val gone = server.url("/api/crash").toString()
        server.close()
        assertFalse(CrashReports.upload("boom", gone, ReportEnvelopeTest.TEST_PUBLIC))
    }
}
