package net.bible.service.download.http

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.platform.AndroidHttpClients
import net.bible.android.platform.SuspendHttpTransport
import net.bible.sharedcore.http.KtorDownloadHttp
import okhttp3.OkHttpClient
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.WebResource
import org.junit.After

class KtorHttpTransportContractTest : HttpTransportContract() {
    override val legacy = false
    private val clients = mutableListOf<HttpClient>()
    private val bases = mutableListOf<OkHttpClient>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun transport(server: TestHttpsServer): HttpTransport {
        val base = OkHttpClient.Builder()
            .sslSocketFactory(server.sslContext.socketFactory, server.trustManager)
            .hostnameVerifier { host, _ -> host == "127.0.0.1" }
            .build().also { bases += it }
        val client = AndroidHttpClients(base).downloadClient.also { clients += it }
        return SuspendHttpTransport(KtorDownloadHttp(client) { WebResource.getTimeout() }, scope)
    }

    @After fun closeResources() {
        scope.cancel()
        clients.forEach { it.close() }
        try {
            runBlocking {
                withTimeout(5_000) {
                    scope.coroutineContext[Job]!!.join()
                    clients.forEach { it.engine.coroutineContext[Job]!!.join() }
                }
            }
        } finally {
            bases.forEach {
                it.dispatcher.executorService.shutdownNow()
                it.connectionPool.evictAll()
            }
        }
    }
}
