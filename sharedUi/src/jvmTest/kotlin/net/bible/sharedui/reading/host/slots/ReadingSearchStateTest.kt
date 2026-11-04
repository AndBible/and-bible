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
package net.bible.sharedui.reading.host.slots

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType

class ReadingSearchStateTest {
    private class MapSettings(val strings: MutableMap<String, String?> = mutableMapOf()) : AppSettings {
        override fun getString(key: String, default: String?) = strings[key] ?: default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) { strings[key] = value }
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private fun state(settings: MapSettings) = SearchFixture(settings = MapSearchSettings(settings.strings)).state

    @Test
    fun persistsTranslationsUnderTheSameKey() {
        val settings = MapSettings()
        state(settings).setSearchTranslations(listOf("KJV", "ESV"))
        // The old host wrote ids.joinToString(",") under this exact key.
        assertEquals("KJV,ESV", settings.strings["search_selected_translations"])
    }

    @Test
    fun readsATranslationListWrittenByTheOldCode() {
        // Old encoding: comma-joined; load kept only initials that still resolve to a Bible.
        val settings = MapSettings(mutableMapOf("search_selected_translations" to "KJV,GONE,ESV"))
        assertEquals(listOf("KJV", "ESV"), state(settings).loadPersistedTranslations(setOf("ESV", "KJV")))
        assertEquals(emptyList(), state(MapSettings()).loadPersistedTranslations(setOf("KJV")))
        assertEquals(
            emptyList(),
            state(MapSettings(mutableMapOf("search_selected_translations" to "  "))).loadPersistedTranslations(setOf("KJV")),
        )
    }

    @Test
    fun setSearchTranslationsUpdatesTheLiveState() {
        val s = state(MapSettings())
        s.setSearchTranslations(listOf("KJV"))
        assertEquals(listOf("KJV"), s.searchTranslations.value)
    }

    @Test
    fun epubSearchModeRoundTrips() {
        val settings = MapSettings()
        for (mode in EpubSearchMode.entries) {
            state(settings).persistEpubSearchMode(mode)
            assertEquals(mode, state(settings).epubSearchMode.value, "fresh state reads back $mode")
        }
    }

    @Test
    fun epubModeUsesTheClassicKeyAndFtsIsAbsent() {
        val settings = MapSettings(mutableMapOf("epubSearch-SearchType" to "PHRASE"))
        val s = state(settings)
        assertEquals(EpubSearchMode.PHRASE, s.epubSearchMode.value)
        s.persistEpubSearchMode(EpubSearchMode.FTS)
        assertNull(settings.strings["epubSearch-SearchType"])
        // another surface changes it meanwhile; reload picks it up
        settings.strings["epubSearch-SearchType"] = "ANY_WORDS"
        s.reloadEpubSearchMode()
        assertEquals(EpubSearchMode.ANY_WORD, s.epubSearchMode.value)
    }

    private fun assertStrongsTargetsSelectedBible(active: SearchDocumentInfo) {
        val f = SearchFixture()
        val kjv = fakeSearchBook("KJV")
        val activeBook = fakeSearchBook(active.docId)
        f.platform.current = activeBook
        f.platform.documents[active.docId] = activeBook
        f.platform.documents["KJV"] = kjv
        f.platform.documentInfos[active.docId] = active
        f.platform.documentInfos["KJV"] = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, true)
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            assertEquals(ReadingSearchPhase.Results("KJV", forEpub = false), f.state.controller.phase.value)
            assertTrue(f.state.searchSheetVisible.value)
            assertEquals(listOf("KJV"), f.state.searchResults.selectedTranslations.value)
            val request = assertNotNull(f.state.lastSearchRequestForTest)
            assertEquals(listOf("KJV"), request.translationIds)
            assertEquals("strong:H430", request.query)
            assertEquals(SearchType.ANY_WORDS, request.searchType)
            assertEquals(SearchBibleSection.ALL, request.bibleSection)
            assertTrue(request.isStrongsSearch)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun strongsSearchUsesSelectedBibleWhenDictionaryIsActive() =
        assertStrongsTargetsSelectedBible(SearchDocumentInfo("DICT", SearchDocumentCategory.DICTIONARY, false, true))

    @Test
    fun strongsSearchUsesSelectedBibleWhenEpubIsActive() =
        assertStrongsTargetsSelectedBible(SearchDocumentInfo("EPUB", SearchDocumentCategory.GENERAL_BOOK, true, true))

    @Test
    fun strongsSearchUsesSelectedBibleWhenAnotherUnindexedBibleIsActive() =
        assertStrongsTargetsSelectedBible(SearchDocumentInfo("ESV", SearchDocumentCategory.BIBLE, false, false))

    private fun strongsFixture(fixture: SearchFixture = SearchFixture()): SearchFixture = fixture.also { f ->
        val dictionary = fakeSearchBook("DICT")
        f.platform.current = dictionary
        f.platform.documents["DICT"] = dictionary
        f.platform.documentInfos["DICT"] = SearchDocumentInfo("DICT", SearchDocumentCategory.DICTIONARY, false, true)
        for (id in listOf("KJV", "ESV")) {
            f.platform.documents[id] = fakeSearchBook(id)
            f.platform.documentInfos[id] = SearchDocumentInfo(id, SearchDocumentCategory.BIBLE, false, true)
        }
        f.platform.bibles = listOf("KJV" to "KJV", "ESV" to "ESV")
    }

    @Test
    fun strongsSelectionIndexesEachSelectedBibleBeforeRunning() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV", "ESV")
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV", "ESV")))
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            f.state.controller.acceptIndexing()
            assertEquals(ReadingSearchPhase.Indexing("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV"), f.index.created)
            f.bible.unindexed = setOf("ESV")
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.NeedsIndex("ESV", false), f.state.controller.phase.value)
            f.state.controller.acceptIndexing()
            assertEquals(listOf("KJV", "ESV"), f.index.created)
            f.bible.unindexed = emptySet()
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV", "ESV"), f.state.lastSearchRequestForTest?.translationIds)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun failedStrongsIndexBuildKeepsPromptInSheet() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV")
        f.platform.documentInfos["KJV"] = SearchDocumentInfo("KJV", SearchDocumentCategory.BIBLE, false, false)
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            f.state.controller.acceptIndexing()
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            assertTrue(f.state.searchSheetVisible.value)
            assertNotNull(f.state.searchIndexProgress.error.value)
            assertNull(f.state.lastSearchRequestForTest)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun completedTextOnlyIndexDoesNotRunStrongsSearch() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV")
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            f.state.controller.acceptIndexing()
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            assertNotNull(f.state.searchIndexProgress.error.value)
            assertNull(f.state.lastSearchRequestForTest)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun cancellingStrongsIndexPromptLeavesSearchWithoutStartingBuild() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV")
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            f.state.leaveSearch()
            assertEquals(ReadingSearchPhase.Closed, f.state.controller.phase.value)
            assertFalse(f.state.searchSheetVisible.value)
            assertTrue(f.index.created.isEmpty())
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun strongsSettingsRerunRetainsTargetAfterActiveWindowChanges() {
        val f = strongsFixture()
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            f.platform.current = f.platform.documents.getValue("ESV")
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV"), f.state.lastSearchRequestForTest?.translationIds)
            assertTrue(assertNotNull(f.state.lastSearchRequestForTest).isStrongsSearch)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun ordinarySearchAfterLeavingStrongsUsesActiveBible() {
        val f = strongsFixture()
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            f.state.leaveSearch()
            f.platform.current = f.platform.documents.getValue("ESV")
            f.state.openSearch("light")
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertFalse(assertNotNull(f.state.lastSearchRequestForTest).isStrongsSearch)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun missingStrongsTargetDoesNotFallBackToActiveBible() {
        val f = SearchFixture()
        f.platform.documents["KJV"] = f.platform.current!!
        try {
            f.state.openSearchStrongs("H430", listOf("MISSING"))
            assertEquals(ReadingSearchPhase.Closed, f.state.controller.phase.value)
            assertNotNull(f.state.searchUnavailableDocName.value)
            assertNull(f.state.lastSearchRequestForTest)
        } finally {
            f.state.dispose()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun strongsProbeIsDeferredAndCancelledHandoffCannotReplaceNewSearch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val f = strongsFixture(SearchFixture(indexValidationDispatcher = dispatcher))
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            assertTrue(f.bible.strongsQueries.isEmpty(), "probe must not run inline")
            f.platform.current = f.platform.documents.getValue("ESV")
            f.state.openSearch("light")
            runCurrent()
            assertTrue(f.bible.strongsQueries.isEmpty(), "cancelled probe must not execute")
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertEquals("light", f.state.lastSearchRequestForTest?.query)
        } finally {
            f.state.dispose()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun newerStrongsHandoffCancelsTheEarlierDeferredProbe() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val f = strongsFixture(SearchFixture(indexValidationDispatcher = dispatcher))
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            assertTrue(f.state.openSearchStrongs("G746", listOf("ESV")))
            assertTrue(f.bible.strongsQueries.isEmpty())
            runCurrent()
            assertEquals(listOf(listOf("ESV")), f.bible.strongsQueries)
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertEquals("strong:G746", f.state.lastSearchRequestForTest?.query)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun strongsSelectorChangesTheSearchedAndDisplayedTranslations() {
        val f = strongsFixture()
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            f.state.onSearchTranslationsChosen(listOf("ESV"))
            assertEquals(listOf("ESV"), f.state.searchResults.selectedTranslations.value)
            assertEquals(listOf("ESV"), f.state.searchTranslations.value)
            assertEquals(listOf("ESV"), f.bible.strongsQueries.last())
            f.state.controller.settingsClosed()
            assertEquals(listOf("ESV"), assertNotNull(f.state.lastSearchRequestForTest).translationIds)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun editedStrongsQuerySubmittedAsOrdinarySearchUsesActiveBible() {
        val f = strongsFixture()
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            f.platform.current = f.platform.documents.getValue("ESV")
            f.state.queries.setQuery("light")
            f.state.controller.submit()
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertFalse(assertNotNull(f.state.lastSearchRequestForTest).isStrongsSearch)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun editedStrongsQuerySettingsRerunUsesActiveBible() {
        val f = strongsFixture()
        try {
            assertTrue(f.state.openSearchStrongs("H430", listOf("KJV")))
            f.platform.current = f.platform.documents.getValue("ESV")
            f.state.queries.setQuery("light")
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertFalse(assertNotNull(f.state.lastSearchRequestForTest).isStrongsSearch)
        } finally {
            f.state.dispose()
        }
    }

    @Test
    fun settingsSelectionValidatesNewUnindexedBibleRatherThanOldSelection() {
        val f = strongsFixture()
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            f.bible.unindexed = setOf("ESV")
            f.state.setSearchTranslations(listOf("ESV"))
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.NeedsIndex("ESV", false), f.state.controller.phase.value)
            assertEquals(listOf("ESV"), f.bible.strongsQueries.last())
        } finally { f.state.dispose() }
    }

    @Test
    fun settingsSelectionDropsOldMissingBibleAndRunsOnlyNewSelection() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV")
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            f.state.setSearchTranslations(listOf("ESV"))
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertEquals(listOf("ESV"), f.state.lastSearchRequestForTest?.translationIds)
        } finally { f.state.dispose() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pendingSelectorCannotOverwriteSubmittedOrdinarySearch() = runTest {
        val f = strongsFixture(SearchFixture(indexValidationDispatcher = StandardTestDispatcher(testScheduler)))
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            runCurrent()
            f.bible.unindexed = setOf("ESV")
            f.state.onSearchTranslationsChosen(listOf("ESV"))
            f.platform.current = f.platform.documents.getValue("KJV")
            f.state.queries.setQuery("light")
            f.state.controller.submit()
            runCurrent()
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals("light", f.state.lastSearchRequestForTest?.query)
        } finally { f.state.dispose() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pendingSelectorCannotOverwriteOrdinarySettingsRerun() = runTest {
        val f = strongsFixture(SearchFixture(indexValidationDispatcher = StandardTestDispatcher(testScheduler)))
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            runCurrent()
            f.bible.unindexed = setOf("ESV")
            f.state.onSearchTranslationsChosen(listOf("ESV"))
            f.platform.current = f.platform.documents.getValue("KJV")
            f.state.queries.setQuery("light")
            f.state.controller.settingsClosed()
            runCurrent()
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals("light", f.state.lastSearchRequestForTest?.query)
        } finally { f.state.dispose() }
    }

    @Test
    fun newStrongsHandoffRetiresOldBuildAndItsCompletion() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV")
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            f.state.controller.acceptIndexing()
            assertEquals(ReadingSearchPhase.Indexing("KJV", false), f.state.controller.phase.value)
            f.state.openSearchStrongs("G746", listOf("ESV"))
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            f.bible.unindexed = emptySet()
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.Results("ESV", false), f.state.controller.phase.value)
            assertEquals("strong:G746", f.state.lastSearchRequestForTest?.query)
            assertEquals(listOf("ESV"), f.state.lastSearchRequestForTest?.translationIds)
        } finally { f.state.dispose() }
    }

    @Test
    fun hiddenStrongsIndexChainDoesNotShowSheetOrReleaseFocus() {
        val f = strongsFixture()
        f.bible.unindexed = setOf("KJV", "ESV")
        try {
            f.state.openSearchStrongs("H430", listOf("KJV", "ESV"))
            f.state.controller.acceptIndexing()
            f.state.controller.closeSheet()
            f.state.controller.imeRequestHandled()
            f.bible.unindexed = setOf("ESV")
            f.jobs.emit(Any(), finished = true)
            assertEquals(ReadingSearchPhase.NeedsIndex("ESV", false), f.state.controller.phase.value)
            assertFalse(f.state.searchSheetVisible.value)
            assertNull(f.state.controller.imeRequest.value)
        } finally { f.state.dispose() }
    }

    @Test
    fun strongsSelectorPersistsConfirmedUnindexedSelectionBeforeBuild() {
        val f = strongsFixture()
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            f.bible.unindexed = setOf("ESV")
            f.state.onSearchTranslationsChosen(listOf("ESV"))
            assertEquals(ReadingSearchPhase.NeedsIndex("ESV", false), f.state.controller.phase.value)
            assertEquals(listOf("ESV"), f.bible.persisted.lastOrNull())
        } finally { f.state.dispose() }
    }

    @Test
    fun emptyStrongsSettingsSelectionRetainsTargetInsteadOfActiveUnindexedBible() {
        val f = strongsFixture()
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            f.platform.current = f.platform.documents.getValue("ESV")
            f.platform.documentInfos["ESV"] = SearchDocumentInfo("ESV", SearchDocumentCategory.BIBLE, false, false)
            f.bible.unindexed = setOf("KJV")
            f.state.setSearchTranslations(emptyList())
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.NeedsIndex("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV"), f.bible.strongsQueries.last())
        } finally { f.state.dispose() }
    }

    @Test
    fun emptyStrongsSettingsSelectionRetainsTargetInsteadOfActiveEpub() {
        val f = strongsFixture()
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            val epub = fakeSearchBook("EPUB")
            f.platform.current = epub
            f.platform.documents["EPUB"] = epub
            f.platform.documentInfos["EPUB"] = SearchDocumentInfo("EPUB", SearchDocumentCategory.GENERAL_BOOK, true, true)
            f.state.setSearchTranslations(emptyList())
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV"), f.state.lastSearchRequestForTest?.translationIds)
            assertEquals(listOf("KJV"), f.bible.strongsQueries.last())
        } finally { f.state.dispose() }
    }

    @Test
    fun emptyStrongsResultsSelectionKeepsTargetAndSelection() {
        val f = strongsFixture()
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            f.platform.current = f.platform.documents.getValue("ESV")
            f.platform.documentInfos["ESV"] = SearchDocumentInfo("ESV", SearchDocumentCategory.BIBLE, false, false)
            f.state.onSearchTranslationsChosen(emptyList())
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV"), f.state.lastSearchRequestForTest?.translationIds)
            assertEquals(listOf("KJV"), f.state.searchTranslations.value)
        } finally { f.state.dispose() }
    }

    @Test
    fun emptyStrongsResultsSelectionCannotRetargetActiveEpub() {
        val f = strongsFixture()
        try {
            f.state.openSearchStrongs("H430", listOf("KJV"))
            val epub = fakeSearchBook("EPUB")
            f.platform.current = epub
            f.platform.documents["EPUB"] = epub
            f.platform.documentInfos["EPUB"] = SearchDocumentInfo("EPUB", SearchDocumentCategory.GENERAL_BOOK, true, true)
            f.state.onSearchTranslationsChosen(emptyList())
            f.state.controller.settingsClosed()
            assertEquals(ReadingSearchPhase.Results("KJV", false), f.state.controller.phase.value)
            assertEquals(listOf("KJV"), f.state.searchTranslations.value)
        } finally { f.state.dispose() }
    }

    @Test
    fun defaults() {
        val s = state(MapSettings())
        assertEquals(SearchType.ALL_WORDS, s.searchType.value)
        assertEquals(SearchBibleSection.ALL, s.searchSection.value)
        assertFalse(s.searchSettingsOpen.value)
    }
}
