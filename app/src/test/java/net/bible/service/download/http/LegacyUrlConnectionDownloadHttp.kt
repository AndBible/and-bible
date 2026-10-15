package net.bible.service.download.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.sharedcore.http.DownloadHttp
import net.bible.sharedcore.http.DownloadResponse
import net.bible.sharedcore.http.DownloadTransportException
import net.bible.sharedcore.http.HttpHeaders
import net.bible.sharedcore.http.HttpTextResult
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Test-only oracle: today's :app URLConnection code (no timeouts) behind the D3 port. */
class LegacyUrlConnectionDownloadHttp : DownloadHttp {
    private fun connect(url: String) = URL(url).openConnection() as HttpsURLConnection

    override suspend fun head(url: String): HttpHeaders = throw UnsupportedOperationException("not used by :app today")

    override suspend fun headers(url: String): HttpHeaders = withContext(Dispatchers.IO) {
        try {
            val c = connect(url)
            try {
                HttpHeaders(c.responseCode, c.contentLengthLong, c.lastModified)
            } finally { c.disconnect() }
        } catch (e: IOException) { throw DownloadTransportException(e.message, e) }
    }

    override suspend fun <T> open(url: String, block: suspend (DownloadResponse) -> T): T = throw UnsupportedOperationException("not used by :app today")

    override suspend fun text(url: String): HttpTextResult = withContext(Dispatchers.IO) {
        try {
            val c = connect(url)
            try {
                val code = c.responseCode
                HttpTextResult(code, if (code == 200) c.inputStream.use { String(it.readBytes()) } else null)
            } finally { c.disconnect() }
        } catch (e: IOException) { throw DownloadTransportException(e.message, e) }
    }
}
