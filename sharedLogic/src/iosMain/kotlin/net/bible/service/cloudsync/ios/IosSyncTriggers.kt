/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.NetworkRestoreEdge
import net.bible.service.cloudsync.SyncBackgroundAllowance
import net.bible.service.cloudsync.SyncTriggerDriver
import net.bible.sharedcore.log.Log
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_get_main_queue

/** The leave-the-app allowance: a UIKit background task named "sync-leave", begun synchronously in the notification. */
internal val uiKitLeaveAllowance = SyncBackgroundAllowance { onExpire ->
    val id = UiKitBackgroundTask.begin("sync-leave")(onExpire)
    return@SyncBackgroundAllowance { if (id != UIBackgroundTaskInvalid) UiKitBackgroundTask.end(id) }
}

/**
 * Connects the shared [SyncTriggerDriver] to iOS (#4025): `willEnterForeground` is the open/resume trigger,
 * `didEnterBackground` the leave trigger (its pass runs inside [uiKitLeaveAllowance]), and an `NWPathMonitor` reports
 * network restores into [network]. `didBecomeActive` is not used: it also fires after a Control Centre pull-down,
 * which is not a return to the app.
 *
 * [start] is called once, on the main thread, when startup is Ready; the app is in the foreground then unless it was
 * launched into the background (a BGTask launch, which has its own refresh path), so the open pass runs at once.
 */
class IosSyncTriggers(
    private val driver: SyncTriggerDriver,
    private val network: NetworkRestoreEdge,
    private val launchedInBackground: () -> Boolean = {
        UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground
    },
) {
    private var observers: List<NSObjectProtocol> = emptyList()
    private val monitor = nw_path_monitor_create()
    private var started = false

    fun start() {
        if (started) return
        started = true
        val center = NSNotificationCenter.defaultCenter
        observers = listOf(
            center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) {
                driver.onForeground()
            },
            center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
                driver.onBackground()
            },
        )
        val edge = network
        val triggers = driver
        nw_path_monitor_set_update_handler(monitor) { path ->
            if (path != null && edge.report(nw_path_get_status(path) == nw_path_status_satisfied)) triggers.onNetworkAvailable()
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
        if (!launchedInBackground()) {
            driver.onForeground()
        } else {
            Log.i(TAG, "Started in the background; the open pass waits for willEnterForeground")
        }
    }

    /** Removes the observers and the monitor and stops the timer (tests and teardown). */
    fun stop() {
        if (!started) return
        started = false
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers = emptyList()
        nw_path_monitor_cancel(monitor)
        driver.stop()
    }

    private companion object { const val TAG = "IosSyncTriggers" }
}
