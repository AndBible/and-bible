package net.bible.service.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.android.control.download.DownloadQueue
import net.bible.android.control.download.repoIdentity
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.http.TestHttpsServer
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.install.DownloadCancelledException
import org.crosswire.jsword.book.sword.SwordBookPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.get
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Cancelling a module download must stop the transfer, leave no partial module installed and surface the way the
 * app handles it (K5b review focus 5). The server streams a module ZIP slowly and records when the client hangs up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallCancelTest {
    @Before fun before() = DatabaseContainer.reset()
    @After fun after() = DatabaseContainer.reset()

    private val chunk = 32 * 1024
    private val zip = RepoServerFixture.moduleZip(payloadBytes = 8 * 1024 * 1024)
    private val firstChunkSent = CountDownLatch(1)
    private val clientHungUp = CountDownLatch(1)
    private val finishedBody = CountDownLatch(1)

    /** Streams the ZIP in [chunk]-sized pieces, 20 ms apart; a failed write means the client closed the connection. */
    private fun slowZip(server: TestHttpsServer) = server.route("/pkg/K4Latin1.zip") { exchange ->
        exchange.sendResponseHeaders(200, zip.size.toLong())
        try {
            var offset = 0
            while (offset < zip.size) {
                val n = minOf(chunk, zip.size - offset)
                exchange.responseBody.write(zip, offset, n)
                exchange.responseBody.flush()
                offset += n
                firstChunkSent.countDown()
                Thread.sleep(20)
            }
            finishedBody.countDown()
        } catch (e: IOException) {
            clientHungUp.countDown()
        }
    }

    private suspend fun await(latch: CountDownLatch, seconds: Long = 20) =
        withContext(Dispatchers.IO) { assertTrue("latch not reached in $seconds s", latch.await(seconds, TimeUnit.SECONDS)) }

    private val moduleDir get() = File(SwordBookPath.getSwordDownloadDir().toString(), "modules/K4Latin1")
    private val confFile get() = File(SwordBookPath.getSwordDownloadDir().toString(), "mods.d/k4latin1.conf")

    private fun assertNothingInstalled() {
        assertNull(Books.installed().getBook("K4Latin1"))
        assertFalse("module dir must not exist: $moduleDir", moduleDir.exists())
        assertFalse("conf must not exist: $confFile", confFile.exists())
    }

    private suspend fun book(fixture: RepoServerFixture): Book =
        withContext(Dispatchers.IO) { fixture.repository.getRepoBooks(refresh = false) }.single { it.initials == "K4Latin1" }

    @Test fun cancellingTheCallerStopsTheTransferAndInstallsNothing() = runBlocking {
        slowZipFixture().use { fixture ->
            val book = book(fixture)
            assertNothingInstalled()
            val job = launch(Dispatchers.IO) { fixture.manager.installBook(fixture.repoName, book) }
            await(firstChunkSent)
            withTimeout(20_000) { job.cancelAndJoin() }

            assertTrue(job.isCancelled)
            // the cancellation reached the server: the body never completed and a write failed
            await(clientHungUp)
            assertEquals(1, finishedBody.count)
            assertNothingInstalled()
            // the install job ended (not left running in the progress list)
            assertTrue(JobManager.findJob(Progress.INSTALL_BOOK.format(book.repoIdentity))?.isFinished != false)
        }
    }

    @Test fun userCancelThroughTheProgressApiThrowsDownloadCancelledAndLeavesTheCallerActive() = runBlocking {
        slowZipFixture().use { fixture ->
            val book = book(fixture)
            val outcome = CompletableDeferred<Throwable?>()
            val caller = async(Dispatchers.IO) {
                try {
                    fixture.manager.installBook(fixture.repoName, book)
                    outcome.complete(null)
                } catch (e: Throwable) {
                    outcome.complete(e)
                }
                // the caller's own coroutine is still active after a user cancel
                isActive
            }
            await(firstChunkSent)
            val progress = JobManager.findJob(Progress.INSTALL_BOOK.format(book.repoIdentity))!!
            progress.cancel()

            val error = withTimeout(20_000) { outcome.await() }
            assertTrue("expected DownloadCancelledException, got $error", error is DownloadCancelledException)
            assertTrue("caller must stay active", withTimeout(20_000) { caller.await() })
            await(clientHungUp)
            assertNothingInstalled()
        }
    }

    @Test fun downloadQueueReportsAUserCancelAsInstallCancelled() = runBlocking {
        slowZipFixture().use { fixture ->
            val book = book(fixture)
            val statuses = java.util.Collections.synchronizedList(mutableListOf<DocumentStatus>())
            val queue = DownloadQueue(
                { statuses.add(it) }, get(UserNotifier::class.java), get(CoreStrings::class.java),
            )
            val job = launch { queue.addDocumentToDownloadQueue(book, fixture.repository) }
            await(firstChunkSent)
            JobManager.findJob(Progress.INSTALL_BOOK.format(book.repoIdentity))!!.cancel()
            withTimeout(20_000) { job.join() }

            assertEquals(listOf(DocumentInstallStatus.INSTALL_CANCELLED), statuses.map { it.documentInstallStatus })
            assertFalse(queue.isInQueue(book))
            assertFalse(queue.isErrorDownloading(book))
            assertNothingInstalled()
        }
    }

    @Test fun cancellingTheQueueCallerReportsCancelledNotAnErrorAndFreesTheQueueSlot() = runBlocking {
        slowZipFixture().use { fixture ->
            val book = book(fixture)
            val statuses = java.util.Collections.synchronizedList(mutableListOf<DocumentStatus>())
            val queue = DownloadQueue(
                { statuses.add(it) }, get(UserNotifier::class.java), get(CoreStrings::class.java),
            )
            val job = launch { queue.addDocumentToDownloadQueue(book, fixture.repository) }
            await(firstChunkSent)
            withTimeout(20_000) { job.cancelAndJoin() }

            assertTrue(job.isCancelled)
            assertEquals(listOf(DocumentInstallStatus.INSTALL_CANCELLED), statuses.map { it.documentInstallStatus })
            assertFalse(queue.isInQueue(book))
            assertFalse(queue.isErrorDownloading(book))
            await(clientHungUp)
            assertNothingInstalled()
        }
    }

    @Test fun stalledDownloadQueueReportsErrorAndNotifiesWithoutCancellingCaller() = runBlocking {
        val release = CountDownLatch(1)
        val fixture = RepoServerFixture.create(timeoutMs = 500) { server ->
            server.route("/pkg/K4Latin1.zip") { exchange ->
                exchange.sendResponseHeaders(200, zip.size.toLong())
                exchange.responseBody.write(zip, 0, chunk)
                exchange.responseBody.flush()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        try {
            val book = book(fixture)
            val statuses = mutableListOf<DocumentStatus>()
            val messages = mutableListOf<Pair<String, Throwable?>>()
            val strings = get<CoreStrings>(CoreStrings::class.java)
            val notifier = object : UserNotifier {
                override fun showInfo(message: String, long: Boolean) = Unit
                override fun showError(message: String, cause: Throwable?) { messages.add(message to cause) }
            }
            val queue = DownloadQueue(statuses::add, notifier, strings)
            queue.addDocumentToDownloadQueue(book, fixture.repository)
            assertEquals(listOf(DocumentInstallStatus.ERROR_DOWNLOADING), statuses.map { it.documentInstallStatus })
            assertEquals(1, messages.size)
            assertEquals("transport failure must not be described as HTTP 200", strings.errorDownloading, messages.single().first)
            val error = messages.single().second
            assertTrue("notifier must retain the timeout cause: $error", error?.cause is net.bible.sharedcore.http.DownloadTimeoutException)
            assertTrue("notifier must retain the provider cause", error!!.cause!!.cause != null)
            assertTrue("caller must stay active", isActive)
            assertFalse(queue.isInQueue(book))
            assertTrue(queue.isErrorDownloading(book))
            assertNothingInstalled()
        } finally {
            release.countDown()
            fixture.close()
        }
    }

    private suspend fun slowZipFixture() = SuspendFixture(RepoServerFixture.create { slowZip(it) })

    /** Adapts [RepoServerFixture.close] (suspend) to a scoped block. */
    private class SuspendFixture(val fixture: RepoServerFixture) {
        suspend fun <T> use(block: suspend (RepoServerFixture) -> T): T =
            try { block(fixture) } finally { fixture.close() }
    }
}
