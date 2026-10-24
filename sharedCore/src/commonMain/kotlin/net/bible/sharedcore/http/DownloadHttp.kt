package net.bible.sharedcore.http

import kotlin.coroutines.cancellation.CancellationException

/**
 * Response metadata with URLConnection semantics: [contentLength] is -1 when absent,
 * [lastModified] is epoch millis or 0 when absent/unparsable, [reason] only for non-200.
 */
data class HttpHeaders(val status: Int, val contentLength: Long, val lastModified: Long, val reason: String? = null)

/** An open response; [read] streams the body. Valid only inside [DownloadHttp.open]'s block. */
interface DownloadResponse {
    val headers: HttpHeaders
    /** Reads up to buf.size bytes into buf; returns -1 at end of body. */
    suspend fun read(buf: ByteArray): Int
}

/** A small text body; [body] is non-null only for status 200 (ManifestFetcher semantics). */
data class HttpTextResult(val status: Int, val body: String?)

/**
 * Host-owned download HTTP (spec D3). Only https URLs. Never throws on an HTTP status; callers
 * read [HttpHeaders.status]. Transport failures throw [DownloadTransportException] (timeouts:
 * [DownloadTimeoutException]); coroutine cancellation propagates unchanged.
 */
interface DownloadHttp {
    /** HEAD request (JSword `HttpTransport.head` parity). */
    suspend fun head(url: String): HttpHeaders
    /** GET, read the headers, discard the body (URLConnection.lastModified/contentLength parity). */
    suspend fun headers(url: String): HttpHeaders
    /** GET and stream the body inside [block]; the connection is released when [block] returns. */
    suspend fun <T> open(url: String, block: suspend (DownloadResponse) -> T): T
    /** GET a small text body (UTF-8). */
    suspend fun text(url: String): HttpTextResult
}

open class DownloadTransportException(message: String?, cause: Throwable? = null) : okio.IOException(message, cause)
class DownloadTimeoutException(cause: Throwable?) : DownloadTransportException("Download timed out", cause)
class InsecureUrlException(val url: String) : DownloadTransportException("Only https is allowed: $url")
/** Thrown by adapters that must turn an error status into a failure (JSword prepareRead parity). */
class HttpStatusException(val status: Int, val reason: String?) : okio.IOException("HTTP $status ${reason.orEmpty()}")

/** Rejects anything but https, before any network access. */
fun requireHttps(url: String) {
    if (!url.startsWith("https://", ignoreCase = true)) throw InsecureUrlException(url)
}

internal fun Throwable.isCancellation() = this is CancellationException
