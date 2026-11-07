package net.bible.test

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.robolectric.android.controller.ActivityController

/**
 * Closes the activity a `runTest` built and waits, still inside `runTest`, until every coroutine of its
 * `lifecycleScope` (a BibleView session among them) has finished.
 *
 * Closing only cancels that work. A cancelled coroutine that is inside a blocking IO call (a Room read, say)
 * finishes later on its IO thread and then resumes on `Dispatchers.Main`. While a test has `Dispatchers.setMain`
 * on a `StandardTestDispatcher`, that resumption is queued on the test's scheduler, and `runTest` drains that
 * scheduler only until its body returns. One that lands later is never run, so the lifecycle job stays
 * cancelling forever and `DatabaseResetter`, which joins the lifecycle scope of a still-registered activity,
 * times out at `join-producer` (#4020). Joining here lets `runTest` run those resumptions.
 *
 * Call it from the `runTest` body (typically its `finally`), never after `runTest` returned. Fails, naming the
 * unfinished coroutines, if the work does not end within [timeout] (real time).
 */
suspend fun <T> ActivityController<T>.closeAndJoinLifecycle(timeout: Duration = 10.seconds)
    where T : android.app.Activity, T : LifecycleOwner {
    val lifecycleJob = get().lifecycleScope.coroutineContext[Job]!!
    close()
    // DESTROYED cancels the scope, but a scope created late registers its observer through a Main dispatch that a
    // StandardTestDispatcher has not run yet, so it would stay active: cancel it as the destruction would.
    lifecycleJob.cancel()
    // Waits in real time off the test scheduler (virtual time would expire at once while IO runs); runTest keeps
    // running its scheduler meanwhile. NonCancellable: also from the finally of a failed or timed-out body.
    val joined = withContext(NonCancellable + Dispatchers.Default) { withTimeoutOrNull(timeout) { lifecycleJob.join(); true } }
    check(joined == true) {
        "Host lifecycle work did not finish within $timeout of the close; " +
            DatabaseResetter.ResetDecision().apply { diagnosticJob = lifecycleJob }.diagnostic()
    }
}
