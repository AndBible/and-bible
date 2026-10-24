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

import net.bible.android.control.speak.SpeakPhrases

/** The spoken-text phrases of [t]'s language (`AndroidSpeakPlatform.phrasesFor`), divine-name arrays included. */
fun keyedSpeakPhrases(t: StringTable): SpeakPhrases = SpeakPhrases(
    t.text("speak_first"), t.text("speak_second"), t.text("speak_third"), t.text("speak_fourth"), t.text("speak_fifth"),
    t.text("speak_book_changed"), t.text("speak_chapter_changed"),
    t.array("speak_divinename_original"), t.array("speak_divinename_replace"),
)
