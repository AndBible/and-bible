/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.event.window

/**
 * Marker for every event about the window/workspace layout.
 *
 * It used to live at the top of `WindowSizeChangedEvent.kt`, sharing a file with the one event it
 * happened to be declared next to. Batch Z-late phase 1's epilogue deleted that event with the
 * classic split reading area (its sole poster), which would have taken this interface -- and its
 * five other implementors -- with it, so it moved into a file of its own.
 */
interface WindowEvent
