package lt.tsx.tennarc.tv.androidtv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import kotlin.coroutines.resume
import lt.tsx.tennarc.catching

fun interface AdbPorts {
    suspend fun find(host: String): Announced

    data class Announced(val plain: Int? = null, val tls: Int? = null)

    companion object {
        val None = AdbPorts { Announced() }
    }
}

internal class NsdAdbPorts(context: Context) : AdbPorts {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override suspend fun find(host: String): AdbPorts.Announced {
        val nsd = nsd ?: return AdbPorts.Announced()
        val (plain, tls) = coroutineScope {
            val plain = async { discover(nsd, PLAIN) }
            val tls = async { discover(nsd, TLS) }
            plain.await() to tls.await()
        }
        suspend fun portOf(services: List<NsdServiceInfo>): Int? =
            services.firstNotNullOfOrNull { service -> resolve(nsd, service)?.takeIf { host in it.addresses().mapNotNull(InetAddress::getHostAddress) }?.port }
        return AdbPorts.Announced(plain = portOf(plain), tls = portOf(tls))
    }

    private suspend fun discover(nsd: NsdManager, type: String): List<NsdServiceInfo> {
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
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
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

    private companion object {
        const val PLAIN = "_adb._tcp."
        const val TLS = "_adb-tls-connect._tcp."
        const val LISTEN_MS = 2_500L
        const val RESOLVE_MS = 1_500L
    }
}
