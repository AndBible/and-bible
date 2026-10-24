/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.platform

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidPlatformLocalesTest {
    @Test fun defaultLanguageIsTheRawJdkCodeWithoutNormalization() {
        val saved = Locale.getDefault()
        try {
            for (locale in listOf(Locale("he", "IL"), Locale("in", "ID"), Locale("yi"), Locale("fi", "FI"))) {
                Locale.setDefault(locale)
                assertEquals(Locale.getDefault().language, AndroidPlatformLocales().defaultLanguage)
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun defaultLanguagePassesLegacyCodesThroughByteIdentically() {
        // On JDK 17+ Locale("he").language is already "he", so inject the raw legacy code through the seam.
        for (legacy in listOf("iw", "in", "ji")) {
            assertEquals(legacy, AndroidPlatformLocales(languageOf = { legacy }).defaultLanguage)
        }
    }

    @Test fun iso3ByIso2MatchesTheOldLanguageGroupingComputation() {
        val old = Locale.getISOLanguages().mapNotNull { iso2 ->
            val iso3 = try {
                Locale(iso2).isO3Language
            } catch (e: java.util.MissingResourceException) {
                null
            }
            if (iso3 == null) null else iso2 to iso3
        }.toMap()
        val actual = AndroidPlatformLocales().iso3ByIso2()
        assertEquals("fin", actual["fi"])
        assertEquals(old, actual)
    }
}
