package net.bible.service.download

import net.bible.sharedcore.http.DownloadHttp
import okio.IOException
import java.net.MalformedURLException
import java.net.URL

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

    private fun isUrl(url: String) = try { URL(url); true } catch (e: MalformedURLException) { false }
}
