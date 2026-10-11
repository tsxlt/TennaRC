package lt.tsx.tennarc.tv.androidtv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.ProtocolException

fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

fun bytes(vararg parts: Any): ByteArray = ByteArrayOutputStream().apply {
    for (part in parts) when (part) {
        is Int -> write(part)
        is String -> write(part.encodeToByteArray())
        is ByteArray -> write(part)
        else -> error("Not a byte: $part")
    }
}.toByteArray()

class ProtobufTest {
    @Test
    fun `varints take seven bits a byte, low first`() {
        assertArrayEquals(bytes(8, 1), proto { int(1, 1) })
        assertArrayEquals(bytes(8, 127), proto { int(1, 127) })
        assertArrayEquals(bytes(8, 0x80, 0x01), proto { int(1, 128) })
        assertArrayEquals(bytes(8, 0xAC, 0x02), proto { int(1, 300) })
        assertArrayEquals(bytes(8, 238, 4), proto { int(1, 622) })
    }

    @Test
    fun `a negative int32 is ten bytes, as protobuf sign-extends it`() {
        val encoded = proto { int(1, -1) }
        assertArrayEquals(bytes(8, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01), encoded)
        assertEquals(-1, ProtoMessage(encoded).int(1))
    }

    @Test
    fun `zeros and empty strings are left out, as in proto3`() {
        assertArrayEquals(ByteArray(0), proto { int(1, 0); bool(2, false); string(3, "") })
    }

    @Test
    fun `an empty nested message is still there`() {
        val encoded = proto { message(11) {} }
        assertArrayEquals(bytes(90, 0), encoded)
        assertTrue(ProtoMessage(encoded).has(11))
        assertFalse(ProtoMessage(encoded).has(10))
    }

    @Test
    fun `strings, bytes and nesting round-trip`() {
        val encoded = proto {
            string(2, "hi")
            message(3) {
                bytes(1, bytes(1, 2, 3))
                bool(2, true)
            }
        }
        assertArrayEquals(bytes(18, 2, 104, 105, 26, 7, 10, 3, 1, 2, 3, 16, 1), encoded)
        val decoded = ProtoMessage(encoded)
        assertEquals("hi", decoded.string(2))
        assertArrayEquals(bytes(1, 2, 3), decoded.message(3)?.bytes(1))
        assertTrue(decoded.message(3)!!.bool(2))
        assertEquals("", decoded.string(9))
        assertEquals(0, decoded.int(9))
    }

    @Test
    fun `fixed-width fields nobody asked for are skipped`() {
        val decoded = ProtoMessage(bytes(25, 1, 2, 3, 4, 5, 6, 7, 8, 37, 1, 2, 3, 4, 8, 42))
        assertEquals(42, decoded.int(1))
    }

    @Test(expected = ProtocolException::class)
    fun `a field running past the end is refused`() {
        ProtoMessage(bytes(18, 5, 104, 105))
    }

    @Test(expected = ProtocolException::class)
    fun `a truncated varint is refused`() {
        ProtoMessage(bytes(8, 0x80))
    }

    @Test
    fun `frames carry their length first`() {
        val message = ByteArray(300) { it.toByte() }
        val out = ByteArrayOutputStream()
        out.writeFrame(message)
        out.writeFrame(bytes(8, 1))
        val written = out.toByteArray()
        assertArrayEquals(bytes(0xAC, 0x02), written.copyOfRange(0, 2))

        val input = ByteArrayInputStream(written)
        assertArrayEquals(message, input.readFrame())
        assertArrayEquals(bytes(8, 1), input.readFrame())
    }

    @Test
    fun `frames survive arriving a byte at a time`() {
        val out = ByteArrayOutputStream()
        out.writeFrame(ByteArray(200) { 7 })
        val trickle = object : InputStream() {
            private val source = ByteArrayInputStream(out.toByteArray())
            override fun read() = source.read()
            override fun read(b: ByteArray, off: Int, len: Int) = source.read(b, off, minOf(len, 1))
        }
        assertArrayEquals(ByteArray(200) { 7 }, trickle.readFrame())
    }

    @Test(expected = EOFException::class)
    fun `a frame cut short ends the stream`() {
        ByteArrayInputStream(bytes(5, 8, 1)).readFrame()
    }

    @Test(expected = ProtocolException::class)
    fun `an absurd length is refused rather than allocated`() {
        ByteArrayInputStream(bytes(0xFF, 0xFF, 0xFF, 0x7F)).readFrame()
    }
}
