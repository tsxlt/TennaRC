package lt.tsx.tennarc.tv.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProtocolException

private const val VARINT = 0
private const val FIXED64 = 1
private const val LENGTH_DELIMITED = 2
private const val FIXED32 = 5

private const val MAX_FRAME = 1 shl 20

internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long): ProtoWriter = apply {
        if (value == 0L) return@apply
        tag(field, VARINT)
        out.writeVarint(value)
    }

    fun int(field: Int, value: Int) = int(field, value.toLong())

    fun bool(field: Int, value: Boolean) = int(field, if (value) 1L else 0L)

    fun string(field: Int, value: String) = bytes(field, value.encodeToByteArray())

    fun bytes(field: Int, value: ByteArray): ProtoWriter = apply {
        if (value.isEmpty()) return@apply
        tag(field, LENGTH_DELIMITED)
        out.writeVarint(value.size.toLong())
        out.write(value)
    }

    fun message(field: Int, build: ProtoWriter.() -> Unit): ProtoWriter = apply {
        val body = proto(build)
        tag(field, LENGTH_DELIMITED)
        out.writeVarint(body.size.toLong())
        out.write(body)
    }

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = out.writeVarint((field.toLong() shl 3) or wireType.toLong())
}

internal fun proto(build: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(build).toByteArray()

internal class ProtoMessage(bytes: ByteArray) {
    private val varints = HashMap<Int, Long>()
    private val chunks = HashMap<Int, MutableList<ByteArray>>()

    init {
        var pos = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= bytes.size) throw ProtocolException("Truncated varint")
                if (shift > 63) throw ProtocolException("Varint too long")
                val byte = bytes[pos++].toInt()
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
        }
        fun skip(count: Int) {
            if (count > bytes.size - pos) throw ProtocolException("Truncated field")
            pos += count
        }
        while (pos < bytes.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            when (val wireType = (key and 7).toInt()) {
                VARINT -> varints[field] = varint()
                LENGTH_DELIMITED -> {
                    val length = varint()
                    if (length < 0 || length > bytes.size - pos) throw ProtocolException("Field $field runs past the message")
                    chunks.getOrPut(field) { mutableListOf() } += bytes.copyOfRange(pos, pos + length.toInt())
                    pos += length.toInt()
                }
                FIXED64 -> skip(8)
                FIXED32 -> skip(4)
                else -> throw ProtocolException("Wire type $wireType in field $field")
            }
        }
    }

    fun has(field: Int) = field in varints || field in chunks

    fun int(field: Int): Int = (varints[field] ?: 0L).toInt()

    fun bool(field: Int): Boolean = (varints[field] ?: 0L) != 0L

    fun string(field: Int): String = chunks[field]?.last()?.decodeToString().orEmpty()

    fun bytes(field: Int): ByteArray? = chunks[field]?.last()

    fun message(field: Int): ProtoMessage? = bytes(field)?.let(::ProtoMessage)
}

internal fun OutputStream.writeFrame(message: ByteArray) {
    val frame = ByteArrayOutputStream(message.size + 5)
    frame.writeVarint(message.size.toLong())
    frame.write(message)
    write(frame.toByteArray())
    flush()
}

internal fun InputStream.readFrame(): ByteArray {
    var length = 0
    var shift = 0
    while (true) {
        val byte = read()
        if (byte < 0) throw EOFException(if (shift == 0) "The TV closed the connection" else "Closed mid-frame")
        length = length or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0) break
        shift += 7
        if (shift > 28) throw ProtocolException("Frame length too long")
    }
    if (length > MAX_FRAME) throw ProtocolException("A $length-byte frame")
    val message = ByteArray(length)
    var read = 0
    while (read < length) {
        val count = read(message, read, length - read)
        if (count < 0) throw EOFException("Closed mid-frame")
        read += count
    }
    return message
}

private fun ByteArrayOutputStream.writeVarint(value: Long) {
    var rest = value
    while (rest and 0x7FL.inv() != 0L) {
        write(((rest and 0x7F) or 0x80).toInt())
        rest = rest ushr 7
    }
    write(rest.toInt())
}
