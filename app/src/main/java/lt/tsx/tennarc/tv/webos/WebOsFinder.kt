package lt.tsx.tennarc.tv.webos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.hostOf
import lt.tsx.tennarc.logged
import lt.tsx.tennarc.tv.DlnaClient
import lt.tsx.tennarc.tv.FoundTv
import lt.tsx.tennarc.tv.Ssdp

class WebOsFinder(private val dlna: DlnaClient) {
    suspend fun find(): List<FoundTv> = withContext(Dispatchers.IO) {
        Ssdp.search("urn:lge-com:service:webos-second-screen:1")
            .mapNotNull { reply ->
                val host = hostOf(reply.location) ?: return@mapNotNull null
                val name = catching { dlna.describe(reply.location).name }.logged("WebOsFinder.find").getOrNull()
                FoundTv(host, name?.takeIf { it.isNotBlank() } ?: "LG webOS TV", reply.uuid)
            }
            .distinctBy { it.host }
    }
}
