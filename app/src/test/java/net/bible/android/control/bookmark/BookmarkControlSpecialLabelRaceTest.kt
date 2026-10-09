package net.bible.android.control.bookmark

import kotlinx.coroutines.runBlocking
import net.bible.test.testOrderedLauncher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import kotlinx.coroutines.runBlocking
import net.bible.android.TestBibleApplication
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.window.WindowControl
import kotlinx.coroutines.runBlocking
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import kotlinx.coroutines.runBlocking
import net.bible.android.common.resource.AndroidResourceProvider
import kotlinx.coroutines.runBlocking
import net.bible.service.db.DatabaseContainer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import kotlinx.coroutines.runBlocking
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CyclicBarrier
import kotlinx.coroutines.runBlocking
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
     */
    @Test fun twoThreadsCreatingTheSameSpecialLabel() = runTest {
        val bc = BookmarkControl(Mockito.mock(WindowControl::class.java), Mockito.mock(AndroidResourceProvider::class.java), testOrderedLauncher())
        val failures = AtomicInteger()
        val first = AtomicInteger()
        repeat(300) {
            bc.deleteLabel(bc.paragraphBreakLabel())
            val barrier = CyclicBarrier(2)
            val ts = List(2) { Thread { barrier.await(); try { runBlocking { bc.paragraphBreakLabel() } } catch (e: Exception) { if (failures.getAndIncrement() == 0) System.err.println("REPRO " + e.message?.take(80)) } }.also { it.start() } }
            ts.forEach { it.join() }
        }
        org.junit.Assert.assertEquals("special-label creation must be race-free", 0, failures.get())
    }
}
