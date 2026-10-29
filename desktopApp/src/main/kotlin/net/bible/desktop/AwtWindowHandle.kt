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
package net.bible.desktop

import androidx.compose.ui.unit.IntSize
import net.bible.sharedui.desktop.assembly.DesktopWindowHandle
import java.awt.Window
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * [DesktopWindowHandle] over the AWT window of the Compose `Window`, which exists only once the window is composed:
 * until [attach] (and while the window has no size yet) it answers the default window size at density 1. AWT sizes on
 * a scaled display are already in logical pixels, which are Compose dp; the density is the screen's scale.
 */
class AwtWindowHandle(private val defaultSize: IntSize = IntSize(DEFAULT_WIDTH_DP, DEFAULT_HEIGHT_DP)) : DesktopWindowHandle {
    @Volatile private var window: Window? = null

    fun attach(window: Window) {
        this.window = window
    }

    override val sizeDp: IntSize
        get() {
            val w = window ?: return defaultSize
            val content = (w as? RootPaneContainer)?.contentPane ?: w
            return IntSize(content.width, content.height).takeIf { it.width > 0 && it.height > 0 } ?: defaultSize
        }

    override val density: Float
        get() = window?.graphicsConfiguration?.defaultTransform?.scaleX?.toFloat()?.takeIf { it > 0f } ?: 1f

    override fun toFront() {
        val w = window ?: return
        SwingUtilities.invokeLater {
            w.toFront()
            w.requestFocus()
        }
    }

    companion object {
        const val DEFAULT_WIDTH_DP = 1200
        const val DEFAULT_HEIGHT_DP = 800
        const val MIN_WIDTH_DP = 480
        const val MIN_HEIGHT_DP = 600
    }
}
