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

package net.bible.sharedui.strings.keyed

/**
 * The labels of the text-selection menu, read from the generated string tables by the resource keys of
 * `res/menu/bibleview_selection*.xml` and Android's `BibleView.onPrepareActionMenu`. The keys are in [KEYS], pinned
 * against `strings.xml` by `KeyedSelectionMenuStringsTest`.
 */
@kotlin.native.HiddenFromObjC
class KeyedSelectionMenuStrings(private val t: StringTable) {
    val addBookmark get() = t.text("add_bookmark1")
    val addBookmarkSelection get() = t.text("add_bookmark3")
    val addBookmarkWholeVerse get() = t.text("add_bookmark_whole_verse1")
    val addParagraphBreak get() = t.text("add_paragraph_break")
    val compare get() = t.text("compare_abbreviation")
    val memorize get() = t.text("memorize_abbreviation")
    val shareVerses get() = t.text("share_verse_ok")
    val search get() = t.text("search")
    val webSearch get() = t.text("web_search")
    val lookupDictionary get() = t.text("lookup_in_dictionary")
    val copy get() = t.text("copy")
    val llmAction get() = t.text("llm_actions")

    /** "Open <ref>" for a selection that resolves to a scripture reference. */
    fun goToRef(refName: String) = t.fmt("go_to_ref", refName)

    /** The search row's label: `Find "<text>"` for a short selection, plain "Find" otherwise (Android: under 16 characters). */
    fun searchLabel(text: String) = if (text.length < 16) t.fmt("search_what", text) else search

    companion object {
        /** Every resource key this class reads. */
        val KEYS = listOf(
            "add_bookmark1", "add_bookmark3", "add_bookmark_whole_verse1", "add_paragraph_break", "compare_abbreviation",
            "memorize_abbreviation", "share_verse_ok", "search", "web_search", "lookup_in_dictionary", "copy", "llm_actions",
            "go_to_ref", "search_what",
        )
    }
}
