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
package net.bible.android.control.page

import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #4028: the book name a Bible document sends to BibleView (the verse menu's title) is in JSword's user locale, the
 * app language on every platform, not the platform default locale (the system language on the desktop and iOS).
 */
class BibleBookNameLocaleTest {
    @Test fun bibleBookNameFollowsTheJswordUserLocaleNotThePlatformDefault() {
        val previousProvider = LocaleProviderManager.getLocaleProvider()
        val previousDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            LocaleProviderManager.setLocaleProvider(object : LocaleProvider {
                override fun getUserLocale() = KLocale("fi")
            })
            val kjv = checkNotNull(Versifications.instance().getVersification("KJV"))
            assertEquals("Saarnaaja", bibleBookName(kjv, BibleBook.ECCL))
        } finally {
            LocaleProviderManager.setLocaleProvider(previousProvider)
            Locale.setDefault(previousDefault)
        }
    }
}
