package lt.tsx.tennarc.tv.firetv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.TvKind
import java.io.IOException
import java.net.Inet4Address
import kotlin.coroutines.resume

class FireTvFinder(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)

    suspend fun find(): List<FoundTv> {
        val services = mutableListOf<NsdServiceInfo>()
        withTimeoutOrNull(SEARCH_MS) { announcements().collect { services += it } }
        return services
            .distinctBy { it.serviceName }
            .mapNotNull { withTimeoutOrNull(RESOLVE_MS) { resolve(it) } }
            .distinctBy { it.host }
    }

    private fun announcements(): Flow<NsdServiceInfo> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                trySend(info)
            }

            override fun onStartDiscoveryFailed(type: String, code: Int) {
                close(IOException("mDNS discovery failed: $code"))
            }

            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { catching { nsd.stopServiceDiscovery(listener) } }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(service: NsdServiceInfo): FoundTv? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(
            service,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) = cont.resume(null)

                override fun onServiceResolved(info: NsdServiceInfo) =
                    cont.resume(info.address()?.let { fireTv(info.serviceName, it, info.attributes) })
            },
        )
    }

    private fun NsdServiceInfo.address(): String? {
        @Suppress("DEPRECATION")
        val addresses = if (Build.VERSION.SDK_INT >= 34) hostAddresses else listOfNotNull(host)
        return (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())?.hostAddress
    }

    private companion object {
        const val SERVICE_TYPE = "_amzn-wplay._tcp"
        const val SEARCH_MS = 3_000L
        const val RESOLVE_MS = 2_000L
    }
}

internal fun fireTv(serviceName: String, host: String, txt: Map<String, ByteArray?>): FoundTv {
    fun text(key: String) = txt[key]?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }
    val name = text("n") ?: serviceName.takeUnless { it.startsWith("amzn.") || it.isBlank() } ?: "Fire TV"
    return FoundTv(host = host, name = name, id = text("u"), kind = TvKind.FIRE_TV)
}
