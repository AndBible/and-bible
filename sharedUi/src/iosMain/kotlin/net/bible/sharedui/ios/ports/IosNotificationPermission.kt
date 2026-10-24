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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.ports.NotificationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.UserNotifications.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@kotlin.native.HiddenFromObjC
interface IosNotificationCenter {
    fun authorizationDecided(completion: (Boolean) -> Unit)
    fun requestAlertAndSound(completion: () -> Unit)
}

private object SystemNotificationCenter : IosNotificationCenter {
    override fun authorizationDecided(completion: (Boolean) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler {
            completion(it?.authorizationStatus != UNAuthorizationStatusNotDetermined)
        }
    }
    override fun requestAlertAndSound(completion: () -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> completion() }
    }
}

/** Serializes concurrent callers through both the status query and the system prompt. */
@kotlin.native.HiddenFromObjC
class IosNotificationPermission(
    private val center: IosNotificationCenter = SystemNotificationCenter,
) : NotificationPermission {
    private val mutex = Mutex()
    private var completed = false
    override suspend fun request() = withContext(Dispatchers.Main) {
        mutex.withLock {
            if (!completed) {
                // The OS prompt cannot be cancelled: retain the lock until its callback arrives.
                val decided = suspendCoroutine { continuation -> center.authorizationDecided { continuation.resume(it) } }
                if (!decided) suspendCoroutine { continuation ->
                    center.requestAlertAndSound { continuation.resume(Unit) }
                }
                completed = true
            }
        }
    }
}
