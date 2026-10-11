package lt.tsx.tennarc.tv

import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object TvHttp {
    fun tvClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        return OkHttpClient.Builder()
            .socketFactory(NoDelaySocketFactory())
            .sslSocketFactory(tls.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .build()
    }

    fun webClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun lanClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
}

private class NoDelaySocketFactory(private val base: SocketFactory = getDefault()) : SocketFactory() {
    private fun Socket.tuned(): Socket = apply { tcpNoDelay = true }

    override fun createSocket(): Socket = base.createSocket().tuned()

    override fun createSocket(host: String, port: Int): Socket = base.createSocket(host, port).tuned()

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        base.createSocket(host, port, localHost, localPort).tuned()

    override fun createSocket(host: InetAddress, port: Int): Socket = base.createSocket(host, port).tuned()

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        base.createSocket(address, port, localAddress, localPort).tuned()
}
