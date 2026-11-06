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
package net.bible.sharedui.desktop.webengine

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much the Compose density of this content exceeds the window's AWT scale (#4006). 1 except on Linux with a
 * fractional display scale, where the host multiplies the density of the window content by the fractional rest that the
 * JDK's integer X11 scale cannot take. Off-screen web panes divide their density by it to get AWT units on screen
 * (mouse events, window bounds), which differ from CEF's DIP there.
 */
val LocalDensityOverAwt = staticCompositionLocalOf { 1.0 }
