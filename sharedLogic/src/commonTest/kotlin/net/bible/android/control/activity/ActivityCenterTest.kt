package net.bible.android.control.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityCenterTest {

    private fun TestScope.center() = ActivityCenter(backgroundScope) { currentTime }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun runningItemHiddenForFirstSecond() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(999)
        assertTrue(c.items.value.isEmpty())
        advance(1)
        assertEquals(listOf(ActivityState.RUNNING), c.items.value.map { it.state })
    }

    @Test
    fun subSecondJobNeverShown() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(500)
        c.succeed("a")
        assertTrue(c.items.value.isEmpty())
        advance(5000)
        assertTrue(c.items.value.isEmpty())
    }

    @Test
    fun succeededKeptSixtySeconds() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(2000)
        c.succeed("a")
        assertEquals(listOf(ActivityState.SUCCEEDED), c.items.value.map { it.state })
        advance(59_999)
        assertEquals(1, c.items.value.size)
        advance(1)
        assertTrue(c.items.value.isEmpty())
    }

    @Test
    fun syncSuccessNotKept() = runTest {
        val c = center()
        c.start("s", ActivityKind.SYNC, "Sync")
        advance(5000)
        assertEquals(1, c.items.value.size)
        c.succeed("s")
        assertTrue(c.items.value.isEmpty())
    }

    @Test
    fun cancelledDroppedAtOnce() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(2000)
        c.cancelled("a")
        assertTrue(c.items.value.isEmpty())
        assertEquals(0, c.unreadFailures.value)
    }

    @Test
    fun failedKeptUntilDismissed() = runTest {
        val c = center()
        c.start("a", ActivityKind.INSTALL, "A")
        advance(2000)
        c.fail("a", "boom")
        val item = c.items.value.single()
        assertEquals(ActivityState.FAILED, item.state)
        assertEquals("boom", item.message)
        assertFalse(item.read)
        assertEquals(1, c.unreadFailures.value)
        advance(3_600_000)
        assertEquals(1, c.items.value.size)
        c.markAllRead()
        assertEquals(0, c.unreadFailures.value)
        assertTrue(c.items.value.single().read)
        c.dismiss("a")
        assertTrue(c.items.value.isEmpty())
    }

    @Test
    fun failureBeforeDebounceIsShown() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(200)
        c.fail("a", "x")
        assertEquals(listOf(ActivityState.FAILED), c.items.value.map { it.state })
        advance(5000)
        assertEquals(1, c.items.value.size)
    }

    @Test
    fun capKeepsNewestFiftyFinished() = runTest {
        val c = center()
        c.start("run", ActivityKind.DOWNLOAD, "running")
        for (i in 0 until 55) {
            c.start("f$i", ActivityKind.DOWNLOAD, "f$i")
            advance(10)
            c.fail("f$i", "x")
        }
        advance(2000)
        val ids = c.items.value.map { it.id }
        assertEquals(51, ids.size)
        assertEquals("run", ids.first())
        assertEquals((54 downTo 5).map { "f$it" }, ids.drop(1))
        assertEquals(50, c.unreadFailures.value)
    }

    @Test
    fun manyRunningItemsKeepOrderAndCount() = runTest {
        val c = center()
        for (i in 0 until 20) {
            c.start("r$i", ActivityKind.DOWNLOAD, "r$i")
            advance(1)
        }
        c.start("f", ActivityKind.DOWNLOAD, "f")
        c.fail("f", "x")
        advance(5000)
        val ids = c.items.value.map { it.id }
        assertEquals(21, ids.size)
        assertEquals((19 downTo 0).map { "r$it" } + "f", ids)
    }

    @Test
    fun startSameIdRestartsItem() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(2000)
        c.fail("a", "x")
        assertEquals(1, c.unreadFailures.value)
        c.start("a", ActivityKind.DOWNLOAD, "A")
        assertEquals(0, c.unreadFailures.value)
        advance(999)
        assertTrue(c.items.value.isEmpty())
        advance(1)
        assertEquals(listOf(ActivityState.RUNNING), c.items.value.map { it.state })
    }

    @Test
    fun lateProgressDoesNotChangeFinishedItem() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(2000)
        c.update("a", 40)
        assertEquals(40, c.items.value.single().progress)
        c.fail("a", "x")
        c.update("a", 10)
        assertEquals(40, c.items.value.single().progress)
    }

    @Test
    fun earlierDeadlineAddedWhileSleepingIsHonoured() = runTest {
        val c = center()
        c.start("a", ActivityKind.DOWNLOAD, "A")
        advance(1000)
        c.succeed("a") // expires at 61_000
        advance(10)
        c.start("b", ActivityKind.DOWNLOAD, "B") // appears at 1010+1000, well before 61_000
        advance(1000)
        assertEquals(setOf("a", "b"), c.items.value.map { it.id }.toSet())
    }

    @Test
    fun startOrUpdateStartsThenUpdatesAndNeverRevivesAFinishedItem() = runTest {
        val c = center()
        c.startOrUpdate("a", ActivityKind.DOWNLOAD, "A", 10, null)
        advance(1000)
        c.startOrUpdate("a", ActivityKind.DOWNLOAD, "A", 60, {})
        assertEquals(60, c.items.value.single().progress)
        assertTrue(c.items.value.single().cancel != null)
        c.cancelled("a")
        assertTrue(c.items.value.isEmpty())
        assertFalse(c.isRunning("a"))
    }

    @Test
    fun startOrFailFailsRunningItemAndCreatesFailedWhenAbsent() = runTest {
        val c = center()
        c.start("a", ActivityKind.INSTALL, "A")
        advance(2000)
        c.startOrFail("a", ActivityKind.INSTALL, "A", "boom")
        c.startOrFail("b", ActivityKind.INSTALL, "B", "bang")
        assertEquals(setOf("a" to ActivityState.FAILED, "b" to ActivityState.FAILED), c.items.value.map { it.id to it.state }.toSet())
        assertEquals(setOf("boom", "bang"), c.items.value.map { it.message }.toSet())
        assertEquals(2, c.unreadFailures.value)
    }
}
