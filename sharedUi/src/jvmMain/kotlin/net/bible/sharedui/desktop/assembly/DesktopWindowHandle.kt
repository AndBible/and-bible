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
package net.bible.sharedui.desktop.assembly

import androidx.compose.ui.unit.IntSize
import net.bible.sharedui.assembly.HostWindowMetrics

/**
 * What the desktop assembly needs from the application window: its content size in dp, the display density and a way to
 * raise it. The window itself (Compose `Window` plus the CEF panes) is Task 4; the assembly only reads this.
 */
interface DesktopWindowHandle {
    val sizeDp: IntSize
    val density: Float
    fun toFront()
}

/** [HostWindowMetrics] over the window: points are dp, scale is the density. */
class DesktopWindowMetrics(private val window: DesktopWindowHandle) : HostWindowMetrics {
    override val width: Double get() = window.sizeDp.width.toDouble()
    override val height: Double get() = window.sizeDp.height.toDouble()
    override val scale: Float get() = window.density
}
