package net.bible.service.download.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.net.InetSocketAddress
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Loopback HTTPS server for D3 contract tests; trust is opt-in per test, never global for long. */
class TestHttpsServer : AutoCloseable {
    data class Recorded(val method: String, val path: String, val headers: Map<String, List<String>>)

    private val password = "d3-test".toCharArray()
    private val keyStore = KeyStore.getInstance("PKCS12").apply {
        TestHttpsServer::class.java.getResourceAsStream("/d3/test-https.p12")!!.use { load(it, password) }
    }
    val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, password) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        init(kmf.keyManagers, tmf.trustManagers, null)
    }
    val trustManager: X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(keyStore) }.trustManagers.filterIsInstance<X509TrustManager>().single()

    private val executor = Executors.newCachedThreadPool()
    private val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        httpsConfigurator = HttpsConfigurator(sslContext)
        executor = this@TestHttpsServer.executor
        start()
    }
    val base: String = "https://127.0.0.1:${server.address.port}"
    val requests = CopyOnWriteArrayList<Recorded>()

    fun route(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            requests += Recorded(
                exchange.requestMethod, exchange.requestURI.path,
                exchange.requestHeaders.entries.associate { (k, v) -> k.lowercase(Locale.ROOT) to v.toList() },
            )
            exchange.use { handler(it) }
        }
    }

    /** Installs this server's trust into HttpsURLConnection's process-wide defaults for [block] only. */
    inline fun <T> withLegacyTrust(block: () -> T): T {
        val oldFactory: SSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
        val oldVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { host, _ -> host == "127.0.0.1" }
            return block()
        } finally {
            HttpsURLConnection.setDefaultSSLSocketFactory(oldFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(oldVerifier)
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }
}

/** Writes [bytes] with a Content-Length (or chunked when [chunked]). */
fun HttpExchange.reply(status: Int, bytes: ByteArray, headers: Map<String, String> = emptyMap(), chunked: Boolean = false) {
    headers.forEach { (k, v) -> responseHeaders.add(k, v) }
    sendResponseHeaders(status, if (chunked) 0 else if (bytes.isEmpty()) -1 else bytes.size.toLong())
    if (bytes.isNotEmpty()) responseBody.write(bytes)
}
