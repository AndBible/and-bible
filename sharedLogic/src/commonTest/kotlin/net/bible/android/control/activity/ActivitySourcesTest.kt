package net.bible.android.control.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.control.download.DocumentStatus
import net.bible.android.control.download.DocumentStatus.DocumentInstallStatus
import net.bible.sharedcore.event.EventSource
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActivitySourcesTest {
    private fun TestScope.center() = ActivityCenter(backgroundScope) { currentTime }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun status(id: String, s: DocumentInstallStatus, percent: Int = 0) = DocumentStatus(id, s, percent)

    private fun download(center: ActivityCenter, events: EventSource<DocumentStatus>) =
        DownloadActivitySource(center, events, titleOf = { "Title $it" }, failedText = { "$it failed" })

    @Test
    fun downloadLifecycle() = runTest {
        val c = center()
        val events = EventSource<DocumentStatus>()
        download(c, events)
        events.emit(status("r--KJV", DocumentInstallStatus.BEING_INSTALLED, 10))
        advance(1000)
        events.emit(status("r--KJV", DocumentInstallStatus.BEING_INSTALLED, 50))
        assertEquals(1, c.items.value.size)
        assertEquals(50, c.items.value[0].progress)
        assertEquals("Title r--KJV", c.items.value[0].title)
        assertEquals(ActivityKind.DOWNLOAD, c.items.value[0].kind)
        events.emit(status("r--KJV", DocumentInstallStatus.INSTALLED, 100))
        assertEquals(listOf(ActivityState.SUCCEEDED), c.items.value.map { it.state })
    }

    @Test
    fun repeatedProgressUpdatesOneItem() = runTest {
        val c = center()
        val events = EventSource<DocumentStatus>()
        download(c, events)
        for (p in 1..100) events.emit(status("d", DocumentInstallStatus.BEING_INSTALLED, p))
        advance(1000)
        assertEquals(1, c.items.value.size)
        assertEquals(100, c.items.value[0].progress)
    }

    @Test
    fun retryAfterFailureReplacesFailedRow() = runTest {
        val c = center()
        val events = EventSource<DocumentStatus>()
        download(c, events)
        events.emit(status("d", DocumentInstallStatus.ERROR_DOWNLOADING))
        assertEquals(listOf(ActivityState.FAILED), c.items.value.map { it.state })
        assertEquals("Title d failed", c.items.value[0].message)
        assertEquals(1, c.unreadFailures.value)
        events.emit(status("d", DocumentInstallStatus.BEING_INSTALLED, 5))
        advance(1000)
        events.emit(status("d", DocumentInstallStatus.INSTALLED, 100))
        assertEquals(listOf(ActivityState.SUCCEEDED), c.items.value.map { it.state })
        assertEquals(0, c.unreadFailures.value)
    }

    @Test
    fun cancelledDownloadIsDropped() = runTest {
        val c = center()
        val events = EventSource<DocumentStatus>()
        download(c, events)
        events.emit(status("d", DocumentInstallStatus.BEING_INSTALLED, 5))
        advance(1000)
        assertEquals(1, c.items.value.size)
        events.emit(status("d", DocumentInstallStatus.INSTALL_CANCELLED))
        assertTrue(c.items.value.isEmpty())
    }

    /** Runs [block] with an attached source; every job it created through [job] is ended and the source detached afterwards. */
    private fun withJobs(c: ActivityCenter, isDownload: (Progress) -> Boolean, block: (JobActivitySource, (String, String, (() -> Unit)?) -> Progress) -> Unit) {
        val created = mutableListOf<Progress>()
        val source = JobActivitySource(c, isDownload)
        source.attach()
        try {
            block(source) { id, name, canceller ->
                JobManager.createJob(id, name, canceller).also { created += it; it.beginJob("x", 100) }
            }
        } finally {
            created.forEach { runCatching { it.done() } }
            source.detach()
        }
    }

    /** Items of this test's own jobs only, so jobs left running by other tests in the JVM cannot disturb the assertions. */
    private fun ActivityCenter.mine(vararg titles: String) = items.value.filter { it.title in titles }

    @Test
    fun jobSkipsDownloadJobs() = runTest {
        val c = center()
        withJobs(c, { it.jobID.startsWith("test-dl-") }) { _, job ->
            val dl = job("test-dl-job", "TestDownload", null)
            dl.work = 30
            val index = job("${Progress.CREATE_INDEX.replace("%s", "KJV")}-t1", "TestIndexing", null)
            index.work = 40
            advance(1000)
            index.work = 60
            assertEquals(listOf("TestIndexing"), c.mine("TestDownload", "TestIndexing").map { it.title })
            val item = c.mine("TestIndexing").single()
            assertEquals(ActivityKind.INDEX, item.kind)
            assertEquals(60, item.progress)
            dl.done()
            index.done()
            assertEquals(listOf(ActivityState.SUCCEEDED), c.mine("TestIndexing").map { it.state })
        }
    }

    @Test
    fun jobCancelOnlyWhenCancelable() = runTest {
        val c = center()
        var cancelled = 0
        withJobs(c, { false }) { _, job ->
            job("test-plain-1", "TestPlain", null)
            job("test-cancelable-1", "TestCancelable") { cancelled++ }
            advance(1000)
            val byTitle = c.mine("TestPlain", "TestCancelable").associateBy { it.title }
            assertNull(byTitle.getValue("TestPlain").cancel)
            val cancel = assertNotNull(byTitle.getValue("TestCancelable").cancel)
            cancel()
            assertEquals(1, cancelled)
            assertEquals(listOf("TestPlain"), c.mine("TestPlain", "TestCancelable").map { it.title })
        }
    }

    @Test
    fun jobCancelabilityIsRereadOnEachEvent() = runTest {
        val c = center()
        withJobs(c, { false }) { _, job ->
            val j = job("test-late-1", "TestLate", { })
            advance(1000)
            assertNotNull(c.mine("TestLate").single().cancel)
            // JobManager listeners only see progress events (state changes go to per-job listeners), so the
            // new cancelability shows with the next progress event.
            j.isCancelable = false
            j.work = 10
            assertNull(c.mine("TestLate").single().cancel)
            j.isCancelable = true
            j.work = 20
            assertNotNull(c.mine("TestLate").single().cancel)
        }
    }

    @Test
    fun finishAfterIneffectiveCancelIsSuccess() = runTest {
        val c = center()
        withJobs(c, { false }) { _, job ->
            // The job reports cancelable but its cancel() is a no-op for it: simulate with a job that is already
            // finished by the time cancel runs (cancel() then does nothing and the job is not "cancelled").
            val j = job("test-ineffective-1", "TestIneffective", { })
            advance(1000)
            val cancel = assertNotNull(c.mine("TestIneffective").single().cancel)
            j.done() // finished normally; the row becomes SUCCEEDED
            assertEquals(listOf(ActivityState.SUCCEEDED), c.mine("TestIneffective").map { it.state })
            cancel() // late tap: job already finished, so cancel() is a no-op and must not leave a stale cancel mark
            val again = job("test-ineffective-1", "TestIneffective", { })
            advance(1000)
            again.done()
            assertEquals(ActivityState.SUCCEEDED, c.mine("TestIneffective").last().state)
        }
    }

    @Test
    fun downloadCloseReleasesMonitoringAndSubscription() = runTest {
        val c = center()
        val events = EventSource<DocumentStatus>()
        var started = 0
        var stopped = 0
        val source = DownloadActivitySource(c, events, { it }, { it }, { started++ }, { stopped++ }, { null })
        assertEquals(1, started)
        assertEquals(0, stopped)
        source.close()
        source.close()
        assertEquals(1, stopped)
        events.emit(status("d", DocumentInstallStatus.ERROR_DOWNLOADING))
        assertTrue(c.items.value.isEmpty())
    }

    @Test
    fun downloadCancelFollowsJswordCancelability() = runTest {
        val c = center()
        val events = EventSource<DocumentStatus>()
        var allowed = false
        DownloadActivitySource(c, events, { it }, { it }, cancelFor = { if (allowed) ({ }) else null })
        events.emit(status("d", DocumentInstallStatus.BEING_INSTALLED, 1))
        advance(1000)
        assertNull(c.items.value.single().cancel)
        allowed = true
        events.emit(status("d", DocumentInstallStatus.BEING_INSTALLED, 2))
        assertNotNull(c.items.value.single().cancel)
    }

    @Test
    fun syncShowsWhileRunningAndLeavesNoRow() = runTest {
        val c = center()
        val running = EventSource<Boolean>()
        SyncActivitySource(c, running, { "Syncing" })
        running.emit(true)
        running.emit(true)
        advance(1000)
        assertEquals(listOf("Syncing"), c.items.value.map { it.title })
        assertEquals(ActivityKind.SYNC, c.items.value[0].kind)
        running.emit(false)
        assertTrue(c.items.value.isEmpty())
    }

    @Test
    fun repeatedSyncFailureCollapsesToOneRow() = runTest {
        val c = center()
        val sync = SyncActivitySource(c, EventSource<Boolean>(), { "Syncing" })
        sync.reportFailure("x")
        sync.reportFailure("x")
        assertEquals(listOf(ActivityState.FAILED), c.items.value.map { it.state })
        assertEquals(1, c.unreadFailures.value)
        sync.reportFailure("y")
        assertEquals(2, c.items.value.size)
        assertFalse(c.items.value.any { it.state != ActivityState.FAILED })
    }
}
