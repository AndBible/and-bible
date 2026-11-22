/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 * Licensed under the GNU General Public License, version 3 or later.
 */
package net.bible.android.view.activity.page

import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals

/** #4200: the short variant of the reading title (what the toolbar falls back to when the full one does not fit). */
class PageShortTitleTest {
    private val kjv = Versifications.instance().getVersification("KJV")

    private fun <T> inLocale(tag: String, block: () -> T): T {
        val previous = LocaleProviderManager.getLocaleProvider()
        val oldFull = BookName.isFullBookName()
        try {
            LocaleProviderManager.setLocaleProvider(object : LocaleProvider { override fun getUserLocale() = KLocale(tag) })
            BookName.setFullBookName(true)
            return block()
        } finally {
            BookName.setFullBookName(oldFull)
            LocaleProviderManager.setLocaleProvider(previous)
        }
    }

    @Test fun finnishVerseHasShortBookNameVariant() = inLocale("fi") {
        assertEquals("2. Mooseksen kirja 1:2" to "2Moos 1:2", pageTitlesFor(Verse(kjv, BibleBook.EXOD, 1, 2)))
    }

    @Test fun finnishWholeChapterHasShortVariant() = inLocale("fi") {
        assertEquals("2. Mooseksen kirja 1" to "2Moos 1", pageTitlesFor(Verse(kjv, BibleBook.EXOD, 1, 0)))
    }

    @Test fun shortVariantDoesNotLeaveShortNamesOnForLaterFullTitles() = inLocale("fi") {
        pageTitlesFor(Verse(kjv, BibleBook.EXOD, 1, 2))
        assertEquals("2. Mooseksen kirja 1:2", pageTitleFor(Verse(kjv, BibleBook.EXOD, 1, 2)))
    }

    @Test fun nonVerseKeyHasNoShortVariant() = inLocale("fi") {
        val (full, short) = pageTitlesFor(DefaultLeafKeyList("Aaron"))
        assertEquals("Aaron", full)
        assertEquals(null, short)
    }

    @Test fun noKeyHasNoShortVariant() = assertEquals(null to null, pageTitlesFor(null))
}
