package lt.tsx.tennarc.tv.androidtv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.TvKind
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume

object AndroidTvFinder {
    const val SERVICE_TYPE = "_androidtvremote2._tcp."
    private const val LISTEN_MS = 3_000L
    private const val RESOLVE_MS = 2_000L

    suspend fun find(context: Context): List<FoundTv> {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return emptyList()
        return discover(nsd).mapNotNull { service ->
            val resolved = resolve(nsd, service) ?: return@mapNotNull null
            foundTv(resolved.serviceName, resolved.addresses(), resolved.attributes)
        }.distinctBy { it.host }
    }

    private suspend fun discover(nsd: NsdManager): List<NsdServiceInfo> {
        val found = mutableListOf<NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(found) { found += service }
            }

            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            delay(LISTEN_MS)
        } finally {
            catching { nsd.stopServiceDiscovery(listener) }
        }
        return synchronized(found) { found.distinctBy { it.serviceName } }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, service: NsdServiceInfo): NsdServiceInfo? = withTimeoutOrNull(RESOLVE_MS) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) = continuation.resume(resolved)
                override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) = continuation.resume(null)
            }
            nsd.resolveService(service, listener)
            continuation.invokeOnCancellation {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) catching { nsd.stopServiceResolution(listener) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.addresses(): List<InetAddress> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) hostAddresses else listOfNotNull(host)

    internal fun foundTv(serviceName: String, addresses: List<InetAddress>, attributes: Map<String, ByteArray?>): FoundTv? {
        val address = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return null
        val host = address.hostAddress ?: return null
        val name = unescape(serviceName).ifBlank { TvKind.ANDROID_TV.label }
        val id = attributes["bt"]?.decodeToString()?.takeIf { it.isNotBlank() }
        return FoundTv(host = host, name = name, id = id, kind = TvKind.ANDROID_TV)
    }

    internal fun unescape(name: String): String {
        val bytes = ByteArrayOutputStream()
        var last = 0
        for (match in DNS_ESCAPE.findAll(name)) {
            bytes.write(name.substring(last, match.range.first).encodeToByteArray())
            val (decimal, literal) = match.destructured
            if (decimal.isNotEmpty()) bytes.write(decimal.toInt()) else bytes.write(literal.encodeToByteArray())
            last = match.range.last + 1
        }
        bytes.write(name.substring(last).encodeToByteArray())
        return bytes.toByteArray().decodeToString()
    }

    private val DNS_ESCAPE = Regex("""\\(?:(\d{3})|(.))""")
}
