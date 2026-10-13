package net.bible.android.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.bible.sharedcore.llm.http.LlmTimeouts
import java.util.concurrent.TimeUnit.MILLISECONDS

/** Android owns the engines; each timeout profile reuses its client and connection pool. */
class AndroidLlmHttpClients {
    private val clients = mutableMapOf<LlmTimeouts, HttpClient>()

    @Synchronized
    fun clientFor(t: LlmTimeouts): HttpClient = clients.getOrPut(t) {
        HttpClient(OkHttp) {
            engine {
                config {
                    t.connectMs?.let { connectTimeout(it, MILLISECONDS) }
                    t.readMs?.let { readTimeout(it, MILLISECONDS) }
                    t.writeMs?.let { writeTimeout(it, MILLISECONDS) }
                }
            }
        }
    }
}
