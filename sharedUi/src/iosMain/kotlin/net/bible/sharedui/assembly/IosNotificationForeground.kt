@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.assembly

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneDidActivateNotification
import platform.UIKit.UISceneDidEnterBackgroundNotification
import platform.UIKit.UISceneWillDeactivateNotification
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowDidBecomeKeyNotification
import platform.UIKit.UIWindowDidResignKeyNotification
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.atomicfu.atomic

internal fun notificationForeground(appActive: Boolean, sceneActive: Boolean, keyWindow: Boolean): Boolean =
    appActive && sceneActive && keyWindow

/** UIKit must be read on Main; only an active scene with a key window counts as foreground. */
internal fun isIosNotificationForeground(): Boolean {
    val app: UIApplication? = UIApplication.sharedApplication
    val scenes: Set<*> = app?.connectedScenes ?: return false
    val active = app.applicationState == UIApplicationState.UIApplicationStateActive
    return scenes.filterIsInstance<UIWindowScene>().any { scene ->
        notificationForeground(active, scene.activationState == UISceneActivationStateForegroundActive,
            scene.windows.filterIsInstance<UIWindow>().any { it.isKeyWindow() })
    }
}

internal fun interface IosForegroundObserverFactory {
    fun observe(onChanged: () -> Unit): AutoCloseable
}

internal class IosNotificationForegroundObserver(onChanged: () -> Unit) : AutoCloseable {
    private val center = NSNotificationCenter.defaultCenter
    private val closed = atomic(false)
    private val observers = listOf(
        UIApplicationDidBecomeActiveNotification,
        UIApplicationWillResignActiveNotification,
        UIApplicationDidEnterBackgroundNotification,
        UISceneDidActivateNotification,
        UISceneWillDeactivateNotification,
        UISceneDidEnterBackgroundNotification,
        UIWindowDidBecomeKeyNotification,
        UIWindowDidResignKeyNotification,
    ).map { name -> center.addObserverForName(name, null, NSOperationQueue.mainQueue) {
        if (!closed.value) onChanged()
        // Will-deactivate notifications can precede UIKit's state update.
        dispatch_async(dispatch_get_main_queue()) { if (!closed.value) onChanged() }
    } }

    override fun close() {
        closed.value = true
        observers.forEach { center.removeObserver(it) }
    }
}
