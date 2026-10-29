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
package net.bible.sharedui.desktop.webview

import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings
import net.bible.sharedui.host.reading.DisplayModeSignals
import javax.swing.SwingUtilities

/**
 * Desktop [DisplayModeSignals]: fires when the night-mode keys of the device settings store ([KEYS]) change, which is
 * where the settings screen writes them. The desktop has no system-appearance or app-activation signal yet (the night
 * mode stub never follows the system), so the store is the only source.
 *
 * [onSignal] runs on the Swing event thread, whichever thread wrote the setting. [runOnMain] is a test seam.
 */
class DesktopDisplayModeSignals(
    private val settings: DesktopDeviceSettings,
    private val runOnMain: (() -> Unit) -> Unit = { block ->
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    },
) : DisplayModeSignals {
    override fun observe(onSignal: () -> Unit): () -> Unit {
        val lock = Any()
        var previous = snapshot()
        var active = true
        val stop = settings.observeChanges {
            val changed = synchronized(lock) {
                val now = snapshot()
                (now != previous).also { previous = now }
            }
            if (changed) runOnMain { if (synchronized(lock) { active }) onSignal() }
        }
        return {
            synchronized(lock) { active = false }
            stop()
        }
    }

    private fun snapshot(): List<String?> = KEYS.map { settings.getString(it) }

    companion object {
        /** The mode (`system`/`manual`/`automatic`) and the manual flag, as read by `DesktopNightModeStub`. */
        val KEYS = listOf("night_mode_pref3", "night_mode_pref")
    }
}
