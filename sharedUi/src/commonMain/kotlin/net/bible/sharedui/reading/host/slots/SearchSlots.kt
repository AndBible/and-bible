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

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.bible.sharedui.search.BibleSearchSettings
import net.bible.sharedui.search.EpubSearchSettings
import net.bible.sharedui.search.SearchSettingsSheet

/**
 * The modal search-settings sheet (the toolbar's Tune affordance). [forEpub] and [currentBookName]
 * are read at composition time, not collected: the sheet is modal and composes its content only
 * while open, so the active window cannot change under it. [onClosed] re-runs a search already in
 * flight (`ReadingSearchController.settingsClosed`).
 */
@kotlin.native.HiddenFromObjC
@Composable
internal fun SearchSettingsSlotContent(
    state: ReadingSearchState,
    forEpub: () -> Boolean,
    currentBookName: () -> String,
    onClosed: () -> Unit,
) {
    SearchSettingsSheet(
        open = state.searchSettingsOpen.value,
        onDismiss = {
            state.searchSettingsOpen.value = false
            onClosed()
        },
    ) {
        if (forEpub()) {
            val mode by state.epubSearchMode.collectAsState()
            EpubSearchSettings(mode = mode, onMode = state::persistEpubSearchMode)
        } else {
            BibleSearchSettings(
                searchType = state.searchType.value,
                bibleSection = state.searchSection.value,
                availableTranslations = state.searchAvailableTranslations.value,
                selectedTranslationIds = state.searchTranslations.value,
                currentBookName = currentBookName(),
                onSearchType = { state.searchType.value = it },
                onBibleSection = { state.searchSection.value = it },
                onTranslations = { ids -> state.setSearchTranslations(ids) },
            )
        }
    }
}
