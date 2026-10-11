package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.math.BigInteger
import java.net.ProtocolException
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

internal class PairingRejectedException(message: String) : IOException(message)

internal object Polo {
    const val STATUS_OK = 200
    private const val PROTOCOL_VERSION = 2
    private const val SERVICE_NAME = "atvremote"
    private const val ENCODING_HEXADECIMAL = 3
    private const val CODE_LENGTH = 6
    private const val ROLE_INPUT = 1

    private const val PROTOCOL_VERSION_FIELD = 1
    private const val STATUS = 2
    private const val PAIRING_REQUEST = 10
    private const val PAIRING_REQUEST_ACK = 11
    private const val OPTIONS = 20
    private const val CONFIGURATION = 30
    private const val CONFIGURATION_ACK = 31
    private const val SECRET = 40
    private const val SECRET_ACK = 41

    enum class Step { PAIRING_REQUEST_ACK, OPTIONS, CONFIGURATION_ACK, SECRET_ACK, UNKNOWN }

    class Reply(val status: Int, val step: Step)

    fun pairingRequest(clientName: String) = outer {
        message(PAIRING_REQUEST) {
            string(1, SERVICE_NAME)
            string(2, clientName)
        }
    }

    fun options() = outer {
        message(OPTIONS) {
            message(1) { hexCode() }
            int(3, ROLE_INPUT)
        }
    }

    fun configuration() = outer {
        message(CONFIGURATION) {
            message(1) { hexCode() }
            int(2, ROLE_INPUT)
        }
    }

    fun secret(secret: ByteArray) = outer { message(SECRET) { bytes(1, secret) } }

    fun parse(bytes: ByteArray): Reply {
        val message = ProtoMessage(bytes)
        val step = when {
            message.has(PAIRING_REQUEST_ACK) -> Step.PAIRING_REQUEST_ACK
            message.has(OPTIONS) -> Step.OPTIONS
            message.has(CONFIGURATION_ACK) -> Step.CONFIGURATION_ACK
            message.has(SECRET_ACK) -> Step.SECRET_ACK
            else -> Step.UNKNOWN
        }
        return Reply(message.int(STATUS), step)
    }

    private fun ProtoWriter.hexCode() {
        int(1, ENCODING_HEXADECIMAL)
        int(2, CODE_LENGTH)
    }

    private fun outer(body: ProtoWriter.() -> Unit) = proto {
        int(PROTOCOL_VERSION_FIELD, PROTOCOL_VERSION)
        int(STATUS, STATUS_OK)
        body()
    }
}

internal fun pairingSecret(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
    if (code.length != 6 || !code.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) return null
    val digest = MessageDigest.getInstance("SHA-256")
    for (number in listOf(client.modulus, client.publicExponent, server.modulus, server.publicExponent)) {
        digest.update(number.unsignedBytes())
    }
    digest.update(code.substring(2).hexToByteArray())
    return digest.digest().takeIf { it[0] == code.substring(0, 2).toInt(16).toByte() }
}

private fun BigInteger.unsignedBytes(): ByteArray = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()

internal class PairingSession private constructor(private val socket: TvSocket, private val clientKey: RSAPublicKey) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val replies = Channel<Polo.Reply>(Channel.UNLIMITED)
    private val serverCertificate = socket.peerCertificate
    private val mutableHungUp = CompletableDeferred<Unit>()

    val hungUp: Deferred<Unit> get() = mutableHungUp

    val fingerprint: String = serverCertificate.fingerprint()

    private fun listen() = scope.launch {
        try {
            while (true) replies.send(Polo.parse(socket.input.readFrame()))
        } catch (_: IOException) {
        } finally {
            replies.close()
            mutableHungUp.complete(Unit)
        }
    }

    suspend fun finish(code: String): Boolean {
        val secret = pairingSecret(clientKey, serverCertificate.publicKey as RSAPublicKey, code) ?: return false
        withContext(Dispatchers.IO) { socket.output.writeFrame(Polo.secret(secret)) }
        val reply = withTimeoutOrNull(REPLY_TIMEOUT_MS.toLong()) { replies.receiveCatching().getOrNull() }
            ?: throw PairingRejectedException("No answer to the code")
        if (reply.status != Polo.STATUS_OK || reply.step != Polo.Step.SECRET_ACK) {
            throw PairingRejectedException("The TV refused the code (status ${reply.status})")
        }
        return true
    }

    fun close() {
        scope.cancel()
        socket.close()
    }

    companion object {
        private const val REPLY_TIMEOUT_MS = 10_000

        suspend fun open(tls: TvTls, host: String, port: Int): PairingSession {
            val socket = tls.connect(host, port)
            try {
                socket.blocking {
                    socket.readTimeoutMs = REPLY_TIMEOUT_MS
                    socket.output.writeFrame(Polo.pairingRequest(CLIENT_NAME))
                    handshake@ while (true) {
                        val reply = Polo.parse(socket.input.readFrame())
                        if (reply.status != Polo.STATUS_OK) throw PairingRejectedException("The TV refused to pair (status ${reply.status})")
                        when (reply.step) {
                            Polo.Step.PAIRING_REQUEST_ACK -> socket.output.writeFrame(Polo.options())
                            Polo.Step.OPTIONS -> socket.output.writeFrame(Polo.configuration())
                            Polo.Step.CONFIGURATION_ACK -> break@handshake
                            else -> throw ProtocolException("Unexpected ${reply.step} while pairing")
                        }
                    }
                    socket.readTimeoutMs = 0
                }
                val publicKey = tls.identity.certificate.publicKey as RSAPublicKey
                return PairingSession(socket, publicKey).also { it.listen() }
            } catch (e: Throwable) {
                socket.close()
                throw e
            }
        }
    }
}
