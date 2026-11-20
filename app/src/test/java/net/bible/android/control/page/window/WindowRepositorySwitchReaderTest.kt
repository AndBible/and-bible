package net.bible.android.control.page.window

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A workspace switch's apply (on Main, under the repository's apply lock) clears the active window and sets the
 * new one only at its end. A reader on another thread during that gap must get a window, never null: it either
 * returns the window it already saw, or waits for the apply to finish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowRepositorySwitchReaderTest {
    private lateinit var f: Fixture

    @Before fun setUp() {
        f = Fixture()
        runBlocking { f.repo.initialize() }
    }

    @After fun tearDown() {
        f.repo.afterLoadedCheck = null
        f.repo.afterApplyClear = null
        f.close()
    }

    /** Waits (bounded) until [thread] is parked or has ended, so no fixed sleep decides the ordering. */
    private fun awaitParkedOrDone(thread: Thread) {
        val deadline = System.currentTimeMillis() + 5_000
        while (thread.isAlive && thread.state != Thread.State.WAITING && thread.state != Thread.State.BLOCKED &&
            System.currentTimeMillis() < deadline) Thread.sleep(5)
    }

    private fun startReader(name: String, result: CompletableFuture<Window>) = Thread({
        try { result.complete(f.repo.activeWindow) } catch (e: Throwable) { result.completeExceptionally(e) }
    }, name).apply { start() }

    /**
     * Breaks if the getter reads the field twice: a reader that saw a loaded repository, and then lost the window to
     * a switch's `clear()` before its second read, threw a NullPointerException (the check-then-read race).
     */
    @Test fun readerThatSawALoadedRepositoryKeepsAWindowThroughASwitchClear() {
        val checked = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        f.repo.afterLoadedCheck = {
            if (Thread.currentThread().name == "reader") { checked.countDown(); proceed.await(10, TimeUnit.SECONDS) }
        }
        val result = CompletableFuture<Window>()
        val before = f.repo.activeWindow
        val reader = startReader("reader", result)
        try {
            check(checked.await(10, TimeUnit.SECONDS)) { "the reader never reached its loaded check" }
            f.repo.afterApplyClear = {
                // The reader finishes its read while the repository has no window (a second read would NPE); it never
                // waits for this apply, which is why joining it here cannot deadlock.
                proceed.countDown()
                reader.join(10_000)
            }
            runBlocking { f.repo.loadFromDb(f.repo.id) }
            val window = result.get(10, TimeUnit.SECONDS)
            assertNotNull(window)
            assertSame("the reader returns the window it saw", before, window)
        } finally {
            proceed.countDown()
        }
    }

    /** Breaks if a reader arriving during the gap returns before the apply has set the new active window. */
    @Test fun readerArrivingBetweenClearAndTheNewActiveWindowWaitsForTheApply() {
        val result = CompletableFuture<Window>()
        f.repo.afterApplyClear = {
            f.repo.afterApplyClear = null
            awaitParkedOrDone(startReader("late-reader", result))
        }
        runBlocking { f.repo.loadFromDb(f.repo.id) }
        val window = result.get(10, TimeUnit.SECONDS)
        assertSame("the reader gets the switched-to workspace's active window", f.repo.activeWindow, window)
    }
}
