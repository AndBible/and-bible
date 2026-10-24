package net.bible.sharedcore.webdav

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.random.Random

/**
 * One usable `WWW-Authenticate: Digest` challenge (RFC 7616, or RFC 2069 when the server offers no
 * qop). AndBible answers Digest itself because ktor's Digest provider hashes through ktor-utils'
 * `Digest()`, which throws "[Digest] is not supported on Darwin" on iOS; okio hashes on every platform.
 */
internal class DigestChallenge private constructor(
    private val realm: String,
    private val nonce: String,
    private val opaque: String?,
    /** As the server spelled it, echoed back; null = the RFC 2069 default, MD5. */
    private val algorithm: String?,
    private val qopAuth: Boolean,
    val stale: Boolean,
) {
    private val sha256 = algorithm?.startsWith("SHA-256", ignoreCase = true) == true
    private val session = algorithm?.endsWith("-sess", ignoreCase = true) == true

    private fun hash(text: String): String = text.encodeUtf8().let { if (sha256) it.sha256() else it.md5() }.hex()

    /** The `Authorization` header value for [method] on [uri] (the request-target: encoded path and query). */
    fun authorization(username: String, password: String, method: String, uri: String, nc: Int, cnonce: String): String {
        val ha1 = hash("$username:$realm:$password").let { if (session) hash("$it:$nonce:$cnonce") else it }
        val ha2 = hash("$method:$uri")
        val count = nc.toString(16).padStart(8, '0')
        val response = if (qopAuth) hash("$ha1:$nonce:$count:$cnonce:auth:$ha2") else hash("$ha1:$nonce:$ha2")
        return buildString {
            append("Digest username=").append(quote(username))
            append(", realm=").append(quote(realm))
            append(", nonce=").append(quote(nonce))
            append(", uri=").append(quote(uri))
            algorithm?.let { append(", algorithm=").append(it) }
            if (qopAuth) append(", qop=auth, nc=").append(count).append(", cnonce=").append(quote(cnonce))
            append(", response=").append(quote(response))
            opaque?.let { append(", opaque=").append(quote(it)) }
        }
    }

    companion object {
        private val ALGORITHMS = setOf("MD5", "MD5-SESS", "SHA-256", "SHA-256-SESS")

        /** The first Digest challenge among the `WWW-Authenticate` [headers] that AndBible can answer, if any. */
        fun parse(headers: List<String>): DigestChallenge? =
            headers.flatMap(::challenges).firstNotNullOfOrNull { (scheme, params) ->
                if (!scheme.equals("Digest", ignoreCase = true)) return@firstNotNullOfOrNull null
                val nonce = params["nonce"] ?: return@firstNotNullOfOrNull null
                val algorithm = params["algorithm"]
                if (algorithm != null && algorithm.uppercase() !in ALGORITHMS) return@firstNotNullOfOrNull null
                val qop = params["qop"]?.split(',')?.map { it.trim() }
                // auth-int would need the body hashed; a -sess hash needs the cnonce only qop sends.
                if (qop != null && "auth" !in qop) return@firstNotNullOfOrNull null
                if (qop == null && algorithm?.endsWith("-sess", ignoreCase = true) == true) return@firstNotNullOfOrNull null
                DigestChallenge(params["realm"] ?: "", nonce, params["opaque"], algorithm, qop != null,
                    params["stale"].equals("true", ignoreCase = true))
            }

        fun newCnonce(): String = Random.nextBytes(16).toByteString().hex()

        private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

        /**
         * Splits one header value into (scheme, lower-cased params) challenges. A token not followed by
         * `=` starts a new challenge, so `Basic realm="x", Digest nonce="y"` yields two.
         */
        private fun challenges(header: String): List<Pair<String, Map<String, String>>> {
            val result = mutableListOf<Pair<String, MutableMap<String, String>>>()
            var i = 0
            fun skip(chars: String) { while (i < header.length && header[i] in chars) i++ }
            fun token(): String {
                val start = i
                while (i < header.length && header[i] !in " \t,=") i++
                return header.substring(start, i)
            }
            fun value(): String {
                if (i >= header.length || header[i] != '"') {
                    val start = i
                    while (i < header.length && header[i] !in " \t,") i++
                    return header.substring(start, i)
                }
                i++
                return buildString {
                    while (i < header.length && header[i] != '"') {
                        if (header[i] == '\\' && i + 1 < header.length) i++
                        append(header[i++])
                    }
                    i++
                }
            }
            while (true) {
                skip(" \t,")
                if (i >= header.length) break
                val name = token()
                skip(" \t")
                if (i < header.length && header[i] == '=') {
                    i++
                    skip(" \t")
                    val v = value()
                    result.lastOrNull()?.second?.put(name.lowercase(), v)
                } else if (name.isNotEmpty()) {
                    result += name to mutableMapOf()
                } else i++
            }
            return result
        }
    }
}
