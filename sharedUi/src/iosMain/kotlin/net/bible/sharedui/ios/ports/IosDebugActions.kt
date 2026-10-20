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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import net.bible.sharedui.app.ports.DebugActions
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.ios.startup.IosDebugActionPolicy
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.native.Platform

/**
 * Debug-only tools. The fatal callback belongs to the main dispatch queue, not a screen/coroutine scope:
 * leaving Settings cannot cancel it and no coroutine exception handler can consume it before the app hook.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
@kotlin.native.HiddenFromObjC
class IosDebugActions(notifier: UserNotifier, strings: CoreStrings) : DebugActions {
    private val actions = IosDebugActionPolicy(
        isDebug = { Platform.isDebugBinary },
        notifier = notifier,
        testMessage = { strings.debugTestError },
        scheduleFatal = { milliseconds, fatal ->
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, milliseconds * 1_000_000L), dispatch_get_main_queue(), fatal)
        },
    )

    override fun crashAppDelayed() = actions.crashAppDelayed()

    // SettingsHost's legacy Android messages stay unchanged; iOS always resolves its localized resource live.
    override fun showTestError(message: String) = actions.showTestError()
}
