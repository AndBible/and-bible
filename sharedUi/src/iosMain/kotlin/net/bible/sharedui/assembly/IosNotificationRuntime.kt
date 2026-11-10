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
import net.bible.service.cloudsync.documents.DocumentSyncProgressTracker
import net.bible.sharedcore.cloud.DocumentSyncProgress
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
    private val foregroundQuery: () -> Boolean = ::isIosNotificationForeground,
    attentionText: (() -> String)? = null,
    tracker: DocumentSyncProgressTracker? = null,
    documentSyncNoticeText: ((DocumentSyncProgress) -> String)? = null,
    documentSyncNoticeTitle: (() -> String)? = null,
    policyScope: CoroutineScope? = null,
    clock: () -> Long = monotonicActivityClock(),
    finishedText: () -> String,
) {
    internal val scope = policyScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = atomic(false)
    private val closed = atomic(false)
    val policy = NativeNotificationPolicy(
        items = center.items,
        notifier = notifier,
        foreground = AppForeground { active.value },
        enabled = preference.enabled,
        clock = clock,
        scope = scope,
        attentionText = attentionText,
        finishedText = finishedText,
        documentSyncLifecycleNotices = true,
        documentSyncNoticeText = documentSyncNoticeText,
        documentSyncNoticeTitle = documentSyncNoticeTitle,
        notificationPreference = preference,
    )
    private val progressSubscription = tracker?.observe(policy::onDocumentSyncProgress)
    private var observer: AutoCloseable? = null
    private val activationHandler: (String) -> Unit = { if (!closed.value) requests.request() }

    init {
        onMain {
            if (!closed.value) {
                notifier.setOnActivated(activationHandler)
                active.value = foregroundQuery()
                policy.onForegroundChanged(active.value)
                observer = observerFactory.observe {
                    if (!closed.value) {
                        active.value = foregroundQuery()
                        policy.onForegroundChanged(active.value)
                    }
                }
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        progressSubscription?.cancel()
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
