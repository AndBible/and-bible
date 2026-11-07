package net.bible.android.control.activity

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.service.cloudsync.host.HostErrorReportHooks

class HostActivityReporter(private val scope: CoroutineScope) : ActivityReporter {
    private val reporting = atomic(false)
    override val available get() = HostErrorReportHooks.available

    override fun report(item: ActivityItem) {
        val hook = HostErrorReportHooks.report ?: return
        if (!reporting.compareAndSet(false, true)) return
        try {
            scope.launch { hook(ActivityFailureReport(item)) }
                .invokeOnCompletion { reporting.value = false }
        } catch (error: Throwable) {
            reporting.value = false
            throw error
        }
    }
}
