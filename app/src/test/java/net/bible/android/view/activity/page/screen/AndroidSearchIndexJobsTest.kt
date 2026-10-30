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
            assertEquals(listOf("Indexing KJV", "Index download"), feed.snapshot().map { it.label })
            assertEquals(45, feed.snapshot().first().percent)
            assertFalse(feed.isEveryJobFinished())
            var labelsAtCompletion = listOf("not called")
            val stop = feed.subscribe { _, isFinished ->
                if (isFinished()) labelsAtCompletion = feed.snapshot().map { it.label }
            }
            try {
                index.done()
                assertEquals(listOf("Index download"), labelsAtCompletion)
            } finally {
                stop()
            }
            downloadedIndex.done()
            assertTrue(feed.snapshot().isEmpty())
            assertTrue(feed.isEveryJobFinished())
        } finally {
            listOf(download, index, downloadedIndex, finished).forEach { it.isNotifyUser = true; it.done() }
        }
    }
}
