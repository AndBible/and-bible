package net.bible.service.cloudsync.drive.fake

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/** A Ktor [MockEngine] that routes every request to [FakeDriveServer.handle]. */
fun FakeDriveServer.ktorEngine(): HttpClientEngine = MockEngine { request ->
    val headers = HashMap<String, String>()
    request.headers.forEach { name, values -> headers[name.lowercase()] = values.joinToString(",") }
    request.body.contentType?.let { headers.putIfAbsent("content-type", it.toString()) }
    request.body.contentLength?.let { headers.putIfAbsent("content-length", it.toString()) }
    val response = handle(FakeDriveRequest(request.method.value, request.url.toString(), headers, request.body.toByteArray()))
    // Content-Length is derived from the body by the engine; a copy would conflict with 206/308 handling.
    val out = response.headers.filterKeys { !it.equals(HttpHeaders.ContentLength, ignoreCase = true) }
    respond(
        content = response.body,
        status = HttpStatusCode.fromValue(response.status),
        headers = headersOf(*out.map { it.key to listOf(it.value) }.toTypedArray()),
    )
}
