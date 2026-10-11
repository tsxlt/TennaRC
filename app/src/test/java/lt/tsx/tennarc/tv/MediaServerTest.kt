package lt.tsx.tennarc.tv

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

class MediaServerTest {
    private val bytes = ByteArray(200_000) { (it * 31 % 251).toByte() }
    private lateinit var file: File
    private lateinit var server: MediaServer

    @Before
    fun start() {
        file = File.createTempFile("media", ".mp4").apply { writeBytes(bytes) }
        server = MediaServer.start(InetAddress.getLoopbackAddress())
    }

    @After
    fun stop() {
        server.close()
        file.delete()
    }

    private inner class FileSource(override val mime: String = "video/mp4", private val known: Boolean = true) : MediaSource {
        override val length: Long? get() = if (known) file.length() else null
        override fun open(offset: Long): InputStream = file.inputStream().also { it.skipFully(offset) }
    }

    @Test
    fun `serves the whole file with its type and length`() = Connection().use { connection ->
        val path = server.add(FileSource(), "mp4")
        val reply = connection.ask("GET", path)
        assertEquals(200, reply.status)
        assertEquals("video/mp4", reply.headers["content-type"])
        assertEquals("200000", reply.headers["content-length"])
        assertEquals("bytes", reply.headers["accept-ranges"])
        assertEquals("Streaming", reply.headers["transfermode.dlna.org"])
        assertEquals(DLNA_FLAGS, reply.headers["contentfeatures.dlna.org"])
        assertArrayEquals(bytes, reply.body)
    }

    @Test
    fun `a range gets 206 and only those bytes`() = Connection().use { connection ->
        val path = server.add(FileSource())
        val reply = connection.ask("GET", path, "Range: bytes=100-199")
        assertEquals(206, reply.status)
        assertEquals("bytes 100-199/200000", reply.headers["content-range"])
        assertEquals("100", reply.headers["content-length"])
        assertArrayEquals(bytes.copyOfRange(100, 200), reply.body)
    }

    @Test
    fun `open-ended and suffix ranges run to the end`() = Connection().use { connection ->
        val path = server.add(FileSource())
        val from = connection.ask("GET", path, "Range: bytes=199000-")
        assertEquals(206, from.status)
        assertEquals("bytes 199000-199999/200000", from.headers["content-range"])
        assertArrayEquals(bytes.copyOfRange(199_000, 200_000), from.body)

        val last = connection.ask("GET", path, "Range: bytes=-500")
        assertEquals(206, last.status)
        assertEquals("bytes 199500-199999/200000", last.headers["content-range"])
        assertArrayEquals(bytes.copyOfRange(199_500, 200_000), last.body)

        val over = connection.ask("GET", path, "Range: bytes=199990-300000")
        assertEquals("bytes 199990-199999/200000", over.headers["content-range"])
        assertEquals(10, over.body.size)
    }

    @Test
    fun `a range wholly past the end is refused with the size`() = Connection().use { connection ->
        val path = server.add(FileSource())
        val reply = connection.ask("GET", path, "Range: bytes=200000-")
        assertEquals(416, reply.status)
        assertEquals("bytes */200000", reply.headers["content-range"])
        assertEquals(0, reply.body.size)
    }

    @Test
    fun `HEAD answers like GET without the body, on a connection that carries on`() = Connection().use { connection ->
        val path = server.add(FileSource())
        val head = connection.ask("HEAD", path)
        assertEquals(200, head.status)
        assertEquals("200000", head.headers["content-length"])
        assertEquals("video/mp4", head.headers["content-type"])
        assertEquals("keep-alive", head.headers["connection"])

        val ranged = connection.ask("HEAD", path, "Range: bytes=10-19")
        assertEquals(206, ranged.status)
        assertEquals("10", ranged.headers["content-length"])

        val get = connection.ask("GET", path, "Range: bytes=0-3")
        assertEquals(206, get.status)
        assertArrayEquals(bytes.copyOfRange(0, 4), get.body)
    }

    @Test
    fun `unknown, mistyped and removed paths are 404`() = Connection().use { connection ->
        val path = server.add(FileSource(), "mp4")
        assertEquals(404, connection.ask("GET", "/").status)
        assertEquals(404, connection.ask("GET", "/0123456789abcdef0123456789abcdef.mp4").status)
        assertEquals(404, connection.ask("GET", path.removeSuffix(".mp4")).status)
        assertEquals(404, connection.ask("HEAD", path + "x").status)
        assertEquals(200, connection.ask("HEAD", "$path?t=1").status)
        server.remove(path)
        assertEquals(404, connection.ask("GET", path).status)
    }

    @Test
    fun `each file goes out as its own type`() = Connection().use { connection ->
        val photo = connection.ask("HEAD", server.add(FileSource("image/jpeg"), "jpg"))
        assertEquals("image/jpeg", photo.headers["content-type"])
        assertEquals("Interactive", photo.headers["transfermode.dlna.org"])
        val song = connection.ask("HEAD", server.add(FileSource("audio/mpeg"), "mp3"))
        assertEquals("audio/mpeg", song.headers["content-type"])
        assertEquals("Streaming", song.headers["transfermode.dlna.org"])
        val asked = connection.ask("HEAD", server.add(FileSource("video/x-matroska"), "mkv"), "transferMode.dlna.org: Background")
        assertEquals("video/x-matroska", asked.headers["content-type"])
        assertEquals("Background", asked.headers["transfermode.dlna.org"])
    }

    @Test
    fun `paths are random tokens ending in the extension`() {
        val first = server.add(FileSource(), "MP4")
        val second = server.add(FileSource(), "mp4")
        assertTrue(first, Regex("/[0-9a-f]{32}\\.mp4").matches(first))
        assertNotEquals(first, second)
        assertTrue(Regex("/[0-9a-f]{32}").matches(server.add(FileSource(), "../x")))
        assertEquals("http://127.0.0.1:${server.port}$first", server.url(first))
    }

    @Test
    fun `other methods and junk are refused`() {
        val path = server.add(FileSource())
        Connection().use { assertEquals(405, it.ask("POST", path).status) }
        Connection().use { connection ->
            connection.send("NONSENSE\r\n\r\n")
            assertEquals(400, connection.reply("GET").status)
        }
    }

    @Test
    fun `a file of unknown length streams to the end, then the connection closes`() = Connection().use { connection ->
        val path = server.add(FileSource(known = false))
        val reply = connection.ask("GET", path, "Range: bytes=100-199")
        assertEquals(200, reply.status)
        assertNull(reply.headers["content-length"])
        assertNull(reply.headers["accept-ranges"])
        assertEquals("close", reply.headers["connection"])
        assertArrayEquals(bytes, reply.body)
    }

    @Test
    fun `a file that can't be read any more is 404`() = Connection().use { connection ->
        val gone = object : MediaSource {
            override val mime = "video/mp4"
            override val length = 10L
            override fun open(offset: Long): InputStream = throw FileNotFoundException()
        }
        val path = server.add(gone)
        assertEquals(200, connection.ask("HEAD", path).status)
        assertEquals(404, connection.ask("GET", path).status)
    }

    @Test
    fun `strangers get no answer`() {
        val picky = MediaServer.start(InetAddress.getLoopbackAddress()) { false }
        try {
            val path = picky.add(FileSource())
            Socket(InetAddress.getLoopbackAddress(), picky.port).use { socket ->
                socket.soTimeout = 5_000
                val answered = try {
                    socket.getOutputStream().write("GET $path HTTP/1.1\r\n\r\n".toByteArray())
                    socket.getInputStream().read() >= 0
                } catch (_: IOException) {
                    false
                }
                assertFalse(answered)
            }
        } finally {
            picky.close()
        }
    }

    @Test
    fun `ranges are read the way RFC 9110 has them`() {
        assertNull(parseRange(null, 100))
        assertEquals(0L..99L, parseRange("bytes=0-", 100))
        assertEquals(10L..19L, parseRange("bytes=10-19", 100))
        assertEquals(10L..99L, parseRange("BYTES=10-1000", 100))
        assertEquals(90L..99L, parseRange("bytes=-10", 100))
        assertEquals(0L..99L, parseRange("bytes=-1000", 100))
        assertEquals(LongRange.EMPTY, parseRange("bytes=100-", 100))
        assertEquals(LongRange.EMPTY, parseRange("bytes=-0", 100))
        assertEquals(LongRange.EMPTY, parseRange("bytes=0-", 0))
        assertNull(parseRange("items=0-5", 100))
        assertNull(parseRange("bytes=20-10", 100))
        assertNull(parseRange("bytes=a-b", 100))
        assertNull(parseRange("bytes=+1-5", 100))
        assertNull(parseRange("bytes=0-1,5-6", 100))
        assertNull(parseRange("bytes=99999999999999999999-", 100))
    }

    @Test
    fun `a subnet is matched by its prefix`() {
        fun ip(text: String) = InetAddress.getByName(text) as Inet4Address
        assertTrue(sameSubnet(ip("192.168.1.20"), ip("192.168.1.70"), 24))
        assertFalse(sameSubnet(ip("192.168.2.20"), ip("192.168.1.70"), 24))
        assertTrue(sameSubnet(ip("10.0.200.1"), ip("10.0.3.4"), 16))
        assertFalse(sameSubnet(ip("10.0.0.1"), ip("10.0.0.2"), 32))
        assertTrue(sameSubnet(ip("172.16.0.1"), ip("172.31.255.254"), 12))
        assertFalse(sameSubnet(ip("10.0.0.1"), ip("10.0.0.1"), 0))
    }

    @Test
    fun `skipping reads through a stream that can't skip`() {
        val pipe = object : InputStream() {
            private val inner = ByteArrayInputStream(bytes)
            override fun read() = inner.read()
            override fun read(b: ByteArray, off: Int, len: Int) = inner.read(b, off, len)
            override fun skip(n: Long): Long = throw IOException("Illegal seek")
        }
        pipe.skipFully(150_000)
        assertEquals(bytes[150_000].toInt() and 0xff, pipe.read())
        try {
            pipe.skipFully(100_000)
            throw AssertionError("skipped past the end")
        } catch (_: EOFException) {
        }
    }

    private class Reply(val status: Int, val headers: Map<String, String>, val body: ByteArray)

    private inner class Connection : Closeable {
        private val socket = Socket(InetAddress.getLoopbackAddress(), server.port).apply { soTimeout = 5_000 }
        private val input = BufferedInputStream(socket.getInputStream())

        fun ask(method: String, path: String, vararg headers: String): Reply {
            send(buildString {
                append("$method $path HTTP/1.1\r\nHost: tv\r\n")
                headers.forEach { append(it).append("\r\n") }
                append("\r\n")
            })
            return reply(method)
        }

        fun send(text: String) = socket.getOutputStream().write(text.toByteArray(Charsets.ISO_8859_1))

        fun reply(method: String): Reply {
            val status = line().split(' ')[1].toInt()
            val headers = generateSequence { line().takeIf { it.isNotEmpty() } }
                .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
            val length = headers["content-length"]?.toInt()
            val body = when {
                method == "HEAD" -> ByteArray(0)
                length != null -> input.readNBytes(length)
                else -> input.readAllBytes()
            }
            return Reply(status, headers, body)
        }

        private fun line(): String {
            val text = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte < 0) throw EOFException()
                if (byte == '\n'.code) return text.toString().removeSuffix("\r")
                text.append(byte.toChar())
            }
        }

        override fun close() = socket.close()
    }
}
