package net.bible.sharedui.platform

import kotlinx.coroutines.MainScope
import net.bible.service.cloudsync.ios.IosBackgroundSync
import net.bible.service.cloudsync.ios.iosBackgroundRefreshEntry
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.IosBackgroundRefresh
import org.koin.mp.KoinPlatform

object IosPlatformBootstrap {
    val backgroundRefresh: BackgroundRefresh by lazy { IosBackgroundRefresh(MainScope()) }

    private var crashObservers: List<platform.darwin.NSObjectProtocol> = emptyList()

    /** Installed before any gate, independently of the reading workspace saver. Background kills are clean. */
    internal fun registerCrashLifecycle(tracker: net.bible.sharedui.ios.startup.IosCrashTracker) {
        if (crashObservers.isNotEmpty()) return
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        crashObservers = listOf(
            platform.UIKit.UIApplicationDidEnterBackgroundNotification,
            platform.UIKit.UIApplicationWillTerminateNotification,
        ).map { name ->
            center.addObserverForName(name, null, platform.Foundation.NSOperationQueue.mainQueue) { tracker.onCleanExit() }
        } + center.addObserverForName(
            platform.UIKit.UIApplicationWillEnterForegroundNotification, null, platform.Foundation.NSOperationQueue.mainQueue,
        ) { tracker.onForeground() }
    }

    /** Called before the Swift host finishes launching; the handler is a no-op until Koin has an [IosBackgroundSync]. */
    fun registerBackgroundTasks() = backgroundRefresh.register { iosBackgroundRefreshEntry() }

    /** Requests the first background refresh; `MainViewController` calls it (off the main thread) once startup is Ready. No-op before Koin starts. */
    fun scheduleBackgroundSync() {
        KoinPlatform.getKoinOrNull()?.getOrNull<IosBackgroundSync>()?.schedule()
    }
}
