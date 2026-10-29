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

import org.koin.core.module.Module
import org.koin.dsl.module

// OWNER: plan D3a (desktop reading: layout direction, reading input such as wheel, keys and back, selection-menu wiring).
// D3a replaces this file's content only. Nothing in the D2b assembly needs a reading binding that is not already
// hoisted, so the module is empty on purpose; the layout direction and key/back handling hang off the window, not Koin.

fun desktopReadingModule(): Module = module { }
