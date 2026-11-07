package net.bible.android.control.report

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.activity.ActivityFailureReport
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityState
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidActivityReporterTest {
    private fun item(cause: Throwable = IllegalStateException("network")) = ActivityItem(
        id = "kjv", kind = ActivityKind.DOWNLOAD, title = "KJV", state = ActivityState.FAILED,
        progress = null, message = "Download failed", cancel = null, startedAt = 0,
        finishedAt = 1, read = false, cause = cause,
    )

    @Test
    fun noActivitySkipsDispatch() = runTest {
        var calls = 0
        val reporter = AndroidActivityReporter({ this }, { null }) { _, _, _ -> calls++ }
        reporter.report(item())
        runCurrent()
        assertEquals(0, calls)
    }

    @Test
    fun capturesLiveActivityAndSuppressesDuplicatesUntilReportCompletes() = runTest {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            val activity = controller.get()
            var current: ActivityBase? = activity
            val release = CompletableDeferred<Unit>()
            val cause = IllegalStateException("original")
            val calls = mutableListOf<Triple<ActivityBase, Throwable, String>>()
            val reporter = AndroidActivityReporter({ this }, { current }) { captured, error, source ->
                calls += Triple(captured, error, source)
                release.await()
            }
            reporter.report(item(cause))
            current = null
            runCurrent()
            assertEquals(1, calls.size)
            assertSame(activity, calls.single().first)
            assertSame(cause, (calls.single().second as ActivityFailureReport).cause)
            assertEquals("activity list", calls.single().third)
            current = activity
            reporter.report(item())
            runCurrent()
            assertEquals(1, calls.size)
            release.complete(Unit)
            runCurrent()
            reporter.report(item())
            runCurrent()
            assertEquals(2, calls.size)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun finishingActivitySkipsDispatch() = runTest {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.finish()
            var calls = 0
            val reporter = AndroidActivityReporter({ this }, { activity }) { _, _, _ -> calls++ }
            reporter.report(item())
            runCurrent()
            assertEquals(0, calls)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
