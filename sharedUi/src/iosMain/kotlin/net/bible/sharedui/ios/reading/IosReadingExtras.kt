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

package net.bible.sharedui.ios.reading

@kotlin.native.HiddenFromObjC
interface IosReadingExtras {
    /** Drawer row ids (ReadingDrawerMenu.ID_*) this build offers; a row not in the set is not shown. */
    val offeredRows: Set<String>
    fun runRow(id: String)
    fun reportJsError()
    object None : IosReadingExtras { override val offeredRows = emptySet<String>(); override fun runRow(id: String) {}; override fun reportJsError() {} }
}
