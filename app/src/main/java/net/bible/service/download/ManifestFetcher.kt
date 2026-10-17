package net.bible.service.download

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

    private fun isUrl(url: String) = try { parseKnownUrl(url); true } catch (e: IllegalArgumentException) { false }
}

private val KNOWN_URL_SCHEMES = setOf("http", "https", "file", "jar", "ftp")

/**
 * Stand-in for the `java.net.URL(x)` validation the download code relied on: an absolute URL with a protocol
 * that `URL` knows. Anything else throws [IllegalArgumentException] (was `MalformedURLException`).
 * `KUri` is stricter than `URL` about the rest of the syntax (a space or `https://` with no authority is rejected).
 */
internal fun parseKnownUrl(url: String): KUri {
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
internal fun urlHost(url: String, uri: KUri): String {
    uri.getHost()?.let { return it }
    val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
    return authority.substringAfterLast('@').substringBefore(':')
}

