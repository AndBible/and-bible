package net.bible.android.view.compose

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.launch
import net.bible.android.ComposeUiDispatcherResetRule
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.EmptyCoroutineContext

/** Lost Handler/frame callbacks must not strand work retained in Compose's SDK-wide dispatcher. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [29])
class ComposeUiDispatcherResetRuleTest {
    @Test fun retainedFrameRunsWithoutAnotherFrameRequestAfterTheLooperDropsItsCallback() {
        val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as AndroidUiDispatcher
        net.bible.test.resetComposeUiDispatcher()
        shadowOf(Looper.getMainLooper()).idle()
        var ran = false
        val scope = kotlinx.coroutines.CoroutineScope(AndroidUiDispatcher.Main)
        val job = scope.launchFrame { ran = true }
        val callback = dispatcher.javaClass.getDeclaredField("dispatchCallback").apply { isAccessible = true }.get(dispatcher)
        val handler = dispatcher.javaClass.getDeclaredField("handler").apply { isAccessible = true }.get(dispatcher) as Handler
        (callback as Runnable).run()
        handler.removeCallbacks(callback)
        dispatcher.choreographer.removeFrameCallback(callback as Choreographer.FrameCallback)
        try {
            ComposeUiDispatcherResetRule().apply(object : Statement() {
                override fun evaluate() {
                    repeat(10) { shadowOf(Looper.getMainLooper()).runOneTask() }
                    assertTrue("retained frame must resume without a new frame request", ran)
                }
            }, Description.EMPTY).evaluate()
        } finally {
            job.cancel()
            try {
                net.bible.test.resetComposeUiDispatcher()
                repeat(10) { shadowOf(Looper.getMainLooper()).runOneTask() }
            } finally {
                scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            }
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchFrame(onFrame: () -> Unit) = launch {
        androidx.compose.runtime.withFrameNanos { onFrame() }
    }

    @Test fun retainedWorkRunsWithoutAnotherDispatchAfterTheLooperDropsItsCallback() {
        val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as AndroidUiDispatcher
        fun field(name: String): Any = dispatcher.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(dispatcher)
        net.bible.test.resetComposeUiDispatcher()
        shadowOf(Looper.getMainLooper()).idle()
        var ran = false
        dispatcher.dispatch(EmptyCoroutineContext, Runnable { ran = true })
        val callback = field("dispatchCallback")
        (field("handler") as Handler).removeCallbacks(callback as Runnable)
        (field("choreographer") as Choreographer).removeFrameCallback(callback as Choreographer.FrameCallback)
        try {
            ComposeUiDispatcherResetRule().apply(object : Statement() {
                override fun evaluate() {
                    repeat(10) { shadowOf(Looper.getMainLooper()).runOneTask() }
                    assertTrue("retained dispatcher work must resume without a new dispatch", ran)
                }
            }, Description.EMPTY).evaluate()
        } finally {
            net.bible.test.resetComposeUiDispatcher()
            repeat(10) { shadowOf(Looper.getMainLooper()).runOneTask() }
        }
    }
}
