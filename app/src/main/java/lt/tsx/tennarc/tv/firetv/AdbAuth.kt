package lt.tsx.tennarc.tv.firetv

import dadb.AdbKeyPair
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.rethrowIfCancelled

class AdbKeys(private val dir: File) {
    private val privateFile = File(dir, "adbkey")
    private val publicFile = File(dir, "adbkey.pub")
    private var loaded: Loaded? = null

    private class Loaded(val pair: AdbKeyPair, val privateKey: PrivateKey)

    @Synchronized
    fun pair(): AdbKeyPair = load().pair

    @Synchronized
    fun sign(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, load().privateKey)
        cipher.update(SIGNATURE_PADDING)
        return cipher.doFinal(token)
    }

    @Synchronized
    fun forget() {
        loaded = null
        privateFile.delete()
        publicFile.delete()
    }

    private fun load(): Loaded {
        loaded?.let { return it }
        val keys = try {
            read()
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            logFailure("AdbAuth.load", e)
            dir.mkdirs()
            AdbKeyPair.generate(privateFile, publicFile)
            read()
        }
        return keys.also { loaded = it }
    }

    private fun read(): Loaded {
        if (!publicFile.isFile) throw IOException("No public key")
        val pem = privateFile.readText()
        val der = Base64.getDecoder().decode(pem.replace(PEM_ARMOR, "").filterNot { it.isWhitespace() })
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
        return Loaded(AdbKeyPair.read(privateFile, publicFile), privateKey)
    }

    private companion object {
        val PEM_ARMOR = Regex("-----(BEGIN|END) PRIVATE KEY-----")

        val SIGNATURE_PADDING: ByteArray = run {
            val digestInfo = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
            val padding = ByteArray(256 - 20 - digestInfo.size) { 0xFF.toByte() }
            padding[0] = 0
            padding[1] = 1
            padding[padding.lastIndex] = 0
            padding + digestInfo
        }
    }
}

internal object AdbAuthProbe {
    private const val A_CNXN = 0x4e584e43
    private const val A_AUTH = 0x48545541
    private const val A_STLS = 0x534c5453
    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val VERSION = 0x01000000
    private const val MAX_PAYLOAD = 4096
    private const val MAX_REPLY = 1024 * 1024

    fun trusted(input: InputStream, output: OutputStream, sign: (ByteArray) -> ByteArray): Boolean {
        val reader = DataInputStream(input)
        write(output, A_CNXN, VERSION, MAX_PAYLOAD, "host::\u0000".toByteArray())
        val challenge = read(reader)
        if (challenge.command == A_CNXN) return true
        if (challenge.command == A_STLS) throw AdbTlsRequiredException()
        if (challenge.command != A_AUTH || challenge.arg0 != AUTH_TOKEN) throw IOException("Unexpected ADB reply ${challenge.command.toString(16)}")
        write(output, A_AUTH, AUTH_SIGNATURE, 0, sign(challenge.payload))
        val verdict = read(reader)
        return when (verdict.command) {
            A_CNXN -> true
            A_AUTH -> false
            else -> throw IOException("Unexpected ADB reply ${verdict.command.toString(16)}")
        }
    }

    private class Message(val command: Int, val arg0: Int, val payload: ByteArray)

    private fun write(output: OutputStream, command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(payload.size)
            .putInt(payload.sumOf { it.toInt() and 0xFF })
            .putInt(command.inv())
        output.write(header.array() + payload)
        output.flush()
    }

    private fun read(input: DataInputStream): Message {
        val bytes = ByteArray(24).also(input::readFully)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.getInt()
        val arg0 = header.getInt()
        header.getInt()
        val length = header.getInt()
        if (length !in 0..MAX_REPLY) throw IOException("ADB message of $length bytes")
        return Message(command, arg0, ByteArray(length).also(input::readFully))
    }
}
