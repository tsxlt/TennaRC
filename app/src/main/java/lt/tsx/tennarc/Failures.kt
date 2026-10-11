package lt.tsx.tennarc

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

inline fun <T> catching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.rethrowIfCancelled() {
    if (this is CancellationException) throw this
}

fun logFailure(what: String, error: Throwable) {
    Log.d(FAILURE_TAG, "$what failed: $error")
}

const val FAILURE_TAG = "Tenna"

fun <T> Result<T>.logged(what: String): Result<T> = onFailure { logFailure(what, it) }
