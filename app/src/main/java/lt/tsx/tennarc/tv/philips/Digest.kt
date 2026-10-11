package lt.tsx.tennarc.tv.philips

import java.security.MessageDigest
import java.security.SecureRandom

internal data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val qop: String?,
    val opaque: String?,
    val algorithm: String?,
) {
    companion object {
        fun parse(header: String): DigestChallenge? {
            if (!header.trimStart().startsWith("Digest", ignoreCase = true)) return null
            val params = PARAM.findAll(header.trimStart().substring("Digest".length))
                .associate { match -> match.groupValues[1].lowercase() to (match.groups[2]?.value ?: match.groupValues[3]) }
            return DigestChallenge(
                realm = params["realm"] ?: return null,
                nonce = params["nonce"] ?: return null,
                qop = params["qop"],
                opaque = params["opaque"],
                algorithm = params["algorithm"],
            )
        }

        private val PARAM = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|([^\s,]+))""")
    }
}

internal class DigestAuth(private val user: String, private val password: String, private val cnonce: () -> String = ::newCnonce) {
    private var challenge: DigestChallenge? = null
    private var count = 0

    @Synchronized
    fun answer(challenge: DigestChallenge) {
        this.challenge = challenge
        count = 0
    }

    @Synchronized
    fun header(method: String, uri: String): String? {
        val challenge = challenge ?: return null
        count++
        return digestHeader(user, password, challenge, method, uri, count, cnonce())
    }
}

internal fun digestHeader(
    user: String,
    password: String,
    challenge: DigestChallenge,
    method: String,
    uri: String,
    count: Int,
    cnonce: String,
): String {
    val sess = challenge.algorithm.equals("MD5-sess", ignoreCase = true)
    val qop = challenge.qop?.split(',')?.map { it.trim() }?.firstOrNull { it.equals("auth", ignoreCase = true) }
    val nc = "%08x".format(count)
    val secret = md5("$user:${challenge.realm}:$password").let { if (sess) md5("$it:${challenge.nonce}:$cnonce") else it }
    val request = md5("$method:$uri")
    val response = if (qop != null) {
        md5("$secret:${challenge.nonce}:$nc:$cnonce:$qop:$request")
    } else {
        md5("$secret:${challenge.nonce}:$request")
    }
    return buildString {
        append("Digest username=\"$user\", realm=\"${challenge.realm}\", nonce=\"${challenge.nonce}\", uri=\"$uri\"")
        append(", response=\"$response\"")
        challenge.algorithm?.let { append(", algorithm=$it") }
        challenge.opaque?.let { append(", opaque=\"$it\"") }
        if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
    }
}

private fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private val random = SecureRandom()

private fun newCnonce(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
