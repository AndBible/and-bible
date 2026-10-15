package net.bible.sharedcore.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders as KtorHeaders
import io.ktor.http.fromHttpToGmtDate
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Download-client configuration shared by every platform; the host supplies the engine and its UA (spec D3-3). */
fun HttpClientConfig<*>.downloadClientDefaults(userAgent: String?) {
    install(HttpTimeout)
    followRedirects = true
    expectSuccess = false
    if (userAgent != null) install(UserAgent) { agent = userAgent }
}

/** ktor implementation of [DownloadHttp] (spec D3 §4.1). The client must be built with [downloadClientDefaults]. */
class KtorDownloadHttp(private val client: HttpClient, private val timeoutMs: () -> Int) : DownloadHttp {
    private fun HttpRequestBuilder.idleTimeouts() {
        val t = timeoutMs().toLong()
        timeout { connectTimeoutMillis = t; socketTimeoutMillis = t }
    }

    override suspend fun head(url: String): HttpHeaders {
        requireHttps(url)
        return transport { client.prepareHead(url) { idleTimeouts() }.execute { it.checkedHeaders() } }
    }

    override suspend fun headers(url: String): HttpHeaders = open(url) { it.headers }

    override suspend fun text(url: String): HttpTextResult {
        requireHttps(url)
        return transport {
            client.prepareGet(url) { idleTimeouts() }.execute { r ->
                val h = r.checkedHeaders()
                HttpTextResult(h.status, if (h.status == 200) r.bodyAsText() else null)
            }
        }
    }

    override suspend fun <T> open(url: String, block: suspend (DownloadResponse) -> T): T {
        requireHttps(url)
        var callerFailure: Throwable? = null
        val result = try {
            transport {
                client.prepareGet(url) { idleTimeouts() }.execute { r ->
                    val h = r.checkedHeaders()
                    val channel = r.bodyAsChannel()
                    val response = object : DownloadResponse {
                        override val headers = h
                        override suspend fun read(buf: ByteArray): Int = transport {
                            val count = channel.readAvailable(buf, 0, buf.size)
                            if (count == 0 && channel.isClosedForRead) -1 else count
                        }
                    }
                    try {
                        block(response)
                    } catch (e: Throwable) {
                        if (e.isCancellation()) {
                            callerFailure = e
                            throw e
                        }
                        callerFailure = e
                        null
                    }
                }
            }
        } catch (e: Throwable) {
            val callerCancellation = callerFailure
            if (callerCancellation != null) {
                currentCoroutineContext().ensureActive()
                throw callerCancellation
            }
            throw e
        }
        callerFailure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /** Maps engine failures; our own exceptions and cancellation pass through. */
    private suspend fun <T> transport(block: suspend () -> T): T = try {
        block()
    } catch (e: Throwable) {
        throw when {
            e.isCancellation() -> e
            e is DownloadTransportException || e is HttpStatusException -> e
            // Streaming engines can wrap socket timeouts in a closed-channel exception.
            generateSequence(e) { it.cause }.any {
                it is SocketTimeoutException || it is ConnectTimeoutException || it is HttpRequestTimeoutException
            } -> DownloadTimeoutException(e)
            e is Exception -> DownloadTransportException(e.message, e)
            else -> e
        }
    }

    private fun HttpResponse.checkedHeaders(): HttpHeaders {
        val status = status.value
        if (status in 300..399) {
            val location = headers[KtorHeaders.Location]
            if (location != null && location.startsWith("http:", ignoreCase = true)) throw InsecureUrlException(location)
        }
        val length = headers[KtorHeaders.ContentLength]?.toLongOrNull() ?: -1L
        val modified = headers[KtorHeaders.LastModified]?.let { runCatching { it.fromHttpToGmtDate().timestamp }.getOrNull() } ?: 0L
        return HttpHeaders(status, length, modified, if (status == 200) null else this.status.description)
    }
}
