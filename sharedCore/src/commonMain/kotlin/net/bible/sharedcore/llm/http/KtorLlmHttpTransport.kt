package net.bible.sharedcore.llm.http

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent

/** One HttpClient per timeout profile; the platform owns the engines and their timeouts. */
class KtorLlmHttpTransport(private val clientFor: (LlmTimeouts) -> HttpClient) : LlmHttpTransport {
    override suspend fun postJson(url: String, headers: Map<String, String>, jsonBody: String, timeouts: LlmTimeouts): LlmHttpResponse =
        clientFor(timeouts).post(url) {
            // Ktor considers Content-Type unsafe as a request header; TextContent supplies it.
            headers.filterKeys { !it.equals("Content-Type", ignoreCase = true) }.forEach { (k, v) -> header(k, v) }
            setBody(TextContent(jsonBody, ContentType.Application.Json))
        }.toLlmResponse()

    override suspend fun get(url: String, headers: Map<String, String>, timeouts: LlmTimeouts): LlmHttpResponse =
        clientFor(timeouts).get(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.toLlmResponse()

    private suspend fun HttpResponse.toLlmResponse() =
        LlmHttpResponse(status.value, bodyAsText(), headers.entries().associate { it.key to it.value })
}
