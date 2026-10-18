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

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType

/**
 * The reading view's search *settings* state (word mode, Bible section, translations, the EPUB
 * word mode, whether the settings sheet is open), in common code and persisted through
 * [AppSettings] under the keys the classic search Activities use, byte for byte.
 *
 * Plain Compose `State` rather than a `SearchFormController`: the reading view's current book
 * moves as the user reads, so the book name is read fresh per search instead. [scope] is the
 * host's scope, kept for the search sheet and controllers that join this class later; nothing
 * here launches on it yet. Everything is public so those parts can read the same state.
 */
@kotlin.native.HiddenFromObjC
class ReadingSearchState(
    private val settings: AppSettings,
    val scope: CoroutineScope,
) {
    val searchType: MutableState<SearchType> = mutableStateOf(SearchType.ALL_WORDS)
    val searchSection: MutableState<SearchBibleSection> = mutableStateOf(SearchBibleSection.ALL)
    val searchTranslations: MutableState<List<String>> = mutableStateOf(emptyList())

    /** The Bibles the settings sheet offers, as (initials, abbreviation). */
    val searchAvailableTranslations: MutableState<List<Pair<String, String>>> = mutableStateOf(emptyList())

    /** Whether the modal search-settings sheet is open. */
    val searchSettingsOpen: MutableState<Boolean> = mutableStateOf(false)

    /**
     * The EPUB word mode, seeded from the key the standalone EPUB search Activities share. The
     * backing flow is private: callers go through [persistEpubSearchMode] so the settings key is
     * always written too. Re-read with [reloadEpubSearchMode] on every search open, because the
     * store is shared and this value is only a starting point.
     */
    private val _epubSearchMode = MutableStateFlow(loadEpubSearchMode())
    val epubSearchMode: StateFlow<EpubSearchMode> = _epubSearchMode.asStateFlow()

    /** Persists the word mode via the classic wire format ([toClassicSearchTypeName]). */
    fun persistEpubSearchMode(mode: EpubSearchMode) {
        _epubSearchMode.value = mode
        settings.setString(EPUB_SEARCH_TYPE_KEY, mode.toClassicSearchTypeName())
    }

    /** Re-reads the EPUB word mode from the shared settings key. */
    fun reloadEpubSearchMode() {
        _epubSearchMode.value = loadEpubSearchMode()
    }

    private fun loadEpubSearchMode(): EpubSearchMode =
        epubSearchModeFromClassicName(settings.getString(EPUB_SEARCH_TYPE_KEY))

    /** Applies and persists a translation choice made in the settings sheet (comma-joined, as classic). */
    fun setSearchTranslations(ids: List<String>) {
        searchTranslations.value = ids
        settings.setString(SEARCH_TRANSLATIONS_KEY, ids.joinToString(","))
    }

    /** The persisted translation choice, restricted to [installed] initials; empty when none is saved. */
    fun loadPersistedTranslations(installed: Set<String>): List<String> {
        val saved = settings.getString(SEARCH_TRANSLATIONS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        return saved.split(",").filter { it in installed }
    }

    private companion object {
        const val SEARCH_TRANSLATIONS_KEY = "search_selected_translations"
    }
}
