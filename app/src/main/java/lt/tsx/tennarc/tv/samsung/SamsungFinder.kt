package lt.tsx.tennarc.tv.samsung

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.logFailure
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Ssdp
import lt.tsx.tennarc.tv.SsdpReply
import lt.tsx.tennarc.tv.TvHttp
import lt.tsx.tennarc.tv.TvKind
import okhttp3.OkHttpClient

object SamsungFinder {
    const val SEARCH_TARGET = "urn:samsung.com:device:RemoteControlReceiver:1"

    suspend fun find(http: OkHttpClient = TvHttp.lanClient(), restPort: Int = 8001): List<FoundTv> = withContext(Dispatchers.IO) {
        val candidates = Ssdp.search(SEARCH_TARGET).mapNotNull(::candidate).distinctBy { it.host }
        coroutineScope {
            candidates
                .map { candidate ->
                    async {
                        val device = try {
                            SamsungRest(http, candidate.host, restPort).device()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logFailure("SamsungFinder.find", e)
                            null
                        }
                        device?.let { found(candidate, it) }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    internal fun candidate(reply: SsdpReply): Candidate? {
        if (!reply.usn.contains(SEARCH_TARGET, ignoreCase = true)) return null
        val host = hostOf(reply.location)?.takeIf { it.isNotEmpty() } ?: return null
        return Candidate(host, reply.uuid)
    }

    internal fun found(candidate: Candidate, device: SamsungDevice): FoundTv? {
        if (!device.isTv) return null
        return FoundTv(candidate.host, device.name, candidate.uuid ?: device.id, TvKind.SAMSUNG, device.model)
    }

    internal data class Candidate(val host: String, val uuid: String?)
}
