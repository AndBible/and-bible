package net.bible.service.sword

import org.crosswire.common.util.KLocale
import org.crosswire.jsword.internationalisation.LocaleProvider
import org.crosswire.jsword.internationalisation.LocaleProviderManager
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.PassageKeyFactory
import org.crosswire.jsword.versification.BookName
import org.crosswire.jsword.versification.system.Versifications
import kotlin.test.Test
import kotlin.test.assertEquals

class BookAndKeyListTitleTest {
    private val kjv = Versifications.instance().getVersification("KJV")

    private fun references(vararg refs: String) = bookAndKeyListOf(refs.map {
        BookAndKey(PassageKeyFactory.instance().getKey(kjv, it))
    })

    @Test
    fun titleAbbreviatesReferencesWithoutDroppingTheVerse() {
        val before = BookName.isFullBookName()
        try {
            BookName.setFullBookName(true)
            assertEquals("Ecc 7:29, Gen 1:26", references("Eccl.7.29", "Gen.1.26").getName())
            assertEquals(true, BookName.isFullBookName())
        } finally {
            BookName.setFullBookName(before)
        }
    }

    @Test
    fun duplicateReferencesAppearOnlyOnceInTheTitleWithoutChangingContent() {
        val list = references("Eccl.7.29", "Gen.1.26", "Eccl.7.29")
        assertEquals("Ecc 7:29, Gen 1:26", list.getName())
        assertEquals(3, list.getCardinality())
        assertEquals("null:Eccl.7.29||null:Gen.1.26||null:Eccl.7.29", list.getOsisRef())
    }

    @Test
    fun titleUsesLocalizedAbbreviations() {
        val list = references("Eccl.7.29", "Gen.1.26")
        val provider = LocaleProviderManager.getLocaleProvider()
        try {
            LocaleProviderManager.setLocaleProvider(object : LocaleProvider {
                override fun getUserLocale() = KLocale("fi")
            })
            assertEquals("Saarn 7:29, 1Moos 1:26", list.getName())
        } finally {
            LocaleProviderManager.setLocaleProvider(provider)
        }
    }

    @Test
    fun titleRetainsRangesAndChapterReferences() {
        assertEquals("Gen 1:26-28, Gen 2", references("Gen.1.26-Gen.1.28", "Gen.2").getName())
    }

    @Test
    fun unnamedCloneRecomputesTitleWhenReferencesChange() {
        val original = references("Eccl.7.29")
        val copy = original.clone()
        copy.addAll(BookAndKey(PassageKeyFactory.instance().getKey(kjv, "Gen.1.26")))
        assertEquals("Ecc 7:29", original.getName())
        assertEquals("Ecc 7:29, Gen 1:26", copy.getName())
    }

    @Test
    fun customTitlesAndTheirClonesArePreserved() {
        val list = references("Gen.1.26")
        list.setName("Dictionary lookup")
        assertEquals("Dictionary lookup", list.getName())
        assertEquals("Dictionary lookup", list.clone().getName())
        list.setName(null)
        assertEquals("Gen 1:26", list.getName())
    }

    @Test
    fun nonVerseKeysKeepTheirNames() {
        val list = bookAndKeyListOf(listOf(BookAndKey(DefaultLeafKeyList("Dictionary entry"))))
        assertEquals("Dictionary entry", list.getName())
        assertEquals("", BookAndKeyList().getName())
    }
}
