package net.bible.service.cloudsync.googledrive

import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient
import net.bible.service.cloudsync.drive.fake.FakeDriveRequest
import net.bible.service.cloudsync.drive.fake.FakeDriveServer
import net.bible.service.cloudsync.drive.fake.FakeGoogleAuth
import net.bible.service.cloudsync.drive.fake.ktorEngine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * A google-http-client [HttpTransport] that answers every request from [FakeDriveServer.handle], so the Android
 * Google API client (and [LegacyDriveAdapter] on it) talks to the same in-memory Drive as the Ktor adapter.
 *
 * The request body is buffered whole. A `Content-Encoding: gzip` body (the client gzips JSON request bodies by
 * default) is decoded here, as Drive itself does, so the fake sees the plain body.
 */
class FakeDriveJavaTransport(private val server: FakeDriveServer) : HttpTransport() {
    override fun buildRequest(method: String, url: String): LowLevelHttpRequest = Request(method, url)

    private inner class Request(private val method: String, private val url: String) : LowLevelHttpRequest() {
        private val headers = LinkedHashMap<String, String>()

        override fun addHeader(name: String, value: String) {
            val key = name.lowercase()
            headers[key] = headers[key]?.let { "$it,$value" } ?: value
        }

        override fun execute(): LowLevelHttpResponse {
            var body = ByteArrayOutputStream().also { out -> streamingContent?.writeTo(out) }.toByteArray()
            val all = HashMap(headers)
            contentType?.let { all["content-type"] = it }
            if (contentEncoding.equals("gzip", ignoreCase = true)) {
                body = GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
            } else if (contentEncoding != null) {
                all["content-encoding"] = contentEncoding
            }
            if (streamingContent != null) all["content-length"] = body.size.toString()
            val response = server.handle(FakeDriveRequest(method, url, all, body))
            return Response(response.status, response.headers, response.body)
        }
    }

    private class Response(
        private val status: Int,
        headers: Map<String, String>,
        private val body: ByteArray,
    ) : LowLevelHttpResponse() {
        private val headerList = headers.entries.map { it.key to it.value }
        private fun header(name: String) = headerList.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

        override fun getContent(): InputStream = ByteArrayInputStream(body)
        override fun getContentEncoding(): String? = header("Content-Encoding")
        override fun getContentLength(): Long = body.size.toLong()
        override fun getContentType(): String? = header("Content-Type")
        override fun getStatusLine(): String = "HTTP/1.1 $status $reasonPhrase"
        override fun getStatusCode(): Int = status
        override fun getReasonPhrase(): String = REASONS[status] ?: "Status $status"
        override fun getHeaderCount(): Int = headerList.size
        override fun getHeaderName(index: Int): String = headerList[index].first
        override fun getHeaderValue(index: Int): String = headerList[index].second
    }

    private companion object {
        val REASONS = mapOf(
            200 to "OK", 204 to "No Content", 206 to "Partial Content", 308 to "Resume Incomplete",
            400 to "Bad Request", 401 to "Unauthorized", 404 to "Not Found", 416 to "Requested Range Not Satisfiable",
        )
    }
}

/**
 * The frozen old-app adapter ([LegacyDriveAdapter]) on [server]. The request initializer stands in for the old
 * app's `GoogleAccountCredential`: it only adds the bearer token the fake requires.
 */
fun oldDriveAdapter(server: FakeDriveServer): CloudAdapter {
    val bearer = HttpRequestInitializer { it.headers.authorization = "Bearer java-client-token" }
    val service = Drive.Builder(FakeDriveJavaTransport(server), GsonFactory.getDefaultInstance(), bearer)
        .setApplicationName("test").build()
    return LegacyDriveAdapter(service)
}

/** The shared Ktor [GoogleDriveRestAdapter] on [server]. */
fun newDriveAdapter(server: FakeDriveServer, auth: GoogleAuth = FakeGoogleAuth()): CloudAdapter =
    GoogleDriveRestAdapter(auth, createGoogleDriveHttpClient(server.ktorEngine()))
