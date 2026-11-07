@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.assembly

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.darwin.NSObject
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal interface UserNotificationCenterApi {
    fun add(id: String, title: String, body: String, sound: Boolean)
    fun removeDelivered(id: String)
    fun removePending(id: String)
}

internal object SystemUserNotificationCenter : UserNotificationCenterApi {
    override fun add(id: String, title: String, body: String, sound: Boolean) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            if (sound) setSound(UNNotificationSound.defaultSound)
        }
        val request = UNNotificationRequest.requestWithIdentifier(id, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) Log.e("IosNativeNotifier", "Notification request failed: $error")
        }
    }

    override fun removeDelivered(id: String) {
        UNUserNotificationCenter.currentNotificationCenter().removeDeliveredNotificationsWithIdentifiers(listOf(id))
    }

    override fun removePending(id: String) {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }
}

@kotlin.native.HiddenFromObjC
internal class IosNativeNotifier(
    internal val center: UserNotificationCenterApi = SystemUserNotificationCenter,
    private val activations: IosNotificationActivations = IosNotificationBootstrap.activations,
) : NativeNotifier {
    override val supportsProgress = false
    override fun show(n: NativeNotification) {
        runCatching { center.add(n.id, n.title, n.body, n.urgent) }
            .onFailure { Log.e("IosNativeNotifier", "Could not show notification", it) }
    }

    override fun remove(id: String) {
        runCatching { center.removeDelivered(id) }
            .onFailure { Log.e("IosNativeNotifier", "Could not remove delivered notification", it) }
        runCatching { center.removePending(id) }
            .onFailure { Log.e("IosNativeNotifier", "Could not remove pending notification", it) }
    }

    override fun setOnActivated(handler: (String) -> Unit) {
        activations.attach(handler)
    }
}

/** Main-queue handoff keeps a tap received before the graph alive until its notifier attaches. */
internal class IosNotificationActivations {
    private var pending: String? = null
    internal var handler: ((String) -> Unit)? = null
        private set

    fun activate(id: String) = onMain {
        val current = handler
        if (current == null) pending = id else current(id)
    }

    fun attach(callback: (String) -> Unit) = onMain {
        handler = callback
        pending?.let { id -> pending = null; callback(id) }
    }

    fun detach() = onMain { handler = null }

    private fun onMain(block: () -> Unit) {
        if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
    }
}

@kotlin.native.HiddenFromObjC
internal class IosNotificationDelegate(private val onTap: (String) -> Unit) : NSObject(), UNUserNotificationCenterDelegateProtocol {
    internal fun handleTap(id: String, completion: () -> Unit = {}) {
        try { onTap(id) } finally { completion() }
    }

    internal fun present(completion: (ULong) -> Unit) {
        completion(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (ULong) -> Unit,
    ) {
        present(withCompletionHandler)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        handleTap(didReceiveNotificationResponse.notification.request.identifier, withCompletionHandler)
    }
}

internal object IosNotificationBootstrap {
    val activations = IosNotificationActivations()
    private val delegate = IosNotificationDelegate(activations::activate)
    private var installed = false

    fun install() {
        if (installed) return
        UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
        installed = true
    }
}
