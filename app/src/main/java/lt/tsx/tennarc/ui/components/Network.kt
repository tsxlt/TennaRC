package lt.tsx.tennarc.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logged

@Composable
fun rememberOnLocalNetwork(): Boolean {
    val context = LocalContext.current
    val connectivity = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    var local by remember(connectivity) { mutableStateOf(connectivity?.let(::isLocal) ?: true) }
    DisposableEffect(connectivity) {
        val manager = connectivity ?: return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                local = capabilities.isLocal
            }

            override fun onLost(network: Network) {
                local = isLocal(manager)
            }
        }
        val registered = catching { manager.registerDefaultNetworkCallback(callback) }.logged("Network.onLost").isSuccess
        onDispose { if (registered) catching { manager.unregisterNetworkCallback(callback) }.logged("Network.onLost") }
    }
    return local
}

private fun isLocal(manager: ConnectivityManager): Boolean =
    catching { manager.getNetworkCapabilities(manager.activeNetwork)?.isLocal ?: false }.getOrDefault(true)

private val NetworkCapabilities.isLocal: Boolean
    get() = hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
