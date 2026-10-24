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
package net.bible.sharedui.reading.host

/**
 * Every constructor parameter of [ReadingHostDeps], by name. The jvm test `ReadingHostDepsFieldsTest` fails when this list and
 * the class disagree (a field was added or removed), and the iOS host test `IosReadingHostTest.everyReadingHostDepsFieldIsSuppliedOrNamedInert`
 * accounts for each name here as supplied or on its named inert allow-list, so a new slot cannot be left at the inert
 * default unnoticed.
 */
object ReadingHostDepsFields {
    val ALL: Set<String> = setOf(
        "state",
        "controller",
        "toolbarCallbacks",
        "toolbarIcons",
        "toolbar",
        "nightMode",
        "monochrome",
        "generation",
        "fullScreen",
        "overlayText",
        "activeIsBibleShown",
        "imeBottomPaddingPx",
        "disableAnimations",
        "overflowItems",
        "overflowExpanded",
        "onOverflowItemClick",
        "onOverflowDismiss",
        "drawerMenu",
        "drawerIcon",
        "onDrawerItemClick",
        "onDrawerInMotion",
        "onDrawerIdleClosed",
        "onDrawerClosed",
        "paneBackground",
        "windowLabel",
        "windowIcon",
        "windowTopLabel",
        "handlePaneMenuItem",
        "menuIcon",
        "llmDialog",
        "onLlmPromptChosen",
        "onLlmToggleFavorite",
        "onLlmCategoryExpandedChanged",
        "onLlmSpecifySubmitted",
        "onLlmModelChosen",
        "onLlmRegenerateConfirmed",
        "onLlmDismiss",
        "speakDialog",
        "onSpeakBookmarkChosen",
        "onSpeakDialogDismiss",
        "searchBar",
        "searchBarCallbacks",
        "searchSheetVisible",
        "onSearchSheetDismissed",
        "onSearchSheetOffsetsChanged",
        "searchUnavailableDocName",
        "onSearchUnavailableMessageShown",
        "agentLogSlot",
        "speakBarSlot",
        "agentLogVisible",
        "speakBarVisible",
        "searchSheetSlot",
        "searchSettingsSlot",
        "textSettingsEditorSlot",
        "speakSettingsSlot",
        "quickSheetContent",
        "readingDialogSlot",
    )
}
