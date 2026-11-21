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
package net.bible.sharedui.host.reading

import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppSettings
import net.bible.service.cloudsync.SyncNow
import net.bible.sharedui.host.app.HostRootLinks
import org.koin.mp.KoinPlatform

/** A change of the font size setting from the keyboard. */
enum class FontSizeChange {
    Larger, Smaller, Reset;

    /** The new `font_size_multiplier` percent for [current], within the settings slider's 10..500. */
    fun apply(current: Int, steps: Int = 1): Int = when (this) {
        Larger -> current + FONT_STEP * steps
        Smaller -> current - FONT_STEP * steps
        Reset -> FONT_DEFAULT
    }.coerceIn(FONT_MIN, FONT_MAX)

    companion object {
        const val FONT_STEP = 10
        const val FONT_DEFAULT = 100
        const val FONT_MIN = 10
        const val FONT_MAX = 500
    }
}


/**
 * The keyboard-shortcut actions both the desktop ([net.bible.sharedui.desktop.reading.DesktopShortcutActionsImpl]) and
 * iOS (#4177) run, so a shortcut does the same on every platform. Each acts on the Koin-provided state and the hoisted
 * [HostReadingWindows]; the callers decide whether the reading screen is current ([HostRootLinks.isOnReadingScreen]).
 */
object HostShortcutActions {
    const val FONT_SIZE_KEY = "font_size_multiplier"

    private fun koin() = KoinPlatform.getKoin()

    /** Applies [change] [steps] times at once (one setting write, one config resend); the result stays within 10..500. */
    fun changeFontSize(change: FontSizeChange, steps: Int = 1) {
        val settings = koin().get<AppSettings>()
        val current = settings.getInt(FONT_SIZE_KEY, FontSizeChange.FONT_DEFAULT)
        val next = change.apply(current, steps)
        if (next == current) return
        settings.setInt(FONT_SIZE_KEY, next)
        HostReadingWindows.nightModeChanged()
    }

    fun focusNextWindow() = koin().get<WindowControl>().focusNextWindow()

    fun focusPreviousWindow() = koin().get<WindowControl>().focusPreviousWindow()

    fun openSettings() = HostRootLinks.openRoute(NavRoutes.SETTINGS)

    /** Starts the menu's Sync now action, or opens synchronization settings when not configured. */
    fun syncNow() {
        val sync = koin().get<SyncNow>()
        if (sync.available()) sync.start() else HostRootLinks.openRoute(NavRoutes.SYNC_SETTINGS)
    }
}
