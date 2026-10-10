package net.bible.android.control.navigation

import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.Versification
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.util.Locale

class BibleBookAlphabeticalComparatorTest {
    @Test
    fun orderingUsesProviderLocaleRatherThanPlatformDefaultAndMovesNumbersToTheEnd() {
        val savedProvider = LocaleProviderManager.getLocaleProvider()
        val savedDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val versification = Mockito.mock(Versification::class.java)
            Mockito.`when`(versification.getShortName(BibleBook.GEN)).thenReturn("2I")
            Mockito.`when`(versification.getShortName(BibleBook.EXOD)).thenReturn("1I")
            Mockito.`when`(versification.getShortName(BibleBook.LEV)).thenReturn("Z")
            val comparator = BibleBookAlphabeticalComparator(versification)
            val books = listOf(BibleBook.GEN, BibleBook.LEV, BibleBook.EXOD)

            LocaleProviderManager.setLocaleProvider(object : LocaleProvider {
                override fun getUserLocale() = KLocale("tr", "TR", "POSIX")
            })
            // Turkish I becomes dotless i, after z; numbers sort after the name, not before Z.
            assertEquals(listOf(BibleBook.LEV, BibleBook.EXOD, BibleBook.GEN), books.sortedWith(comparator))

            LocaleProviderManager.setLocaleProvider(object : LocaleProvider {
                override fun getUserLocale() = KLocale.ENGLISH
            })
            assertEquals(listOf(BibleBook.EXOD, BibleBook.GEN, BibleBook.LEV), books.sortedWith(comparator))
        } finally {
            LocaleProviderManager.setLocaleProvider(savedProvider)
            Locale.setDefault(savedDefault)
        }
    }
}
