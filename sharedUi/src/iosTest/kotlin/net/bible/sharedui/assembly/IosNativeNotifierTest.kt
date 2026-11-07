@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.assembly

import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.control.activity.ActivityListRequests
import net.bible.sharedcore.platform.NativeNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosNativeNotifierTest {
    private class Center : UserNotificationCenterApi {
        val added = mutableListOf<List<Any>>()
        val delivered = mutableListOf<String>()
        val pending = mutableListOf<String>()
        override fun add(id: String, title: String, body: String, sound: Boolean) {
            added += listOf(id, title, body, sound)
        }
        override fun removeDelivered(id: String) { delivered += id }
        override fun removePending(id: String) { pending += id }
    }

    @Test
    fun asynchronousAddErrorsAreLoggedOnlyOnce() {
        val messages = mutableListOf<String>()
        val completion = NotificationAddErrorLogger { messages += it }
        completion.onComplete(null)
        completion.onComplete("first")
        completion.onComplete("second")
        assertEquals(listOf("first"), messages)
    }

    @Test
    fun showUsesStableIdAndOnlyUrgentSound() {
        val center = Center()
        val notifier = IosNativeNotifier(center)
        assertFalse(notifier.supportsProgress)
        notifier.show(NativeNotification("job", "First", "Working", null, false, false))
        notifier.show(NativeNotification("job", "Failed", "Error", null, false, true))
        assertEquals(listOf<List<Any>>(listOf("job", "First", "Working", false), listOf("job", "Failed", "Error", true)), center.added)
    }

    @Test
    fun removeForwardsId() {
        val center = Center()
        IosNativeNotifier(center).remove("job")
        assertEquals(listOf("job"), center.delivered)
        assertEquals(listOf("job"), center.pending)
    }

    @Test
    fun delegateTapForwardsIdentifierAndCompletes() {
        val events = mutableListOf<String>()
        val delegate = IosNotificationDelegate { events += it }
        delegate.handleTap("job") { events += "completed" }
        assertEquals(listOf("job", "completed"), events)
    }

    @Test
    fun foregroundPresentationIncludesBannerListAndSound() {
        var options: ULong? = null
        IosNotificationDelegate {}.present { options = it }
        assertEquals(
            platform.UserNotifications.UNNotificationPresentationOptionBanner or
                platform.UserNotifications.UNNotificationPresentationOptionList or
                platform.UserNotifications.UNNotificationPresentationOptionSound,
            options,
        )
    }

    @Test
    fun tapBeforeGraphAndHostIsDeliveredOnce() = awaitStartupOnMain {
        val activations = IosNotificationActivations()
        val delegate = IosNotificationDelegate { activations.activate(it) }
        delegate.handleTap("job")
        val requests = ActivityListRequests()
        IosNativeNotifier(Center(), activations).setOnActivated { requests.request() }
        kotlin.test.assertNotNull(withTimeoutOrNull(1_000) { requests.requests.first() })
        assertEquals(null, withTimeoutOrNull(10) { requests.requests.first() })
    }

    @Test
    fun tapAfterGraphRequestsActivityList() = awaitStartupOnMain {
        val activations = IosNotificationActivations()
        val requests = ActivityListRequests()
        IosNativeNotifier(Center(), activations).setOnActivated { requests.request() }
        val delegate = IosNotificationDelegate { activations.activate(it) }
        delegate.handleTap("job")
        kotlin.test.assertNotNull(withTimeoutOrNull(1_000) { requests.requests.first() })
        assertEquals(null, withTimeoutOrNull(10) { requests.requests.first() })
    }

    @Test
    fun foregroundQueryHandlesBareNativeTestProcess() = awaitStartupOnMain {
        assertFalse(isIosNotificationForeground())
    }

    @Test
    fun foregroundRequiresActiveAppAndActiveKeyWindowScene() {
        assertFalse(notificationForeground(false, true, true))
        assertFalse(notificationForeground(true, false, true))
        assertFalse(notificationForeground(true, true, false))
        kotlin.test.assertTrue(notificationForeground(true, true, true))
    }

    @Test
    fun sceneLifecycleObserverStopsAfterClose() = awaitStartupOnMain {
        var calls = 0
        val observer = IosNotificationForegroundObserver { calls++ }
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        try {
            center.postNotificationName(platform.UIKit.UISceneDidActivateNotification, null)
            assertEquals(1, calls)
            center.postNotificationName(platform.UIKit.UISceneDidEnterBackgroundNotification, null)
            assertEquals(2, calls)
        } finally {
            observer.close()
        }
        center.postNotificationName(platform.UIKit.UISceneDidActivateNotification, null)
        assertEquals(2, calls)
    }

    @Test
    fun closingForegroundObserverRemovesEveryRegisteredTokenOnlyOnce() {
        val registered = mutableListOf<platform.darwin.NSObjectProtocol>()
        val removed = mutableListOf<platform.darwin.NSObjectProtocol>()
        val observer = IosNotificationForegroundObserver(
            register = { _, _ -> platform.darwin.NSObject().also { registered += it } },
            remove = { removed += it },
            onChanged = {},
        )
        assertEquals(8, registered.size)
        observer.close()
        assertEquals(registered, removed)
        observer.close()
        assertEquals(registered, removed)
    }

    @Test
    fun closingRuntimeDetachesActivationAndStopsPolicy() = awaitStartupOnMain {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val activations = IosNotificationBootstrap.activations
        val center = Center()
        val notifier = IosNativeNotifier(center, activations)
        val requests = ActivityListRequests()
        val settings = net.bible.sharedlogic.settings.DatabaseAppSettings({ error("database not open") }, { false })
        val preference = net.bible.android.control.activity.SystemNotificationsPreference(settings, true)
        val activities = net.bible.android.control.activity.ActivityCenter(scope) { 0L }
        val runtime = IosNotificationRuntime(activities, notifier, requests, preference) { "Finished" }
        try {
            runtime.policy.start()
            activities.startOrFail("before", net.bible.android.control.activity.ActivityKind.DOWNLOAD, "Download", "Error")
            kotlinx.coroutines.delay(20)
            assertEquals(1, center.added.size)
            runtime.close()
            activations.activate("after-close")
            assertEquals(null, withTimeoutOrNull(20) { requests.requests.first() })
            activities.startOrFail("failed", net.bible.android.control.activity.ActivityKind.DOWNLOAD, "Download", "Error")
            kotlinx.coroutines.delay(20)
            assertEquals(1, center.added.size)
        } finally {
            runtime.close()
            activations.attach { }
            activations.detach()
            scope.cancel()
        }
    }

    @Test
    fun closingOldRuntimeKeepsNewRuntimeActivationHandler() = awaitStartupOnMain {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val activations = IosNotificationBootstrap.activations
        val settings = net.bible.sharedlogic.settings.DatabaseAppSettings({ error("database not open") }, { false })
        val preference = net.bible.android.control.activity.SystemNotificationsPreference(settings, true)
        val activities = net.bible.android.control.activity.ActivityCenter(scope) { 0L }
        val first = IosNotificationRuntime(activities, IosNativeNotifier(Center(), activations), ActivityListRequests(), preference) { "Finished" }
        val requests = ActivityListRequests()
        val second = IosNotificationRuntime(activities, IosNativeNotifier(Center(), activations), requests, preference) { "Finished" }
        try {
            first.close()
            activations.activate("new-graph")
            kotlin.test.assertNotNull(withTimeoutOrNull(1_000) { requests.requests.first() })
        } finally {
            first.close()
            second.close()
            scope.cancel()
        }
    }

    @Test
    fun foregroundInstallationUpdatesAlreadyStartedPolicy() = awaitStartupOnMain {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val settings = net.bible.sharedlogic.settings.DatabaseAppSettings({ error("database not open") }, { false })
        val preference = net.bible.android.control.activity.SystemNotificationsPreference(settings, true)
        val activities = net.bible.android.control.activity.ActivityCenter(scope) { 0L }
        val center = Center()
        val ready = platform.darwin.dispatch_semaphore_create(0)
        val worker = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).async {
            val runtime = IosNotificationRuntime(
                activities, IosNativeNotifier(center), ActivityListRequests(), preference,
                observerFactory = IosForegroundObserverFactory { AutoCloseable {} },
                foregroundQuery = { true },
                finishedText = { "Finished" },
            )
            runtime.policy.start()
            platform.darwin.dispatch_semaphore_signal(ready)
            runtime
        }
        val returned = platform.darwin.dispatch_semaphore_wait(
            ready, platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 1_000_000_000),
        ) == 0L
        val runtime = worker.await()
        try {
            kotlin.test.assertTrue(returned)
            kotlinx.coroutines.delay(50)
            activities.startOrFail("foreground", net.bible.android.control.activity.ActivityKind.DOWNLOAD,
                "Download", "Error", backgroundFailure = false)
            kotlinx.coroutines.delay(50)
            assertEquals(0, center.added.size)
        } finally {
            runtime.close()
            scope.cancel()
        }
    }

    @Test
    fun backgroundConstructionAndCloseDoNotWaitForMainOrInstallAfterClose() = awaitStartupOnMain {
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
        val settings = net.bible.sharedlogic.settings.DatabaseAppSettings({ error("database not open") }, { false })
        val preference = net.bible.android.control.activity.SystemNotificationsPreference(settings, true)
        val activities = net.bible.android.control.activity.ActivityCenter(scope) { 0L }
        val semaphore = platform.darwin.dispatch_semaphore_create(0)
        var installed = 0
        val worker = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).async {
            try {
                val runtime = IosNotificationRuntime(
                    activities, IosNativeNotifier(Center()), ActivityListRequests(), preference,
                    observerFactory = IosForegroundObserverFactory { installed++; AutoCloseable {} },
                    finishedText = { "Finished" },
                )
                runtime.close()
            } finally {
                platform.darwin.dispatch_semaphore_signal(semaphore)
            }
        }
        try {
            // Keep Main unavailable until construction and close have had time to return.
            val returned = platform.darwin.dispatch_semaphore_wait(
                semaphore, platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW, 1_000_000_000),
            ) == 0L
            worker.await()
            kotlinx.coroutines.delay(50)
            kotlin.test.assertTrue(returned, "Background lifecycle must not wait for Main")
            assertEquals(0, installed, "A closed runtime must not install an observer later")
        } finally {
            worker.cancel()
            scope.cancel()
        }
    }

    @Test
    fun centerFailuresDoNotEscape() {
        val center = object : UserNotificationCenterApi {
            override fun add(id: String, title: String, body: String, sound: Boolean) { error("add") }
            override fun removeDelivered(id: String) { error("remove") }
            override fun removePending(id: String) { error("remove") }
        }
        val notifier = IosNativeNotifier(center)
        notifier.show(NativeNotification("job", "Title", "Body", null, false, false))
        notifier.remove("job")
    }
}
