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

    private fun withJobs(c: ActivityCenter, isDownload: (Progress) -> Boolean, block: (JobActivitySource) -> Unit) {
        val source = JobActivitySource(c, isDownload)
        source.attach()
        try { block(source) } finally { source.detach() }
    }

    @Test
    fun jobSkipsDownloadJobs() = runTest {
        val c = center()
        val prefix = "test-dl-${currentTime}-"
        withJobs(c, { it.jobID.startsWith("test-dl-") }) {
            val dl = JobManager.createJob("test-dl-job", "Download", null)
            dl.beginJob("x", 100)
            dl.work = 30
            val index = JobManager.createJob("${Progress.CREATE_INDEX.replace("%s", "KJV")}-t1", "Indexing KJV", null)
            index.beginJob("x", 100)
            index.work = 40
            advance(1000)
            index.work = 60
            assertEquals(listOf("Indexing KJV"), c.items.value.map { it.title })
            assertEquals(ActivityKind.INDEX, c.items.value[0].kind)
            assertEquals(60, c.items.value[0].progress)
            dl.done()
            index.done()
            assertEquals(listOf(ActivityState.SUCCEEDED), c.items.value.map { it.state })
        }
        assertTrue(prefix.isNotEmpty())
    }

    @Test
    fun jobCancelOnlyWhenCancelable() = runTest {
        val c = center()
        var cancelled = 0
        withJobs(c, { false }) {
            val plain = JobManager.createJob("test-plain-1", "Plain", null)
            plain.beginJob("x", 100)
            val cancelable = JobManager.createJob("test-cancelable-1", "Cancelable", { cancelled++ })
            cancelable.beginJob("x", 100)
            advance(1000)
            val byTitle = c.items.value.associateBy { it.title }
            assertNull(byTitle.getValue("Plain").cancel)
            val cancel = assertNotNull(byTitle.getValue("Cancelable").cancel)
            cancel()
            assertEquals(1, cancelled)
            assertEquals(listOf("Plain"), c.items.value.map { it.title })
            plain.done()
        }
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
