package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities.RecentLabel
import net.bible.android.view.activity.bookmark.ManageLabelsContract
import net.bible.android.view.activity.bookmark.updateFrom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecentLabelsConcurrencyTest {
    @Test
    fun workspaceCopyKeepsItsSnapshotWhileRecentLabelsAreUpdated() {
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined))
        val copying = CountDownLatch(1)
        val updated = CountDownLatch(1)
        val first = IdType()
        val second = IdType()
        val newLabels = List(50) { IdType() }
        val executor = Executors.newSingleThreadExecutor()
        // Stop the real deepCopy iterator after its first element, not the writer's iterator.
        val copyingThread = Thread.currentThread()
        repository.workspaceSettings.recentLabels = object : ArrayList<RecentLabel>() {
            override fun iterator(): MutableIterator<RecentLabel> {
                val delegate = super.iterator()
                var firstElement = true
                return object : MutableIterator<RecentLabel> by delegate {
                    override fun next(): RecentLabel {
                        val value = delegate.next()
                        if (Thread.currentThread() === copyingThread && firstElement) {
                            firstElement = false
                            copying.countDown()
                            assertTrue(updated.await(10, TimeUnit.SECONDS), "Recent-label updates did not finish")
                        }
                        return value
                    }
                }
            }
        }.apply {
            add(RecentLabel(first, 1))
            add(RecentLabel(second, 2))
        }
        val writer = executor.submit {
            assertTrue(copying.await(10, TimeUnit.SECONDS), "Workspace copy did not start")
            try {
                newLabels.forEach { repository.updateRecentLabels(listOf(it)) }
            } finally {
                updated.countDown()
            }
        }
        try {
            val saved = repository.workspaceSettings.deepCopy()
            writer.get(10, TimeUnit.SECONDS)
            assertEquals(listOf(RecentLabel(first, 1), RecentLabel(second, 2)), saved.recentLabels)
            assertEquals(newLabels.takeLast(15), repository.workspaceSettings.recentLabels.map { it.labelId })
        } finally {
            copying.countDown()
            updated.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun labelManagerResultCannotMutatePublishedAutoAssignLabels() {
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined))
        val labels = setOf(IdType(), IdType())
        val result = ManageLabelsContract.ManageLabelsData(
            mode = ManageLabelsContract.Mode.WORKSPACE,
            autoAssignLabels = labels.toMutableSet(),
        )
        repository.workspaceSettings.updateFrom(result)
        result.autoAssignLabels.clear()
        assertEquals(labels, repository.workspaceSettings.deepCopy().autoAssignLabels)
    }

    @Test
    fun concurrentLabelWritersDoNotLoseUpdates() {
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined))
        val labels = List(12) { IdType() }
        val executor = Executors.newFixedThreadPool(labels.size)
        val start = CountDownLatch(1)
        try {
            val writers = labels.map { label -> executor.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                repeat(100) { repository.updateRecentLabels(listOf(label)) }
            } }
            start.countDown()
            writers.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(labels.toSet(), repository.workspaceSettings.recentLabels.map { it.labelId }.toSet())
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun concurrentCursorWritersKeepPreviousSnapshotsAndAllLabels() {
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined))
        val labels = List(12) { IdType() }
        repository.workspaceSettings.setStudyPadCursor(labels.first(), 1)
        val snapshot = repository.workspaceSettings.studyPadCursors
        val executor = Executors.newFixedThreadPool(labels.size)
        val start = CountDownLatch(1)
        try {
            val writers = labels.map { label -> executor.submit {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                repeat(100) { repository.workspaceSettings.setStudyPadCursor(label, it) }
            } }
            start.countDown()
            repeat(1_000) { repository.workspaceSettings.deepCopy() }
            writers.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals(mapOf(labels.first() to 1), snapshot)
            assertEquals(labels.associateWith { 99 }, repository.workspaceSettings.studyPadCursors)
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun updatingAnExistingLabelDoesNotMutateAPreviousSnapshot() {
        val repository = WindowRepository(CoroutineScope(Dispatchers.Unconfined))
        val first = IdType()
        val second = IdType()
        repository.workspaceSettings.recentLabels = mutableListOf(RecentLabel(first, 1), RecentLabel(second, 2))
        val snapshot = repository.workspaceSettings.recentLabels.toList()

        repository.updateRecentLabels(listOf(first))

        assertEquals(listOf(RecentLabel(first, 1), RecentLabel(second, 2)), snapshot)
        assertEquals(listOf(second, first), repository.workspaceSettings.recentLabels.map { it.labelId })
        assertTrue(repository.workspaceSettings.recentLabels.last().lastAccess > 2)
    }
}
