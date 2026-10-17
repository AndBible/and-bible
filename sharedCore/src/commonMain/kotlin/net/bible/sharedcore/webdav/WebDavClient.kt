@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package net.bible.sharedcore.webdav

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64

/**
 * Builds the HttpClient a [WebDavClient] needs. Authentication (Basic and Digest) is answered by
 * [WebDavClient] itself, not ktor's Auth plugin, whose Digest hashing throws on Darwin. The
 * credentials are therefore unused here; the parameters stay for the existing callers.
 */
@Suppress("UNUSED_PARAMETER")
fun createWebDavHttpClient(engine: HttpClientEngine, username: String, password: String): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

/**
 * RFC 4918 client for AndBible sync. Knows nothing about sync semantics; every path is relative to
 * [baseUrl] (see [DavPath]). Times in [DavResource] are server clock; [serverClockOffsetMs] is
 * `server − device` from the most recent `Date` header.
 *
 * Error contract: transport failures and 408/429/5xx → [DavTransientException] (an IOException);
 * everything else → a non-IO [DavException]. A [DavUntrustedCertificateException] anywhere in a
 * failure's cause chain is rethrown as is, because the JVM wraps TrustManager failures in
 * SSLHandshakeException, which is an IOException and would otherwise be retried silently forever.
 * [mapPlatformError] lets the platform translate failures common code cannot see (hostname
 * verification).
 */
class WebDavClient(
    private val http: HttpClient,
    baseUrl: String,
    private val username: String,
    private val password: String,
    private val nowMs: () -> Long,
    private val mapPlatformError: (Throwable) -> DavException? = { null },
) {
    val davPath = DavPath(baseUrl)
    private val basicHeader = "Basic " + Base64.encode("$username:$password".encodeToByteArray())

    @Volatile private var preemptiveBasic = false
    @Volatile var serverClockOffsetMs: Long = 0L
        private set

    suspend fun propfind(path: String, depth: Int): List<DavResource> = call(path) {
        val resp = send(path, PROPFIND, collectionUrl = true) {
            header("Depth", depth.toString())
            setBody(TextContent(PROPFIND_BODY, ContentType.Application.Xml))
        }
        val body = resp.bodyAsText()
        if (!body.contains("multistatus", ignoreCase = true))
            throw DavProtocolException(resp.status.value, "Not a multistatus response for $path")
        try { MultistatusParser.parse(body, davPath) }
        catch (e: IllegalArgumentException) { throw DavProtocolException(resp.status.value, "Bad multistatus for $path", e) }
    }

    suspend fun get(path: String, onChunk: suspend (ByteArray, Int) -> Unit) = call(path) {
        http.prepareRequest { build(path, HttpMethod.Get, collectionUrl = false) }.execute { resp ->
            val answer = challengeAnswer(resp, HttpMethod.Get, path, collectionUrl = false)
            if (answer != null) {
                http.prepareRequest { build(path, HttpMethod.Get, collectionUrl = false, answer) }.execute { r2 -> stream(path, r2, onChunk) }
            } else stream(path, resp, onChunk)
        }
    }

    /** [writeBody] may be invoked more than once (auth retries); it must write the full body each time. */
    suspend fun put(path: String, contentLength: Long, contentType: String, writeBody: suspend (ByteWriteChannel) -> Unit) = call(path) {
        send(path, HttpMethod.Put, collectionUrl = false) {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentLength: Long = contentLength
                override val contentType: ContentType = ContentType.parse(contentType)
                override suspend fun writeTo(channel: ByteWriteChannel) = writeBody(channel)
            })
        }
        Unit
    }

    /** Creates one collection; an existing one (405) is fine. A missing parent throws (409). */
    suspend fun mkcol(path: String) = call(path) { send(path, MKCOL, collectionUrl = true, okStatuses = setOf(405)); Unit }

    /** Creates every missing collection along [path]. */
    suspend fun mkcols(path: String) {
        val segments = path.split('/').filter { it.isNotEmpty() }
        for (i in segments.indices) mkcol(segments.subList(0, i + 1).joinToString("/"))
    }

    suspend fun delete(path: String) = call(path) { send(path, HttpMethod.Delete, collectionUrl = false, okStatuses = setOf(404)); Unit }

    // ---- internals ----

    private suspend fun send(
        path: String, method: HttpMethod, collectionUrl: Boolean,
        okStatuses: Set<Int> = emptySet(), configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        var resp = http.request { build(path, method, collectionUrl); configure() }
        challengeAnswer(resp, method, path, collectionUrl)?.let { answer ->
            resp = http.request { build(path, method, collectionUrl, answer); configure() }
        }
        noteDate(resp)
        val status = resp.status.value
        if (status !in okStatuses) davStatusException(status, path)?.let { throw it }
        return resp
    }

    private fun requestUrl(path: String, collectionUrl: Boolean): String =
        davPath.url(path).let { if (collectionUrl && !it.endsWith("/")) "$it/" else it }

    /** [authorization] answers a challenge for this one request; otherwise Basic goes pre-emptively once known. */
    private fun HttpRequestBuilder.build(path: String, method: HttpMethod, collectionUrl: Boolean, authorization: String? = null) {
        this.method = method
        url(requestUrl(path, collectionUrl))
        (authorization ?: basicHeader.takeIf { preemptiveBasic })?.let { header(HttpHeaders.Authorization, it) }
    }

    /**
     * The `Authorization` value for one retry of a 401, or null when there is nothing to answer.
     * Digest wins over Basic when both are offered and is answered per request (like ktor's provider
     * did); a Basic challenge switches to pre-emptive Basic so later requests skip the 401.
     */
    private fun challengeAnswer(resp: HttpResponse, method: HttpMethod, path: String, collectionUrl: Boolean): String? {
        if (resp.status.value != 401) return null
        val challenges = resp.headers.getAll(HttpHeaders.WWWAuthenticate).orEmpty()
        DigestChallenge.parse(challenges)?.let { digest ->
            val uri = Url(requestUrl(path, collectionUrl)).encodedPathAndQuery
            return digest.authorization(username, password, method.value, uri, 1, DigestChallenge.newCnonce())
        }
        if (preemptiveBasic || challenges.none { it.trimStart().startsWith("Basic", ignoreCase = true) }) return null
        preemptiveBasic = true
        return basicHeader
    }

    private suspend fun stream(path: String, resp: HttpResponse, onChunk: suspend (ByteArray, Int) -> Unit) {
        noteDate(resp)
        davStatusException(resp.status.value, path)?.let { throw it }
        val ch = resp.bodyAsChannel()
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = ch.readAvailable(buf, 0, buf.size)
            if (n == -1) break
            if (n > 0) onChunk(buf, n)
        }
    }

    private fun noteDate(resp: HttpResponse) {
        resp.headers[HttpHeaders.Date]?.let(HttpDates::parseRfc1123)?.let { serverClockOffsetMs = it - nowMs() }
    }

    private suspend fun <T> call(path: String, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: DavException) {
        throw e
    } catch (e: DavTransientException) {
        throw e
    } catch (e: Throwable) {
        generateSequence(e) { it.cause }.filterIsInstance<DavUntrustedCertificateException>().firstOrNull()?.let { throw it }
        mapPlatformError(e)?.let { throw it }
        if (e is kotlinx.io.IOException) throw DavTransientException("${e.message} ($path)", e)
        throw e
    }

    private companion object {
        val PROPFIND = HttpMethod("PROPFIND")
        val MKCOL = HttpMethod("MKCOL")
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:creationdate/><d:getetag/></d:prop></d:propfind>"""
    }
}
