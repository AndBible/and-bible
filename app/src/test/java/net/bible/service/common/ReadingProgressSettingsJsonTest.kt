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

package net.bible.service.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.bible.android.TestBibleApplication
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F131: the `update_reading_progress_settings` message must carry the full bundle, because the JS side
 * merges it with `Object.assign`; a field omitted for equalling its default would never be reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingProgressSettingsJsonTest {
    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    private val allKeys = setOf(
        "autoMarkMemorized", "memorizeTypeFullWords", "memorizeWordVisibility",
        "memorizeErrorHeatmap", "memorizeScrambleHideUsed", "memorizeIncludeReference",
    )

    private fun currentJson(): JsonObject = Json.parseToJsonElement(ReadingProgressSettings.getBundleAsJson()).jsonObject

    @Test fun getBundleAsJson_withAllDefaults_containsEveryKey() {
        val json = currentJson()

        assertEquals(allKeys, json.keys)
        assertEquals(ReadingProgressSettings.getBundle(), Json.decodeFromString<ReadingProgressSettingsBundle>(json.toString()))
    }

    @Test fun getBundleAsJson_afterTogglingBackToDefault_reportsTheDefault() {
        ReadingProgressSettings.memorizeIncludeReference = false
        assertEquals(false, currentJson().getValue("memorizeIncludeReference").jsonPrimitive.boolean)

        ReadingProgressSettings.memorizeIncludeReference = true
        assertEquals(true, currentJson().getValue("memorizeIncludeReference").jsonPrimitive.boolean)
    }
}
