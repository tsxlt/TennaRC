package lt.tsx.tennarc.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lt.tsx.tennarc.data.Settings
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    private val MAC = Regex("""^([0-9A-Fa-f]{2})([:-]?[0-9A-Fa-f]{2}){5}$""")

    private val SEPARATORS = Regex("""[\s,;]+""")

    fun isValidMac(mac: String) = MAC.matches(mac.trim())

    fun macs(setting: String): List<String> = setting.split(SEPARATORS).filter { it.isNotEmpty() }

    fun isValidSetting(setting: String) = macs(setting).let { it.isNotEmpty() && it.all(::isValidMac) }

    fun setting(macs: List<String>) = macs.joinToString(", ")

    fun digits(mac: String) = mac.trim().replace(Regex("[:-]"), "").lowercase()

    fun packet(mac: String): ByteArray {
        require(isValidMac(mac)) { "Not a MAC address: $mac" }
        val bytes = digits(mac).chunked(2).map { it.toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + List(16) { bytes }.flatten().toByteArray()
    }

    suspend fun wake(setting: String, host: String) = withContext(Dispatchers.IO) {
        val packets = macs(setting).map(::packet).ifEmpty { throw IllegalArgumentException("No MAC address in \"$setting\"") }
        val targets = listOfNotNull("255.255.255.255", subnetBroadcast(host), host).distinct()
        var sent = 0
        var failure: IOException? = null
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (data in packets) for (target in targets) for (port in listOf(9, 7)) {
                try {
                    socket.send(DatagramPacket(data, data.size, InetAddress.getByName(target), port))
                    sent++
                } catch (e: IOException) {
                    failure = e
                }
            }
        }
        if (sent == 0) throw failure ?: IOException("Nothing sent")
    }

    private fun subnetBroadcast(host: String): String? {
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
        return parts.take(3).joinToString(".") + ".255"
    }
}

fun Settings.learnMacs(host: String, reported: List<String>, onlyIfUnset: Boolean = false) {
    val macs = reported.filter(WakeOnLan::isValidMac).distinctBy(WakeOnLan::digits)
    if (macs.isEmpty()) return
    update { prefs ->
        val theirs = WakeOnLan.macs(prefs.mac).map(WakeOnLan::digits)
        val keep = prefs.host != host ||
            theirs.isNotEmpty() && (onlyIfUnset || macs.map(WakeOnLan::digits).containsAll(theirs))
        if (keep) prefs else prefs.copy(mac = WakeOnLan.setting(macs))
    }
}
