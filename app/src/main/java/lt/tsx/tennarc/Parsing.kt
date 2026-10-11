package lt.tsx.tennarc

import java.net.URI
import java.net.URISyntaxException
import org.json.JSONException
import org.json.JSONObject

fun uriOrNull(text: String): URI? = try {
    URI(text)
} catch (_: URISyntaxException) {
    null
}

fun hostOf(url: String): String? = uriOrNull(url)?.host

fun jsonOrNull(text: String): JSONObject? = try {
    JSONObject(text)
} catch (_: JSONException) {
    null
}
