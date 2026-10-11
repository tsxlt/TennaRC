package lt.tsx.tennarc.tv.androidtv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.Principal
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import lt.tsx.tennarc.catching

internal class TvTls(val identity: ClientIdentity) {
    private val factory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply { init(arrayOf(IdentityKeyManager(identity)), arrayOf(AnyServer), null) }.socketFactory
    }

    suspend fun connect(host: String, port: Int): TvSocket {
        val plain = Socket()
        return plain.blocking {
            try {
                plain.tcpNoDelay = true
                plain.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                plain.soTimeout = HANDSHAKE_TIMEOUT_MS
                val ssl = factory.createSocket(plain, host, port, true) as SSLSocket
                ssl.useClientMode = true
                ssl.startHandshake()
                TvSocket(plain, ssl)
            } catch (e: Exception) {
                plain.close()
                throw e
            }
        }
    }

    private object AnyServer : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class IdentityKeyManager(private val identity: ClientIdentity) : X509ExtendedKeyManager() {
        private fun alias(keyTypes: Array<out String>?) = ALIAS.takeIf { keyTypes == null || "RSA" in keyTypes }

        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = alias(keyType)
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias(keyType)
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getCertificateChain(alias: String?): Array<X509Certificate> = arrayOf(identity.certificate)
        override fun getPrivateKey(alias: String?) = identity.privateKey
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    }

    private companion object {
        const val ALIAS = "tenna"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val HANDSHAKE_TIMEOUT_MS = 10_000
    }
}

internal class TvSocket(private val plain: Socket, private val ssl: SSLSocket) : Closeable {
    val input: InputStream get() = ssl.inputStream
    val output: OutputStream get() = ssl.outputStream
    val peerCertificate: X509Certificate get() = ssl.session.peerCertificates.first() as X509Certificate

    var readTimeoutMs: Int
        get() = ssl.soTimeout
        set(value) {
            ssl.soTimeout = value
        }

    override fun close() {
        Dispatchers.IO.asExecutor().execute {
            catching { plain.close() }
            catching { ssl.close() }
        }
    }
}

internal suspend fun <T> Closeable.blocking(block: () -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { catching { close() } }
    Dispatchers.IO.asExecutor().execute { continuation.resumeWith(runCatching(block)) }
}

internal fun X509Certificate.fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
