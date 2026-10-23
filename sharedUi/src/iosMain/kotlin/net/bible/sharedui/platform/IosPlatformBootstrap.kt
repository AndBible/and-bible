@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.platform

import kotlinx.coroutines.MainScope
import net.bible.service.cloudsync.ios.IosBackgroundSync
import net.bible.service.cloudsync.ios.iosBackgroundRefreshEntry
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.IosBackgroundRefresh
import org.koin.mp.KoinPlatform
import net.bible.sharedui.ios.files.IosIncomingFiles
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

object IosPlatformBootstrap {
    /** Debug-only app-hosted port diagnostics; release builds ignore launch arguments. */
    fun prepareFilesPortTestLaunch() {
        net.bible.sharedui.ios.files.IosFilesRuntimeHarness.prepareLaunch()
        net.bible.sharedui.ios.files.IosIncomingRuntimeHarness.prepareLaunch()
        net.bible.sharedui.ios.files.IosFilesFeatureHarness.prepareLaunch()
    }

    /** Null on success; an explicit diagnostic otherwise, including unsupported release builds. */
    fun runFilesClipboardSelfTest(): String? = net.bible.sharedui.ios.files.IosFilesRuntimeHarness.clipboardSelfTest()

    /** Swift's Files/Mail entry; buffers until the real navigation graph has composed. */
    fun openIncomingFile(url: String) {
        val receive = {
            net.bible.sharedui.ios.files.IosOsRoutingReceipt.observe(url)
            IosIncomingFiles.offer(url)
        }
        if (NSThread.isMainThread) receive()
        else dispatch_async(dispatch_get_main_queue()) { receive() }
    }

    /** Shared with the UI, confined to Main; background checks never mutate its launch accounting. */
    internal val crashTracker by lazy { net.bible.sharedui.ios.startup.IosCrashTracker(net.bible.sharedcore.platform.IosDeviceSettings()) }
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

    /** Registered before launch ends. Admission is pre-Koin/pre-database and never mutates crash accounting. */
    fun registerBackgroundTasks() = backgroundRefresh.register {
        iosBackgroundRefreshEntry(
            protectedDataAvailable = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    net.bible.sharedui.ios.startup.backgroundAdmissionAllowed(
                        platform.UIKit.UIApplication.sharedApplication.protectedDataAvailable,
                        crashTracker.wouldOfferRecoveryAfterLaunch(),
                        net.bible.sharedui.ios.startup.legacySettled(net.bible.sharedcore.platform.IosDeviceSettings()),
                    )
                }
            },
            start = { net.bible.sharedui.assembly.IosAppStartup.ensureStarted() },
            openDatabases = { net.bible.sharedui.assembly.IosAppStartup.openDatabases() },
            refresh = backgroundRefresh,
        )
    }

    /** Requests the first background refresh; `MainViewController` calls it (off the main thread) once startup is Ready. No-op before Koin starts. */
    fun scheduleBackgroundSync() {
        KoinPlatform.getKoinOrNull()?.getOrNull<IosBackgroundSync>()?.schedule()
    }
}
