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

package net.bible.sharedlogic.platform.ios

import kotlinx.coroutines.delay
import net.bible.service.llm.agent.AgentPlatform
import net.bible.service.llm.agent.ToolPermissionAnswer
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.event.Events
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.UserNotifier
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSThread
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.darwin.NSObjectProtocol
import kotlin.concurrent.Volatile

/**
 * iOS [AgentPlatform]. The foreground state is the `UIApplication` state, kept in a flag that
 * `UIApplicationDidBecomeActive` / `UIApplicationDidEnterBackground` update, so any thread can read it
 * ([hasForegroundUi] reads the live state itself when it runs on Main).
 *
 * C7 pending (phase 2: route through the shared root): the tool-permission and question dialogs are not drawn yet.
 * [askToolPermission] answers [ToolPermissionAnswer.DENY] and [askQuestion] answers `false`, each logged with tag
 * `C7-pending`, so an agent tool that needs approval fails closed instead of running unasked.
 *
 * [liveActive] and [center] exist so tests can drive the platform without a running UIApplication.
 */
class IosAgentPlatform(
    private val notifier: UserNotifier,
    private val center: NSNotificationCenter = NSNotificationCenter.defaultCenter,
    private val liveActive: () -> Boolean = { UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive },
) : AgentPlatform {
    private val source = EventSource<Boolean>()

    @Volatile private var active: Boolean = if (NSThread.isMainThread) liveActive() else false

    private val observers: List<NSObjectProtocol> = listOf(
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, null) { change(true) },
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, null) { change(false) },
    )

    private fun change(isActive: Boolean) {
        active = isActive
        source.emit(isActive)
    }

    /** Stops observing; the app-wide instance never calls it, tests do. */
    fun close() = observers.forEach(center::removeObserver)

    override val hasForegroundUi: Boolean
        get() = if (NSThread.isMainThread) liveActive().also { active = it } else active

    override val foregroundChanges: Events<Boolean> get() = source

    /**
     * Polls like the Android platform does (500 ms): a check followed by a subscription could miss the change in
     * between, and the wait is rare and short.
     */
    override suspend fun awaitForegroundUi(): Boolean {
        while (!hasForegroundUi) delay(POLL_MS)
        return true
    }

    override suspend fun askToolPermission(toolDisplayName: String, toolDescription: String, actionDescription: String?): ToolPermissionAnswer {
        Log.w(PENDING_TAG, "IosAgentPlatform.askToolPermission('$toolDisplayName'): DENY (phase 2: route through the shared root)")
        return ToolPermissionAnswer.DENY
    }

    override suspend fun askQuestion(title: String?, message: String): Boolean {
        Log.w(PENDING_TAG, "IosAgentPlatform.askQuestion('${title.orEmpty()}'): false (phase 2: route through the shared root)")
        return false
    }

    override fun showShortMessage(message: String) = notifier.showInfo(message)

    private companion object {
        const val POLL_MS = 500L
        const val PENDING_TAG = "C7-pending"
    }
}
