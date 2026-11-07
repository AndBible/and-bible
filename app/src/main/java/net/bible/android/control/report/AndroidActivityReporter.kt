package net.bible.android.control.report

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    override fun report(item: ActivityItem) {
        val activity = currentActivity()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w("AndroidActivityReporter", "Skipping activity report: no live activity")
            return
        }
        if (!reporting.compareAndSet(false, true)) return
        try {
            scope().launch {
                if (activity.isFinishing || activity.isDestroyed) {
                    Log.w("AndroidActivityReporter", "Skipping activity report: captured activity is no longer live")
                } else {
                    dispatch(activity, ActivityFailureReport(item), "activity list")
                }
            }.invokeOnCompletion { reporting.value = false }
        } catch (error: Throwable) {
            reporting.value = false
            throw error
        }
    }
}
