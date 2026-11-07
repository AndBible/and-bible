package net.bible.android.control.report

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.activity.ActivityFailureReport
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityState
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
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
    fun availabilityIsFalseWithoutAnActivity() = runTest {
        val reporter = AndroidActivityReporter({ backgroundScope }, { null }) { _, _, _ -> }
        runCurrent()
        assertEquals(false, reporter.available.value)
    }

    @Test
    fun availabilityEmitsWhenCurrentActivityStartsAndStops() = runTest {
        CurrentActivityHolder.forgetActivitiesForTest()
        val reporter = AndroidActivityReporter({ backgroundScope })
        val emissions = mutableListOf<Boolean>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reporter.available.collect { emissions += it }
        }
        runCurrent()
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            runCurrent()
            assertEquals(listOf(false, true), emissions)
            controller.pause().stop().destroy()
            runCurrent()
            assertEquals(listOf(false, true, false), emissions)
        } finally {
            collector.cancel()
            if (!controller.get().isDestroyed) controller.pause().stop().destroy()
            CurrentActivityHolder.forgetActivitiesForTest()
        }
    }

    @Test
    fun cancellingReporterScopeStopsAvailabilityUpdates() = runTest {
        CurrentActivityHolder.forgetActivitiesForTest()
        val reporterScope = CoroutineScope(backgroundScope.coroutineContext + Job())
        val reporter = AndroidActivityReporter({ reporterScope })
        runCurrent()
        reporterScope.cancel()
        runCurrent()
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            runCurrent()
            assertEquals(false, reporter.available.value)
        } finally {
            controller.pause().stop().destroy()
            CurrentActivityHolder.forgetActivitiesForTest()
        }
    }

    @Test
    fun availabilityTracksActivityFinishingAndDestruction() = runTest {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val activity = controller.get()
        val reporter = AndroidActivityReporter({ backgroundScope }, { activity }) { _, _, _ -> }
        try {
            runCurrent()
            assertEquals(true, reporter.available.value)
            val emissions = mutableListOf<Boolean>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                reporter.available.collect { emissions += it }
            }
            activity.finish()
            runCurrent()
            assertEquals(false, reporter.available.value)
            assertEquals(listOf(true, false), emissions)
            controller.pause().stop().destroy()
            runCurrent()
            assertEquals(false, reporter.available.value)
        } finally {
            if (!activity.isDestroyed) controller.pause().stop().destroy()
        }
    }

    @Test
    fun noActivitySkipsDispatch() = runTest {
        var calls = 0
        val reporter = AndroidActivityReporter({ backgroundScope }, { null }) { _, _, _ -> calls++ }
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
            val reporter = AndroidActivityReporter({ backgroundScope }, { current }) { captured, error, source ->
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
    fun destroyingActivityDuringDispatchAllowsReplacementToReport() = runTest {
        val first = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val replacement = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val release = CompletableDeferred<Unit>()
        var current: ActivityBase = first.get()
        val calls = mutableListOf<ActivityBase>()
        val reporter = AndroidActivityReporter({ backgroundScope }, { current }) { activity, _, _ ->
            calls += activity
            release.await()
        }
        try {
            reporter.report(item())
            runCurrent()
            assertEquals(listOf(first.get()), calls)
            first.pause().stop()
            current = replacement.get()
            reporter.report(item())
            runCurrent()
            assertEquals(1, calls.size)
            first.destroy()
            runCurrent()
            reporter.report(item())
            runCurrent()
            assertEquals(listOf(first.get(), replacement.get()), calls)
        } finally {
            release.complete(Unit)
            runCurrent()
            replacement.pause().stop().destroy()
        }
    }

    @Test
    fun finishingActivitySkipsDispatch() = runTest {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        try {
            val activity = controller.get()
            activity.finish()
            var calls = 0
            val reporter = AndroidActivityReporter({ backgroundScope }, { activity }) { _, _, _ -> calls++ }
            reporter.report(item())
            runCurrent()
            assertEquals(0, calls)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
