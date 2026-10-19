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
    /** Swift's Files/Mail entry; buffers until the real navigation graph has composed. */
    fun openIncomingFile(url: String) {
        if (NSThread.isMainThread) IosIncomingFiles.offer(url)
        else dispatch_async(dispatch_get_main_queue()) { IosIncomingFiles.offer(url) }
    }

    val backgroundRefresh: BackgroundRefresh by lazy { IosBackgroundRefresh(MainScope()) }

    /** Called before the Swift host finishes launching; the handler is a no-op until Koin has an [IosBackgroundSync]. */
    fun registerBackgroundTasks() = backgroundRefresh.register { iosBackgroundRefreshEntry() }

    /** Requests the first background refresh; `MainViewController` calls it (off the main thread) once startup is Ready. No-op before Koin starts. */
    fun scheduleBackgroundSync() {
        KoinPlatform.getKoinOrNull()?.getOrNull<IosBackgroundSync>()?.schedule()
    }
}
