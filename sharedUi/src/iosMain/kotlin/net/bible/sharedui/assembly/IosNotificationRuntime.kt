@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.assembly

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.NativeNotificationPolicy
import net.bible.android.control.activity.SystemNotificationsPreference
import net.bible.android.control.activity.monotonicActivityClock
import net.bible.sharedcore.platform.AppForeground
import net.bible.sharedcore.platform.NativeNotifier
import platform.Foundation.NSThread
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync

/** Owns the policy and UIKit observers for exactly one Koin graph. */
internal class IosNotificationRuntime(
    center: ActivityCenter,
    notifier: NativeNotifier,
    requests: ActivityListRequests,
    preference: SystemNotificationsPreference,
    observerFactory: IosForegroundObserverFactory = IosForegroundObserverFactory { IosNotificationForegroundObserver(onChanged = it) },
    finishedText: () -> String,
) {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = atomic(false)
    private val closed = atomic(false)
    val policy = NativeNotificationPolicy(
        items = center.items,
        notifier = notifier,
        foreground = AppForeground { active.value },
        enabled = preference.enabled,
        clock = monotonicActivityClock(),
        scope = scope,
        finishedText = finishedText,
    )
    private val observer: AutoCloseable

    init {
        notifier.setOnActivated { if (!closed.value) requests.request() }
        var installed: AutoCloseable? = null
        onMainSync {
            active.value = isIosNotificationForeground()
            installed = observerFactory.observe {
                if (!closed.value) {
                    active.value = isIosNotificationForeground()
                    policy.onForegroundChanged(active.value)
                }
            }
        }
        observer = checkNotNull(installed)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        policy.close()
        scope.cancel()
        onMainSync {
            observer.close()
            IosNotificationBootstrap.activations.detach()
        }
    }
}

private fun onMainSync(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_sync(dispatch_get_main_queue()) { block() }
}
