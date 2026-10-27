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

package net.bible.android.view.activity.page

/** The rows of the text-selection menu (the items of `res/menu/bibleview_selection.xml` and `bibleview_selection2.xml`). */
@kotlin.native.HiddenFromObjC
enum class SelectionMenuItem {
    AddBookmark, AddBookmarkSelection, AddBookmarkWholeVerse, AddParagraphBreak, Compare, Memorize, ShareVerses,
    OpenRef, Search, WebSearch, LookupDictionary, Copy, LlmAction,
}

/**
 * What decides the selection menu's rows.
 *
 * @param isBible the window's first document is a Bible (two-step bookmarking, compare, memorize and share are Bible-only)
 * @param twoStepDisabled the `disable_two_step_bookmarking` preference
 * @param hasVerseSelection the page reported a verse-bound selection (Android: `currentSelection != null`)
 * @param selectionText the selected text, null when there is no selection
 * @param resolvedRef the text resolves to a scripture reference (Android: `currentSelectionRef != null`)
 * @param paragraphBreakEnabled `CommonUtils.settings.addParagraphBreakEnabled`
 * @param hasLookupDictionaries a word-lookup dictionary is configured and installed
 * @param llmConfigured the AI features are configured
 */
@kotlin.native.HiddenFromObjC
data class SelectionMenuInput(
    val isBible: Boolean,
    val twoStepDisabled: Boolean,
    val hasVerseSelection: Boolean,
    val selectionText: String?,
    val resolvedRef: Boolean,
    val paragraphBreakEnabled: Boolean,
    val hasLookupDictionaries: Boolean,
    val llmConfigured: Boolean,
)

/**
 * The rows of the selection menu in menu order, exactly as Android's `BibleView.onPrepareActionMenu` shows them
 * (Android reads this and adds its process-text activities on top). [step2] is the second menu
 * (`bibleview_selection2`) a Bible shows after the single "Bookmark" row: selection or whole verses.
 *
 * - Bookmark rows need a verse selection. A Bible with two-step bookmarking disabled shows "selection" and "verses"
 *   directly; every other case (two-step Bible, any non-Bible document) shows the single "Bookmark" row.
 * - Compare, memorize and share are Bible-only; share also needs a verse selection.
 * - Copy needs text and is offered for non-Bible documents or selections without verse context (e.g. footnote popups).
 * - A selection that resolves to a reference offers "open"; otherwise "search". Web search needs text, dictionary
 *   lookup needs text and a dictionary, the AI row needs text and AI configured.
 * - The paragraph break needs a verse selection and the preference.
 */
@kotlin.native.HiddenFromObjC
fun selectionMenuItems(input: SelectionMenuInput, step2: Boolean): List<SelectionMenuItem> {
    if (step2) return listOf(SelectionMenuItem.AddBookmarkSelection, SelectionMenuItem.AddBookmarkWholeVerse)
    val text = input.selectionText != null
    return buildList {
        if (input.hasVerseSelection) {
            if (input.isBible && input.twoStepDisabled) {
                add(SelectionMenuItem.AddBookmarkSelection)
                add(SelectionMenuItem.AddBookmarkWholeVerse)
            } else {
                add(SelectionMenuItem.AddBookmark)
            }
        }
        if (input.isBible) add(SelectionMenuItem.Compare)
        if (input.isBible) add(SelectionMenuItem.Memorize)
        if (input.isBible && input.hasVerseSelection) add(SelectionMenuItem.ShareVerses)
        if (text && (!input.isBible || !input.hasVerseSelection)) add(SelectionMenuItem.Copy)
        if (!input.resolvedRef && text) add(SelectionMenuItem.Search)
        if (input.resolvedRef) add(SelectionMenuItem.OpenRef)
        if (text) add(SelectionMenuItem.WebSearch)
        if (text && input.hasLookupDictionaries) add(SelectionMenuItem.LookupDictionary)
        if (input.hasVerseSelection && input.paragraphBreakEnabled) add(SelectionMenuItem.AddParagraphBreak)
        if (text && input.llmConfigured) add(SelectionMenuItem.LlmAction)
    }
}
