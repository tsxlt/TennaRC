package lt.tsx.tennarc.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import lt.tsx.tennarc.logFailure

class TvDiscovery(private val searches: List<suspend () -> List<FoundTv>>) {
    suspend fun find(): List<FoundTv> = coroutineScope {
        searches
            .map { search -> async { quietly(search) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.host }
    }

    fun stream(): Flow<List<FoundTv>> = channelFlow {
        val seen = mutableListOf<FoundTv>()
        val lock = Mutex()
        searches.forEach { search ->
            launch {
                val tvs = quietly(search)
                lock.withLock {
                    val fresh = tvs.filter { tv -> seen.none { it.host == tv.host } }.distinctBy { it.host }
                    if (fresh.isNotEmpty()) {
                        seen += fresh
                        send(seen.toList())
                    }
                }
            }
        }
    }

    private suspend fun quietly(search: suspend () -> List<FoundTv>): List<FoundTv> = try {
        search()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logFailure("TvDiscovery.quietly", e)
        emptyList()
    }
}
