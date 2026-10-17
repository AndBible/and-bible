package net.bible.service.download

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.http.DownloadHttp
import okio.IOException
import org.crosswire.common.util.KUri

interface ManifestFetcher {
    suspend fun status(url: String): Int?
    suspend fun body(url: String): String?
}

/** Manifest probes return null for malformed URLs and transport failures, but preserve cancellation. */
class KtorManifestFetcher(private val http: DownloadHttp) : ManifestFetcher {
    override suspend fun status(url: String): Int? {
        if (!isUrl(url)) return null
        return try { http.headers(url).status } catch (e: IOException) { null }
    }

    override suspend fun body(url: String): String? {
        if (!isUrl(url)) return null
        return try { http.text(url).body } catch (e: IOException) { null }
    }

    private fun isUrl(url: String) = try { parseLenientUrl(url); true } catch (e: IllegalArgumentException) { false }
}

private val KNOWN_URL_SCHEMES = setOf("http", "https", "file", "jar", "ftp")

/**
 * Stand-in for the `java.net.URL(x)` validation the download code relied on: an absolute URL with a protocol
 * that `URL` knows. Anything else throws [IllegalArgumentException] (was `MalformedURLException`).
 * `KUri` is stricter than `URL` about the rest of the syntax (a space or `https://` with no authority is rejected).
 */
@VisibleForTesting fun parseKnownUrl(url: String): KUri {
    val uri = try { KUri(url) } catch (e: Exception) { throw IllegalArgumentException("Malformed URL: $url", e) }
    if (!uri.isAbsolute() || uri.getScheme()?.lowercase() !in KNOWN_URL_SCHEMES) {
        throw IllegalArgumentException("Malformed URL: $url")
    }
    return uri
}

/**
 * `java.net.URL.getHost()` equivalent: `KUri.getHost()` is null for a server name that is not a valid host name
 * (for example one with an underscore), where `URL` still returned it (and "" when there is none).
 */
@VisibleForTesting fun urlHost(url: String, uri: KUri): String {
    uri.getHost()?.let { return it }
    val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.substringAfterLast('@').substringBefore(':')
}


/** A text-field URL: [text] is what `java.net.URL` would have used (trimmed), [uri] only validates it. */
@VisibleForTesting class LenientUrl(val text: String, val uri: KUri)

/**
 * The custom-repository URL check, as lenient as `java.net.URL(x)` was: leading and trailing characters up to
 * U+0020 are dropped and path, query and fragment characters that `URI` rejects (space, `| { } ^ \ ` " < >`,
 * a stray `%`, `[ ]`) are accepted. Scheme and absoluteness are checked as in [parseKnownUrl], on a copy with
 * those characters percent-encoded. Host and path must be taken from [LenientUrl.text], never from the copy.
 */
@VisibleForTesting fun parseLenientUrl(url: String): LenientUrl {
    val text = url.trim { it <= ' ' }
    val authorityEnd = text.indexOf("://").let { i ->
        if (i < 0) 0 else text.indexOfAny(charArrayOf('/', '?', '#'), i + 3).let { if (it < 0) text.length else it }
    }
    val encoded = StringBuilder(text.length + 8)
    text.forEachIndexed { i, c ->
        val bad = c <= ' ' || c in "\"<>\\^`{|}" || (c == '[' || c == ']') && i >= authorityEnd ||
            c == '%' && !(i + 2 < text.length && text[i + 1].isHexDigit() && text[i + 2].isHexDigit())
        if (bad) encoded.append('%').append(c.code.toString(16).uppercase().padStart(2, '0')) else encoded.append(c)
    }
    return LenientUrl(text, parseKnownUrl(encoded.toString()))
}

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

/** `java.net.URL.getPath()` equivalent on the (trimmed) text: from the end of the authority up to `?` or `#`. */
@VisibleForTesting fun urlPath(text: String): String {
    val afterAuthority = text.substringAfter("://", "")
    val fromPath = afterAuthority.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) "" else afterAuthority.substring(it) }
    return fromPath.substringBefore('?').substringBefore('#')
}
