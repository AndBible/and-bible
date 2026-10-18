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

package net.bible.sharedui.strings.keyed

import net.bible.sharedlogic.platform.LabelNameStrings

/** [LabelNameStrings] over a [StringTable]; mirrors `AndroidLabelNameStrings`. */
class KeyedLabelNameStrings(private val t: StringTable) : LabelNameStrings {
    override val speak: String get() = t.text("speak")
    override val unlabelled: String get() = t.text("label_unlabelled")
    override val paragraphBreak: String get() = t.text("add_paragraph_break")
    override val ai: String get() = t.text("ai_label")
}
