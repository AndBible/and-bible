package net.bible.android.view.activity.page.screen

import org.crosswire.common.progress.JobManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSearchIndexJobsTest {
    @Test
    fun snapshotAndCompletionIgnoreDownloadsAndFinishedIndexes() {
        val feed = AndroidSearchIndexJobs()
        val download = JobManager.createJob("Downloading files")
        val index = JobManager.createJob("CREATE_INDEX-KJV", "Indexing KJV", null)
        val downloadedIndex = JobManager.createJob("DOWNLOAD_SEARCH_INDEX-ESV", "Index download", null)
        val finished = JobManager.createJob("CREATE_INDEX-OLD", "Old index", null)
        try {
            finished.isNotifyUser = false
            finished.done()
            index.beginJob("Indexing")
            index.work = 45
            // JobManager is a JVM-global registry: jobs left by other tests may be listed too, so use membership.
            assertTrue(feed.snapshot().map { it.label }.containsAll(listOf("Indexing KJV", "Index download")))
            assertFalse("Downloading files" in feed.snapshot().map { it.label })
            assertFalse("Old index" in feed.snapshot().map { it.label })
            assertEquals(45, feed.snapshot().first { it.label == "Indexing KJV" }.percent)
            assertFalse(feed.isEveryJobFinished())
            var labelsAtCompletion = listOf("not called")
            val stop = feed.subscribe { _, isFinished ->
                if (isFinished()) labelsAtCompletion = feed.snapshot().map { it.label }
            }
            try {
                index.done()
                assertTrue("Index download" in labelsAtCompletion)
                assertFalse("Indexing KJV" in labelsAtCompletion)
            } finally {
                stop()
            }
            downloadedIndex.done()
            assertFalse("Index download" in feed.snapshot().map { it.label })
        } finally {
            listOf(download, index, downloadedIndex, finished).forEach { it.isNotifyUser = true; it.done() }
        }
    }
}
