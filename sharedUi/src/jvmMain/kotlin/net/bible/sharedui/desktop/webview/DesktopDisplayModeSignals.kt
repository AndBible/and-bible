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

import net.bible.sharedui.desktop.assembly.DesktopNightModeSource
import net.bible.sharedui.host.reading.DisplayModeSignals
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * Desktop [DisplayModeSignals]: fires whenever the [DesktopNightModeSource] reports a possible change (today: its
 * night-mode keys in the device settings changed; D3d adds the system appearance). The source owns the change detection.
 *
 * [onSignal] runs on the Swing event thread, whichever thread the source reported on; nothing is delivered after the
 * returned stop function ran. [runOnMain] is a test seam.
 */
class DesktopDisplayModeSignals(
    private val source: DesktopNightModeSource,
    private val runOnMain: (() -> Unit) -> Unit = { block ->
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    },
) : DisplayModeSignals {
    override fun observe(onSignal: () -> Unit): () -> Unit {
        val active = AtomicBoolean(true)
        val stop = source.observeChanges { if (active.get()) runOnMain { if (active.get()) onSignal() } }
        return {
            active.set(false)
            stop()
        }
    }
}
