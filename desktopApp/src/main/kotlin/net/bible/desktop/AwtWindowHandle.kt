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
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * [DesktopWindowHandle] over the AWT window of the Compose `Window`, which exists only once the window is composed:
 * until [attach] (and while the window has no size yet) it answers the default window size at density 1. AWT sizes on
 * a scaled display are already in logical pixels; the density is the screen's scale. With a Compose [factor] (the
 * fractional part of the Linux display scale, `DisplayScale.composeFactor`) a dp is [factor] logical pixels.
 */
class AwtWindowHandle(
    private val defaultSize: IntSize = IntSize(DEFAULT_WIDTH_DP, DEFAULT_HEIGHT_DP),
    private val factor: Double = 1.0,
) : DesktopWindowHandle {
    @Volatile private var window: Window? = null

    fun attach(window: Window) {
        this.window = window
    }

    override val sizeDp: IntSize
        get() {
            val w = window ?: return defaultSize
            val content = (w as? RootPaneContainer)?.contentPane ?: w
            return IntSize((content.width / factor).toInt(), (content.height / factor).toInt())
                .takeIf { it.width > 0 && it.height > 0 } ?: defaultSize
        }

    override val density: Float
        get() = ((window?.graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0.0 } ?: 1.0) * factor).toFloat()

    override val isFocusedAndVisible: Boolean
        get() {
            val mainWindow = window ?: return false
            if (!mainWindow.isVisible) return false
            val focusedWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow ?: return false
            return DesktopAppFocusTracker.ownedBy(mainWindow)(focusedWindow)
        }

    override fun toFront() {
        val w = window ?: return
        SwingUtilities.invokeLater {
            (w as? Frame)?.let { frame ->
                val restored = restoredExtendedState(frame.extendedState)
                if (restored != frame.extendedState) frame.extendedState = restored
            }
            w.toFront()
            w.requestFocus()
        }
    }

    companion object {
        /** [state] without the ICONIFIED bit (a minimized frame is restored); MAXIMIZED_* bits are kept. */
        fun restoredExtendedState(state: Int): Int = state and Frame.ICONIFIED.inv()

        const val DEFAULT_WIDTH_DP = 1200
        const val DEFAULT_HEIGHT_DP = 800
        const val MIN_WIDTH_DP = 480
        const val MIN_HEIGHT_DP = 600

        /**
         * The first window size: the default, shrunk to fit [workArea] (the screen minus the taskbar, in AWT user space,
         * i.e. dp on a scaled Windows display), but never below the minimum size. At 150 % a 1920 x 1080 laptop has only
         * 1280 x ~690 dp above the taskbar (W1). A null [workArea] (headless, unknown) keeps the default. [factor] is the
         * Compose part of the display scale (`DisplayScale.composeFactor`): sizes are in AWT units, the content in
         * Compose dp scaled by it.
         */
        fun initialSizeDp(workArea: java.awt.Rectangle?, factor: Double = 1.0): IntSize {
            val width = (DEFAULT_WIDTH_DP * factor).toInt()
            val height = (DEFAULT_HEIGHT_DP * factor).toInt()
            if (workArea == null || workArea.width <= 0 || workArea.height <= 0) return IntSize(width, height)
            return IntSize(
                width.coerceAtMost(workArea.width).coerceAtLeast(minSize(MIN_WIDTH_DP, factor)),
                height.coerceAtMost(workArea.height).coerceAtLeast(minSize(MIN_HEIGHT_DP, factor)),
            )
        }

        /** A minimum size in AWT units for the Compose [factor]. */
        fun minSize(minDp: Int, factor: Double = 1.0): Int = (minDp * factor).toInt()

        /**
         * Where the first window goes: centred on [workArea], but never above or left of it, so the title bar stays on
         * screen even when the minimum size is taller than the work area. Null (no work area) leaves it to the platform.
         */
        fun initialPositionDp(workArea: java.awt.Rectangle?, size: IntSize): androidx.compose.ui.unit.IntOffset? {
            if (workArea == null || workArea.width <= 0 || workArea.height <= 0) return null
            return androidx.compose.ui.unit.IntOffset(
                workArea.x + ((workArea.width - size.width) / 2).coerceAtLeast(0),
                workArea.y + ((workArea.height - size.height) / 2).coerceAtLeast(0),
            )
        }

        /** The default screen's work area, or null when there is none (headless). */
        fun screenWorkArea(): java.awt.Rectangle? =
            runCatching { java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
    }
}
