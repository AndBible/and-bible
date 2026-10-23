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

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** Locks resource-backed outputs measured with TestBibleApplication on Robolectric SDK 33. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class LocaleFormattingProbeTest {
    @Test fun moduleSizeFormattingUsesConfiguredResourceLocale() {
        val base = ApplicationProvider.getApplicationContext<Context>()

        mapOf(
            "fi" to "1,5 MB",
            "ar" to "١٫٥ ميجابايت",
        ).forEach { (requestedTag, expected) ->
            val requestedLocale = Locale.forLanguageTag(requestedTag)
            val configuration = Configuration(base.resources.configuration).apply {
                setLocale(requestedLocale)
            }
            val localizedContext = base.createConfigurationContext(configuration)
            val effectiveLocale = localizedContext.resources.configuration.locales[0]
            val result = localizedContext.getString(R.string.module_size_megabytes, 1.5)

            assertEquals("resource context must use requested language", requestedLocale.language, effectiveLocale.language)
            assertEquals("resource-backed module size in $requestedTag", expected, result)
        }
    }
}
