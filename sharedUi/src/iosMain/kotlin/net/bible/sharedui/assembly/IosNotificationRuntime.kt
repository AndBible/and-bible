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
import platform.darwin.dispatch_async

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
    private var observer: AutoCloseable? = null
    private val activationHandler: (String) -> Unit = { if (!closed.value) requests.request() }

    init {
        onMain {
            if (!closed.value) {
                notifier.setOnActivated(activationHandler)
                active.value = isIosNotificationForeground()
                observer = observerFactory.observe {
                    if (!closed.value) {
                        active.value = isIosNotificationForeground()
                        policy.onForegroundChanged(active.value)
                    }
                }
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        policy.close()
        scope.cancel()
        onMain {
            observer?.close()
            observer = null
            if (IosNotificationBootstrap.activations.handler === activationHandler) {
                IosNotificationBootstrap.activations.detach()
            }
        }
    }
}

private fun onMain(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
}
