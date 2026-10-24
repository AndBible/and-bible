package net.bible.sharedcore.llm.http

/** Per-call timeouts. null = the engine default. Values copied from the okhttp clients they replace. */
data class LlmTimeouts(val connectMs: Long?, val readMs: Long?, val writeMs: Long?) {
    companion object {
        val CHAT = LlmTimeouts(120_000, 90_000, 120_000)
        val CONNECTION_TEST = LlmTimeouts(10_000, 15_000, null)
        val MODELS = LlmTimeouts(30_000, 30_000, null)
    }
}

class LlmHttpResponse(val code: Int, val body: String, private val headers: Map<String, List<String>>) {
    val isSuccessful: Boolean get() = code in 200..299
    /** Case-insensitive, first value, like okhttp Response.header. */
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/** Non-2xx is a returned response. Network failures throw; cancellation cancels the request. */
interface LlmHttpTransport {
    suspend fun postJson(url: String, headers: Map<String, String>, jsonBody: String, timeouts: LlmTimeouts): LlmHttpResponse
    suspend fun get(url: String, headers: Map<String, String>, timeouts: LlmTimeouts): LlmHttpResponse
}
