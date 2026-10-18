package net.bible.sharedui.platform

import kotlinx.coroutines.MainScope
import net.bible.service.cloudsync.ios.IosBackgroundSync
import net.bible.service.cloudsync.ios.iosBackgroundRefreshEntry
import net.bible.sharedcore.platform.sync.BackgroundRefresh
import net.bible.sharedcore.platform.sync.IosBackgroundRefresh
import org.koin.mp.KoinPlatform

object IosPlatformBootstrap {
    val backgroundRefresh: BackgroundRefresh by lazy { IosBackgroundRefresh(MainScope()) }

    /** Called before the Swift host finishes launching; the handler is a no-op until Koin has an [IosBackgroundSync]. */
    fun registerBackgroundTasks() = backgroundRefresh.register { iosBackgroundRefreshEntry() }

    /** Requests the first background refresh; `MainViewController` calls it (off the main thread) once startup is Ready. No-op before Koin starts. */
    fun scheduleBackgroundSync() {
        KoinPlatform.getKoinOrNull()?.getOrNull<IosBackgroundSync>()?.schedule()
    }
}
