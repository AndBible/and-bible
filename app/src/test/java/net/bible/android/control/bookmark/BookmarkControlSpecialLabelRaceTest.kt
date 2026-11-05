package net.bible.android.control.bookmark

import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings

import kotlinx.coroutines.runBlocking
import net.bible.test.testOrderedLauncher
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.service.db.DatabaseContainer
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarkControlSpecialLabelRaceTest {
    /**
     * Minimal reproduction of the production race behind the leaked `UNIQUE constraint failed:
     * Label.id` (fix batch 2 task 6, run 2): `BookmarkControl.getOrCreateSpecialLabel` is a
     * check-then-insert with no lock, so two threads on a fresh DB both insert. Measured 265/300
     * iterations failed (271 at the RED run) before the fix; getOrCreateSpecialLabel now does check+insert
     * under one lock, so it must report 0.
     *
     * Real threads and durable commits, no virtual time: every round commits a delete and an insert to the real
     * bookmark database file (TRUNCATE journal, fsync per commit). `runTest`'s 60 s wall-clock budget made this a
     * load-dependent red (#3947): with the host disk busy (48 fsync writers plus 12 busy loops) the body took over
     * 60 s while every thread was still committing. The body therefore runs in `runBlocking`, and a stuck round (a
     * real deadlock) fails with the stuck threads' stacks after [ROUND_DEADLINE_MS] instead of hanging the suite.
     */
    @Test fun twoThreadsCreatingTheSameSpecialLabel() = runBlocking {
        val bc = BookmarkControl(Mockito.mock(WindowControl::class.java), testAppSettings(), testCoreStrings(), testOrderedLauncher(), net.bible.android.platform.androidSaxTextPolicy())
        val failures = AtomicInteger()
        val first = AtomicInteger()
        repeat(300) {
            bc.deleteLabel(bc.paragraphBreakLabel())
            val barrier = CyclicBarrier(2)
            val ts = List(2) { Thread { barrier.await(); try { runBlocking { bc.paragraphBreakLabel() } } catch (e: Exception) { if (failures.getAndIncrement() == 0) System.err.println("REPRO " + e.message?.take(80)) } }.also { it.isDaemon = true; it.start() } }
            ts.forEach { it.join(ROUND_DEADLINE_MS) }
            ts.filter { it.isAlive }.takeIf { it.isNotEmpty() }?.let { stuck ->
                val report = stuck.joinToString("\n\n") { t -> t.name + "\n" + t.stackTrace.joinToString("\n") { "  at $it" } }
                // Do not leave stuck writers committing into later tests' databases.
                stuck.forEach { it.interrupt() }
                org.junit.Assert.fail("special-label round stuck for ${ROUND_DEADLINE_MS} ms:\n" + report)
            }
        }
        org.junit.Assert.assertEquals("special-label creation must be race-free", 0, failures.get())
    }

    private companion object {
        /** A round is two label writes; even a badly loaded disk finishes it in seconds, a deadlock never does. */
        const val ROUND_DEADLINE_MS = 120_000L
    }
}
