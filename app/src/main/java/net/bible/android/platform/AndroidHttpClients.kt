package net.bible.android.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.bible.sharedcore.http.downloadClientDefaults
import net.bible.sharedcore.llm.http.LlmTimeouts
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Android owns the HTTP engines (spec D3-6): one download client and one client per LLM timeout
 * profile, derived from one OkHttp base but with isolated pools/dispatchers: ktor closes them per engine.
 * Redirects are left to ktor, which refuses https-to-http; the User-Agent is the platform's own (D3-3).
 */
class AndroidHttpClients(
    base: OkHttpClient = OkHttpClient(),
    private val userAgent: String? = System.getProperty("http.agent"),
) {
    private val base = base.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val clients = mutableMapOf<LlmTimeouts, HttpClient>()

    private fun engineBase(): OkHttpClient = base.newBuilder()
        .dispatcher(Dispatcher())
        .connectionPool(ConnectionPool())
        .build()

    val downloadClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine { preconfigured = engineBase() }
            downloadClientDefaults(userAgent)
        }
    }

    @Synchronized
    fun clientFor(t: LlmTimeouts): HttpClient = clients.getOrPut(t) {
        HttpClient(OkHttp) {
            engine {
                preconfigured = engineBase()
                config {
                    t.connectMs?.let { connectTimeout(it, MILLISECONDS) }
                    t.readMs?.let { readTimeout(it, MILLISECONDS) }
                    t.writeMs?.let { writeTimeout(it, MILLISECONDS) }
                }
            }
        }
    }
}
