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
package net.bible.service.cloudsync.ios

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import net.bible.service.cloudsync.BackgroundSyncPolicy
import net.bible.service.cloudsync.NetworkRestoreEdge
import net.bible.service.cloudsync.SyncTriggerDriver
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The UIKit notifications reach the shared driver (#4025). Notifications are posted on this (main) thread. */
class IosSyncTriggersTest {
    private fun driver(scope: CoroutineScope) = SyncTriggerDriver(
        policy = BackgroundSyncPolicy(
            syncEnabled = { false }, replacing = { false }, intervalSeconds = { 300 }, lastSynchronizedMs = { 0 },
            nowMs = { 0 }, signedIn = { true }, signIn = { true }, startSync = { CompletableDeferred(true) },
        ),
        scope = scope,
    )

    @Test fun startInTheForegroundOpensAndNotificationsDriveTheTriggers() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val d = driver(scope)
        val triggers = IosSyncTriggers(d, NetworkRestoreEdge(), launchedInBackground = { false })
        try {
            triggers.start()
            assertTrue(d.isForeground, "Ready in the foreground is the open trigger")
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
            assertFalse(d.isForeground, "didEnterBackground is the leave trigger")
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationWillEnterForegroundNotification, null)
            assertTrue(d.isForeground, "willEnterForeground is the resume trigger")
        } finally {
            triggers.stop(); scope.cancel()
        }
    }

    @Test fun aBackgroundLaunchWaitsForWillEnterForeground() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val d = driver(scope)
        val triggers = IosSyncTriggers(d, NetworkRestoreEdge(), launchedInBackground = { true })
        try {
            triggers.start()
            assertFalse(d.isForeground)
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationWillEnterForegroundNotification, null)
            assertTrue(d.isForeground)
        } finally {
            triggers.stop(); scope.cancel()
        }
    }

    @Test fun stopRemovesTheObservers() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val d = driver(scope)
        val triggers = IosSyncTriggers(d, NetworkRestoreEdge(), launchedInBackground = { true })
        try {
            triggers.start(); triggers.stop()
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationWillEnterForegroundNotification, null)
            assertFalse(d.isForeground)
        } finally {
            scope.cancel()
        }
    }
}
