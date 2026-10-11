package lt.tsx.tennarc.tv.roku

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Ssdp
import lt.tsx.tennarc.tv.SsdpReply
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.tv.TvKind
import lt.tsx.tennarc.uriOrNull

object RokuFinder {
    private const val SEARCH_TARGET = "roku:ecp"

    private val http by lazy { TvHttp.lanClient() }

    suspend fun find(): List<FoundTv> = withContext(Dispatchers.IO) {
        Ssdp.search(SEARCH_TARGET)
            .mapNotNull { reply ->
                val (host, port) = endpoint(reply) ?: return@mapNotNull null
                val info = try {
                    RokuEcp(http, host, port).deviceInfo()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logFailure("RokuFinder.find", e)
                    null
                }
                found(reply, info)
            }
            .distinctBy { it.host }
    }

    internal fun found(reply: SsdpReply, info: RokuDeviceInfo?): FoundTv? {
        val (host, _) = endpoint(reply) ?: return null
        return FoundTv(host, info?.name ?: "Roku", reply.uuid, TvKind.ROKU, info?.model)
    }

    private fun endpoint(reply: SsdpReply): Pair<String, Int>? {
        val location = uriOrNull(reply.location) ?: return null
        val host = location.host?.takeIf { location.scheme == "http" && it.isNotEmpty() } ?: return null
        return host to (location.port.takeIf { it > 0 } ?: RokuEcp.PORT)
    }
}
