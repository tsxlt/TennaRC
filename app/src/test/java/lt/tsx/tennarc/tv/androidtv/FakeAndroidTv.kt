package lt.tsx.tennarc.tv.androidtv

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.Principal
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.random.Random

internal class FakeAndroidTv : Closeable {
    val identity = TestIdentity("atvremote/fake/Fake TV/00:11:22:33:44:55")
    val fingerprint: String = identity.certificate.fingerprint()

    val paired: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val codes = LinkedBlockingQueue<String>()

    private val received = LinkedBlockingQueue<ProtoMessage>()

    @Volatile var refuseCodes = 0

    @Volatile var hangUpAfterCode = false

    @Volatile private var phone: OutputStream? = null
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()

    private val pairingServer = serverSocket { true }
    private val remoteServer = serverSocket { it in paired }
    val pairingPort: Int get() = pairingServer.localPort
    val remotePort: Int get() = remoteServer.localPort

    init {
        serve(pairingServer, ::pair)
        serve(remoteServer, ::remote)
    }

    fun next(): ProtoMessage = received.poll(5, TimeUnit.SECONDS) ?: error("The phone sent nothing")

    fun nothingSent(): Boolean = received.poll(300, TimeUnit.MILLISECONDS) == null

    fun send(message: ByteArray) = synchronized(this) { checkNotNull(phone) { "No phone connected" }.writeFrame(message) }

    override fun close() {
        pairingServer.close()
        remoteServer.close()
        sockets.forEach { runCatching { it.close() } }
    }

    private fun pair(socket: SSLSocket) {
        val phoneCertificate = socket.session.peerCertificates.first() as X509Certificate
        var expected: ByteArray? = null
        while (true) {
            val message = ProtoMessage(socket.inputStream.readFrame())
            val out = socket.outputStream
            when {
                message.has(10) -> out.writeFrame(bytes(8, 2, 16, 200, 1, 90, 0))
                message.has(20) -> out.writeFrame(bytes(8, 2, 16, 200, 1, 162, 1, 8, 18, 4, 8, 3, 16, 6, 24, 1))
                message.has(30) -> {
                    val nonce = Random.nextBytes(2)
                    val secret = secret(phoneCertificate, nonce).also { expected = it }
                    out.writeFrame(bytes(8, 2, 16, 200, 1, 250, 1, 0))
                    codes.put("%02X%s".format(secret[0], nonce.toHexString().uppercase()))
                    if (hangUpAfterCode) return
                }
                message.has(40) -> {
                    val right = message.message(40)?.bytes(1) contentEquals expected
                    if (!right || refuseCodes > 0) {
                        if (right) refuseCodes--
                        out.writeFrame(bytes(8, 2, 16, 146, 3))
                        return
                    }
                    paired += phoneCertificate.fingerprint()
                    out.writeFrame(bytes(8, 2, 16, 200, 1, 202, 2, 34, 10, 32, expected!!))
                }
            }
        }
    }

    private fun remote(socket: SSLSocket) {
        socket.startHandshake()
        val out = socket.outputStream
        out.writeFrame(bytes(10, 20, 8, 255, 4, 18, 15, 10, 5, "Model", 18, 6, "Vendor"))
        received.put(ProtoMessage(socket.inputStream.readFrame()))
        out.writeFrame(bytes(18, 0))
        received.put(ProtoMessage(socket.inputStream.readFrame()))
        synchronized(this) {
            phone = out
            out.writeFrame(bytes(194, 2, 2, 8, 1))
            out.writeFrame(bytes(146, 3, 6, 48, 100, 56, 12, 64, 0))
            out.writeFrame(bytes(162, 1, 33, 10, 31, 98, 29, AndroidTvApps.YOUTUBE))
        }
        while (true) received.put(ProtoMessage(socket.inputStream.readFrame()))
    }

    private fun secret(phone: X509Certificate, nonce: ByteArray): ByteArray {
        val client = phone.publicKey as RSAPublicKey
        val server = identity.certificate.publicKey as RSAPublicKey
        val digits = listOf(client.modulus.toString(16), "0" + client.publicExponent.toString(16), server.modulus.toString(16), "0" + server.publicExponent.toString(16))
        return MessageDigest.getInstance("SHA-256").digest((digits.joinToString("") + nonce.toHexString()).hexToByteArray())
    }

    private fun serve(server: SSLServerSocket, handle: (SSLSocket) -> Unit) = thread(isDaemon = true) {
        while (true) {
            val socket = try {
                server.accept() as SSLSocket
            } catch (_: IOException) {
                return@thread
            }
            sockets += socket
            thread(isDaemon = true) {
                try {
                    handle(socket)
                } catch (_: IOException) {
                } finally {
                    socket.close()
                }
            }
        }
    }

    private fun serverSocket(trusts: (String) -> Boolean): SSLServerSocket {
        val keys = object : X509ExtendedKeyManager() {
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = "tv"
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = "tv"
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf("tv")
            override fun getCertificateChain(alias: String?) = arrayOf(identity.certificate)
            override fun getPrivateKey(alias: String?) = identity.privateKey
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
        }
        val phones = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) {
                if (!trusts(chain.first().fingerprint())) throw CertificateException("Not a paired phone")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(arrayOf(keys), arrayOf(phones), null) }
        return (context.serverSocketFactory.createServerSocket(0, 8, InetAddress.getLoopbackAddress()) as SSLServerSocket)
            .apply { needClientAuth = true }
    }
}
