package net.bible.service.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import net.bible.android.control.versification.BookInstallWatcher
import net.bible.android.database.CustomRepository
import net.bible.android.platform.AndroidHttpClients
import net.bible.android.platform.SuspendHttpTransport
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.http.TestHttpsServer
import net.bible.service.download.http.reply
import net.bible.sharedcore.http.KtorDownloadHttp
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import okhttp3.OkHttpClient
import org.crosswire.common.util.HttpTransport
import org.crosswire.common.util.WebResource
import org.crosswire.jsword.book.BookFilter
import org.koin.java.KoinJavaComponent.get
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sends requests for [fakeHost] to 127.0.0.1:[port]; everything else is passed through unchanged. */
private class LoopbackRewrite(private val delegate: HttpTransport, private val fakeHost: String, private val port: Int) : HttpTransport {
    private fun rewrite(uri: URI) =
        if (uri.host == fakeHost) URI(uri.scheme, null, "127.0.0.1", port, uri.path, uri.query, uri.fragment) else uri

    override suspend fun head(uri: URI, timeoutMs: Int) = delegate.head(rewrite(uri), timeoutMs)
    override suspend fun get(uri: URI, timeoutMs: Int) = delegate.get(rewrite(uri), timeoutMs)
}

/**
 * A SWORD repository served over loopback HTTPS: the K5b catalog fixture as `mods.d.tar.gz` and a module ZIP that
 * the server can stream slowly. Wires `WebResource.transport` to the app's [SuspendHttpTransport] for the duration
 * of [use] and registers a custom `sword-https` repository for it.
 */
class RepoServerFixture private constructor(
    val server: TestHttpsServer,
    val repoName: String,
    val manager: DownloadManager,
    val repository: Repository,
    private val cleanup: suspend () -> Unit,
) {
    suspend fun close() = cleanup()

    companion object {
        /** The committed K5b catalog; the :app unit tests run with the project directory `app/` as working directory. */
        val catalogFile = File("../jsword/src/commonTest/resources/k5b/mods.d.tar.gz")

        /** A module ZIP for `K4Latin1` (conf plus an incompressible [payloadBytes] data file). */
        fun moduleZip(payloadBytes: Int): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("mods.d/k4latin1.conf"))
                zip.write("[K4Latin1]\nDataPath=./modules/K4Latin1\nModDrv=RawText\nEncoding=UTF-8\n".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("modules/K4Latin1/ot"))
                zip.write(ByteArray(payloadBytes).also { java.util.Random(1).nextBytes(it) })
                zip.closeEntry()
            }
            return out.toByteArray()
        }

        /** [routes] registers extra handlers (for example `/pkg/K4Latin1.zip`) before the repository is created. */
        suspend fun create(routes: (TestHttpsServer) -> Unit = {}): RepoServerFixture {
            check(catalogFile.canRead()) { "K5b catalog fixture missing: ${catalogFile.absolutePath}" }
            val server = TestHttpsServer()
            server.route("/cat/mods.d.tar.gz") {
                it.reply(200, catalogFile.readBytes(), mapOf("Last-Modified" to "Sun, 13 Sep 2020 12:26:40 GMT"))
            }
            routes(server)
            val base = OkHttpClient.Builder()
                .sslSocketFactory(server.sslContext.socketFactory, server.trustManager)
                .hostnameVerifier { host, _ -> host == "127.0.0.1" }.build()
            val client = AndroidHttpClients(base).downloadClient
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val previous = WebResource.transport
            // KUri(scheme, host, path) cannot carry a port, so the repository uses a unique fake host that the
            // transport maps to the loopback server; the unique host also gives each fixture its own empty catalog cache
            val fakeHost = "k5b-${System.nanoTime()}.test"
            val port = server.base.substringAfterLast(':').toInt()
            WebResource.transport = LoopbackRewrite(SuspendHttpTransport(KtorDownloadHttp(client) { 5_000 }, scope), fakeHost, port)
            val repoName = "K5b fixture $fakeHost"
            DatabaseContainer.instance.repoDb.customRepositoryDao().insert(
                CustomRepository(
                    name = repoName, type = "sword-https", host = fakeHost,
                    catalogDirectory = "/cat", packageDirectory = "/pkg",
                )
            )
            val settings = get<AppSettings>(AppSettings::class.java)
            val manager = DownloadManager(
                settings, get(BookInstallWatcher::class.java),
                GenericFileDownloader(settings = settings, notifier = get(UserNotifier::class.java), strings = get(CoreStrings::class.java)),
                get(AppEnvironment::class.java),
            )
            manager.refreshInstallManager()
            val repository = Repository(repoName, BookFilter { true }, manager)
            return RepoServerFixture(server, repoName, manager, repository) {
                WebResource.transport = previous
                scope.cancel()
                client.close()
                try {
                    withTimeout(5_000) {
                        scope.coroutineContext[Job]!!.join()
                        client.engine.coroutineContext[Job]!!.join()
                    }
                } finally {
                    base.dispatcher.executorService.shutdownNow()
                    base.connectionPool.evictAll()
                    server.close()
                }
            }
        }
    }
}
