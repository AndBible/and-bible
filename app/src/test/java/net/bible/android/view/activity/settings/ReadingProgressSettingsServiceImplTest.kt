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
package net.bible.android.view.activity.settings

import net.bible.android.TestBibleApplication
import net.bible.service.common.ReadingProgressSettings
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingProgressSettingsServiceImplTest {
    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    // --- defaults mirror ReadingProgressSettingsBundle() / GlobalReadingProgressSettings() ---

    @Test fun defaults_matchClassicSettings() {
        val service = ReadingProgressSettingsServiceImpl()
        val s = service.snapshot.value

        assertTrue(s.autoMarkMemorized)
        assertFalse(s.memorizeTypeFullWords)
        assertEquals("light", s.memorizeWordVisibility)
        assertTrue(s.memorizeErrorHeatmap)
        assertFalse(s.memorizeScrambleHideUsed)
        assertTrue(s.memorizeIncludeReference)
        assertEquals(setOf("light", "dim", "hidden"), s.memorizeWordVisibilityChoices.map { it.value }.toSet())
    }

    // --- bool round trip + persistence via the ReadingProgressSettings singleton (not CommonUtils.settings) ---

    @Test fun setBool_autoMarkMemorized_updatesSnapshotAndUnderlyingStore() {
        val service = ReadingProgressSettingsServiceImpl()

        service.setBool("auto_mark_memorized", false)

        assertFalse(service.snapshot.value.autoMarkMemorized)
        assertFalse(ReadingProgressSettings.autoMarkMemorized)
    }

    @Test fun setBool_memorizeScrambleHideUsed_roundTrips() {
        val service = ReadingProgressSettingsServiceImpl()

        service.setBool("memorize_scramble_hide_used", true)

        assertTrue(service.snapshot.value.memorizeScrambleHideUsed)
        assertTrue(ReadingProgressSettings.memorizeScrambleHideUsed)
    }

    // --- string round trip ---

    @Test fun setString_memorizeWordVisibility_roundTrips() {
        val service = ReadingProgressSettingsServiceImpl()

        service.setString("memorize_word_visibility", "full")

        assertEquals("full", service.snapshot.value.memorizeWordVisibility)
        assertEquals("full", ReadingProgressSettings.memorizeWordVisibility)
    }

    // --- notification parity: classic ReadingProgressSettingsDataStore notified on every write (the singleton's own setters do not) ---

    @Test fun setBool_notifiesReadingProgressSettingsChanged() {
        val service = ReadingProgressSettingsServiceImpl()
        var eventCount = 0
        val subscription = ReadingProgressSettings.changed.subscribe { eventCount++ }
        try {
            service.setBool("auto_mark_memorized", false)
            assertEquals(1, eventCount)
        } finally {
            subscription.cancel()
        }
    }

    @Test fun setString_notifiesReadingProgressSettingsChanged() {
        val service = ReadingProgressSettingsServiceImpl()
        var eventCount = 0
        val subscription = ReadingProgressSettings.changed.subscribe { eventCount++ }
        try {
            service.setString("memorize_word_visibility", "hidden")
            assertEquals(1, eventCount)
        } finally {
            subscription.cancel()
        }
    }

    @Test fun theSettingsSettersAloneDoNotNotify() {
        var eventCount = 0
        val subscription = ReadingProgressSettings.changed.subscribe { eventCount++ }
        try {
            ReadingProgressSettings.autoMarkMemorized = true
            assertEquals("only writers that call notifyChanged() notify, as before", 0, eventCount)
        } finally {
            subscription.cancel()
        }
    }

    // --- refresh() re-reads the underlying store into the snapshot ---

    @Test fun refresh_picksUpExternalStoreChange() {
        val service = ReadingProgressSettingsServiceImpl()

        // Change the store directly (bypassing the service), as another writer (e.g. sync) might.
        ReadingProgressSettings.memorizeIncludeReference = false
        assertTrue(service.snapshot.value.memorizeIncludeReference) // stale until refresh()

        service.refresh()

        assertFalse(service.snapshot.value.memorizeIncludeReference)
    }
}
