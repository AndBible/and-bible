package net.bible.service.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

interface ManifestFetcher {
    suspend fun status(url: String): Int?
    suspend fun body(url: String): String?
}

class JvmManifestFetcher : ManifestFetcher {
    override suspend fun status(url: String): Int? {
        val manifestUrl = try { URL(url) } catch (e: MalformedURLException) { return null }
        return withContext(Dispatchers.IO) {
            val conn = try { manifestUrl.openConnection() as HttpsURLConnection } catch (e: IOException) {
                return@withContext null
            }
            try { conn.responseCode } catch (e: IOException) { null }
        }
    }

    override suspend fun body(url: String): String? {
        val manifestUrl = try { URL(url) } catch (e: MalformedURLException) { return null }
        return withContext(Dispatchers.IO) {
            val conn = try { manifestUrl.openConnection() as HttpsURLConnection } catch (e: IOException) {
                return@withContext null
            }
            val responseCode = try { conn.responseCode } catch (e: IOException) { null }
            if (responseCode == 200) String(conn.inputStream.readBytes()) else null
        }
    }
}
