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


package net.bible.sharedui.ios.reading

import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/** UIKit's background-task window for an agent run: begins it, [onExpire] runs when iOS expires it. Passed to [HostAgentStarter] by the iOS host. */
internal fun iosBeginAgentBackgroundTask(onExpire: () -> Unit): ULong =
    UIApplication.sharedApplication.beginBackgroundTaskWithName("agent-run") { onExpire() }

/** Ends a window [iosBeginAgentBackgroundTask] began. */
internal fun iosEndAgentBackgroundTask(id: ULong) {
    UIApplication.sharedApplication.endBackgroundTask(id)
}

/** UIKit's "no task was granted" identifier. */
internal val iosInvalidBackgroundTaskId: ULong get() = UIBackgroundTaskInvalid
