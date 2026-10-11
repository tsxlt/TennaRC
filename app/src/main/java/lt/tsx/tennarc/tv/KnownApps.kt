package lt.tsx.tennarc.tv

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lt.tsx.tennarc.catching
import lt.tsx.tennarc.logged
import org.json.JSONArray
import org.json.JSONObject

internal class KnownApps(private val dir: File, private val scope: CoroutineScope) {
    data class Known(val apps: List<TvApp>, val inputs: List<TvApp>, val channels: List<TvChannel>)

    private val memory = HashMap<String, Known?>()

    fun get(host: String): Known? = memory.getOrPut(host) { read(file(host)) }

    fun put(host: String, known: Known) {
        if (memory[host] == known) return
        memory[host] = known
        val file = file(host)
        scope.launch(Dispatchers.IO) { write(file, known) }
    }

    private fun file(host: String): File =
        File(dir, MessageDigest.getInstance("SHA-256").digest(host.toByteArray()).joinToString("") { "%02x".format(it) }.take(32) + ".json")

    private fun read(file: File): Known? = catching {
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        Known(apps(json.optJSONArray("apps")), apps(json.optJSONArray("inputs")), channels(json.optJSONArray("channels")))
    }.logged("KnownApps.read").getOrNull()

    private fun write(file: File, known: Known) = catching {
        dir.mkdirs()
        val json = JSONObject()
            .put("apps", JSONArray(known.apps.map(::app)))
            .put("inputs", JSONArray(known.inputs.map(::app)))
            .put("channels", JSONArray(known.channels.map { JSONObject().put("id", it.id).put("number", it.number).put("name", it.name) }))
        val partial = File(dir, file.name + ".part")
        partial.writeText(json.toString())
        partial.renameTo(file)
    }.logged("KnownApps.write")

    private fun app(app: TvApp) = JSONObject()
        .put("id", app.id)
        .put("title", app.title)
        .put("iconUrl", app.iconUrl ?: JSONObject.NULL)
        .put("color", app.color ?: JSONObject.NULL)
        .put("searchable", app.searchable)
        .put("input", app.input)
        .put("plugged", app.plugged ?: JSONObject.NULL)

    private fun apps(array: JSONArray?): List<TvApp> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        TvApp(
            id = o.optString("id").ifEmpty { return@mapNotNull null },
            title = o.optString("title"),
            iconUrl = o.optString("iconUrl").takeIf { !o.isNull("iconUrl") && it.isNotEmpty() },
            color = if (o.isNull("color")) null else o.optLong("color"),
            searchable = o.optBoolean("searchable"),
            input = o.optBoolean("input"),
            plugged = if (o.isNull("plugged") || !o.has("plugged")) null else o.optBoolean("plugged"),
        )
    }

    private fun channels(array: JSONArray?): List<TvChannel> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        TvChannel(o.optString("id").ifEmpty { return@mapNotNull null }, o.optString("number"), o.optString("name"))
    }
}
