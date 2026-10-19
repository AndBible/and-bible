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

    val backgroundRefresh: BackgroundRefresh by lazy { IosBackgroundRefresh(MainScope()) }

    /** Called before the Swift host finishes launching; the handler is a no-op until Koin has an [IosBackgroundSync]. */
    fun registerBackgroundTasks() = backgroundRefresh.register { iosBackgroundRefreshEntry() }

    /** Requests the first background refresh; `MainViewController` calls it (off the main thread) once startup is Ready. No-op before Koin starts. */
    fun scheduleBackgroundSync() {
        KoinPlatform.getKoinOrNull()?.getOrNull<IosBackgroundSync>()?.schedule()
    }
}
