package net.bible.android.control.report

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import net.bible.android.control.activity.ActivityFailureReport
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityReporter
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.sharedcore.log.Log

class AndroidActivityReporter(
    private val scope: () -> CoroutineScope,
    private val currentActivity: () -> ActivityBase? = { CurrentActivityHolder.currentActivity },
    private val dispatch: suspend (ActivityBase, Throwable, String) -> Unit = { activity, error, source ->
        BugReport.reportBug(context_ = activity, exception = error, source = source)
    },
) : ActivityReporter {
    private val reporting = atomic(false)

    private fun isLive(activity: ActivityBase?) = activity != null &&
        !activity.isFinishing && !activity.isDestroyed &&
        activity.lifecycle.currentState != Lifecycle.State.DESTROYED

    @OptIn(ExperimentalCoroutinesApi::class)
    override val available: StateFlow<Boolean> = CurrentActivityHolder.currentActivityChanges.flatMapLatest {
        callbackFlow {
            val activity = currentActivity()
            fun update() { trySend(isLive(currentActivity())) }
            val observer = LifecycleEventObserver { _, _ -> update() }
            activity?.lifecycle?.addObserver(observer)
            update()
            awaitClose { activity?.lifecycle?.removeObserver(observer) }
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Main.immediate)
        .stateIn(scope(), SharingStarted.Eagerly, false)

    override fun report(item: ActivityItem) {
        val activity = currentActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w("AndroidActivityReporter", "Skipping activity report: no live activity")
            return
        }
        if (!reporting.compareAndSet(false, true)) return
        try {
            scope().launch {
                withContext(Dispatchers.Main.immediate) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) cancel()
                    }
                    activity.lifecycle.addObserver(observer)
                    try {
                        if (activity.isFinishing || activity.isDestroyed ||
                            activity.lifecycle.currentState == Lifecycle.State.DESTROYED
                        ) {
                            Log.w("AndroidActivityReporter", "Skipping activity report: captured activity is no longer live")
                        } else {
                            dispatch(activity, ActivityFailureReport(item), "activity list")
                        }
                    } finally {
                        activity.lifecycle.removeObserver(observer)
                    }
                }
            }.invokeOnCompletion { reporting.value = false }
        } catch (error: Throwable) {
            reporting.value = false
            throw error
        }
    }
}
