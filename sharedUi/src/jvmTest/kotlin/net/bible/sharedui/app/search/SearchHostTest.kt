package net.bible.sharedui.app.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.bible.sharedui.app.HostShell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHostTest {
    private val shell = object : HostShell {
        override val hostScope: CoroutineScope = CoroutineScope(Job())
        override fun exitHost() = Unit
        override fun setWindowTitle(title: String) = Unit
        override fun recreateHost() = Unit
        override fun openReading() = Unit
        override fun setHistoryRoute(owner: Any, route: String) = Unit
        override fun clearHistoryRoute(owner: Any) = Unit
        override fun goBackInHistory(): Boolean = false
        override fun navigate(route: String) = Unit
    }

    private fun host() = SearchHost(shell = shell)

    @Test
    fun progressStatusListsEveryRunningJobNotOnlyIndexJobs() {
        val download = org.crosswire.common.progress.JobManager.createJob("Downloading files")
        val index = org.crosswire.common.progress.JobManager.createJob("CREATE_INDEX-3911", "Indexing", null)
        var labels = emptyList<String>()
        val stop = host().observeProgressStatusJobs { labels = it.map { row -> row.label } }
        try {
            // JobManager is a JVM-global registry: jobs left by other tests may be listed too, so assert membership.
            assertTrue("Downloading files" in labels && "Indexing" in labels, "generic progress keeps both: $labels")
        } finally {
            stop()
            download.done()
            index.done()
        }
    }

    @Test
    fun progressStatusStopsListeningOnceTheReturnedUndoRuns() {
        val job = org.crosswire.common.progress.JobManager.createJob("Listener probe", "Probe", null)
        var calls = 0
        val stop = host().observeProgressStatusJobs { calls++ }
        stop()
        val afterStop = calls
        try {
            job.work = 50
            assertEquals(afterStop, calls, "no refresh may arrive after the undo ran")
        } finally {
            job.done()
        }
    }
}
