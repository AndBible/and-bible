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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.search.EpubSearchMode
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

    @Test
    fun defaults() {
        val s = state(MapSettings())
        assertEquals(SearchType.ALL_WORDS, s.searchType.value)
        assertEquals(SearchBibleSection.ALL, s.searchSection.value)
        assertFalse(s.searchSettingsOpen.value)
    }
}
