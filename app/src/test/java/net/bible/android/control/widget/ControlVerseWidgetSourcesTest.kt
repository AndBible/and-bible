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
package net.bible.android.control.widget

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.readingplan.ReadingPlanControl
import net.bible.android.platform.AndroidLocalCalendar
import net.bible.android.platform.androidSaxTextPolicy
import net.bible.test.DatabaseResetter.resetDatabase
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testOrderedLauncher
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real [ControlVerseWidgetSources] over the test SWORD modules (`~/.sword`, KJV and the German-versified FinRK). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ControlVerseWidgetSourcesTest {
    private val settings = testAppSettings()
    private lateinit var bookmarkControl: BookmarkControl
    private lateinit var readingPlanControl: ReadingPlanControl
    private lateinit var sources: ControlVerseWidgetSources
    private var originalPlan: String? = null

    private val kjv: VerseBible get() = VerseBible("KJV", "KJV", (Books.installed().getBook("KJV") as SwordBook).versification.name)
    private val finRk: VerseBible get() = VerseBible("FinRK", "FinRK", (Books.installed().getBook("FinRK") as SwordBook).versification.name)

    @Before fun setUp() {
        bookmarkControl = BookmarkControl(mock(WindowControl::class.java), settings, testCoreStrings(), testOrderedLauncher(), androidSaxTextPolicy())
        readingPlanControl = GlobalContext.get().get()
        originalPlan = settings.getString("reading_plan", null)
        sources = ControlVerseWidgetSources(
            bookmarkControl, mock(WindowControl::class.java), readingPlanControl, testCoreStrings(), androidSaxTextPolicy(),
            AndroidLocalCalendar(), mock(WidgetStrings::class.java),
        )
    }

    @After fun tearDown() {
        settings.setString("reading_plan", originalPlan)
        resetDatabase()
    }

    private fun verse(v11n: String, ordinal: Int): Verse = Verse(Versifications.instance().getVersification(v11n), ordinal)

    /** Every pool ordinal, in order. */
    private fun all(pool: VerseOrdinals) = List(pool.size) { pool.ordinalAt(it) }

    @Test fun newTestamentPoolHasEveryRealVerseAndNoIntroductions() = runBlocking {
        val pool = sources.partOrdinals(VerseSource.RandomPart.NEW_TESTAMENT, kjv)
        val ordinals = all(pool)
        assertEquals("KJV New Testament verse count", 7957, pool.size)
        assertEquals(Verse(Versifications.instance().getVersification(kjv.v11nName), BibleBook.MATT, 1, 1).ordinal, ordinals.first())
        val last = verse(kjv.v11nName, ordinals.last())
        assertEquals(BibleBook.REV, last.book)
        assertEquals(22, last.chapter)
        assertEquals(21, last.verse)
        assertTrue("ordinals strictly increase", ordinals.zipWithNext().all { (a, b) -> a < b })
        assertTrue("no chapter 0 or verse 0", ordinals.all { verse(kjv.v11nName, it).let { v -> v.chapter >= 1 && v.verse >= 1 } })
        assertTrue("no Old Testament verse", ordinals.all { verse(kjv.v11nName, it).book.ordinal >= BibleBook.MATT.ordinal })
    }

    @Test fun psalmsAndProverbsPoolHasNoVerseZeroAndNoJobEcclesiastesOrSong() = runBlocking {
        val pool = sources.partOrdinals(VerseSource.RandomPart.PSALMS_PROVERBS, kjv)
        val verses = all(pool).map { verse(kjv.v11nName, it) }
        assertEquals("KJV Psalms 2461 + Proverbs 915", 2461 + 915, pool.size)
        assertTrue(verses.all { it.book == BibleBook.PS || it.book == BibleBook.PROV })
        assertTrue("no chapter 0 or verse 0 (Psalm titles are verse 0 in some versifications)", verses.all { it.chapter >= 1 && it.verse >= 1 })
        assertEquals(BibleBook.PS, verses.first().book)
        assertEquals(BibleBook.PROV, verses.last().book)
    }

    @Test fun wholeBiblePoolIsExactlyTheRealVerses() = runBlocking {
        val pool = sources.partOrdinals(VerseSource.RandomPart.WHOLE_BIBLE, kjv)
        val verses = all(pool).map { verse(kjv.v11nName, it) }
        assertEquals("KJV verse count", 31102, pool.size)
        assertEquals(BibleBook.GEN, verses.first().book)
        assertEquals(BibleBook.REV, verses.last().book)
        assertTrue(verses.all { it.chapter >= 1 && it.verse >= 1 })
    }

    @Test fun germanVersificationPoolsUseThatVersificationsOrdinals() = runBlocking {
        val pool = sources.partOrdinals(VerseSource.RandomPart.NEW_TESTAMENT, finRk)
        val v11n = Versifications.instance().getVersification(finRk.v11nName)
        assertEquals(Verse(v11n, BibleBook.MATT, 1, 1).ordinal, pool.ordinalAt(0))
        assertTrue(all(pool).all { verse(finRk.v11nName, it).let { v -> v.chapter >= 1 && v.verse >= 1 } })
    }

    @Test fun unknownVersificationGivesAnEmptyPool() = runBlocking {
        assertEquals(0, sources.partOrdinals(VerseSource.RandomPart.WHOLE_BIBLE, VerseBible("X", "X", "NoSuchV11n")).size)
    }

    @Test fun rangeTextJoinsTheVersesAndReferenceNamesTheRange() = runBlocking {
        val v11n = Versifications.instance().getVersification(kjv.v11nName)
        val ref = VerseRef("KJV", kjv.v11nName, Verse(v11n, BibleBook.PS, 23, 1).ordinal, Verse(v11n, BibleBook.PS, 23, 2).ordinal)
        val text = sources.text(ref, kjv)
        assertTrue(text, text.contains("shepherd") && text.contains("green pastures"))
        assertTrue(sources.referenceName(ref, kjv), sources.referenceName(ref, kjv).contains("23:1-2"))
        assertEquals("Ps.23.1-Ps.23.2", sources.osisRef(ref, kjv))
    }

    @Test fun textOfAnUninstalledBibleIsEmpty() = runBlocking {
        val v11n = Versifications.instance().getVersification(kjv.v11nName)
        val o = Verse(v11n, BibleBook.PS, 23, 1).ordinal
        assertEquals("", sources.text(VerseRef("GONE", kjv.v11nName, o, o), VerseBible("GONE", "GONE", kjv.v11nName)))
    }

    @Test fun refInAnotherVersificationIsConvertedForTextAndReference() = runBlocking {
        // Joel 2:28 in KJV is Joel 3:1 in the German versification of FinRK
        val kjvV11n = Versifications.instance().getVersification(kjv.v11nName)
        val o = Verse(kjvV11n, BibleBook.JOEL, 2, 28).ordinal
        val ref = VerseRef("KJV", kjv.v11nName, o, o)
        assertEquals("Joel.3.1", sources.osisRef(ref, finRk))
        assertTrue(sources.referenceName(ref, finRk), sources.referenceName(ref, finRk).contains("3:1"))
        assertTrue("converted verse has text in FinRK", sources.text(ref, finRk).isNotBlank())
        assertEquals("Joel.2.28", sources.osisRef(ref, kjv))
    }

    @Test fun readingPlanPassageIsNullWithoutAPlan() = runBlocking {
        settings.setString("reading_plan", null)
        assertNull(sources.readingPlanPassage(kjv))
    }

    @Test fun readingPlanPassageIsTheFirstReadingOfTheCurrentDay() = runBlocking {
        readingPlanControl.setReadingPlan("y2ot1ntps2")
        val passage = sources.readingPlanPassage(kjv)
        assertNotNull(passage)
        passage!!
        assertEquals("KJV", passage.bookInitials)
        assertTrue(passage.startOrdinal <= passage.endOrdinal)
        val first = verse(passage.v11nName, passage.startOrdinal)
        assertTrue("a real verse of the plan: $first", first.chapter >= 1)
        assertFalse(sources.referenceName(passage, kjv).isBlank())
    }

    @Test fun biblesListsInstalledSwordBiblesWithTheirVersification() = runBlocking {
        val list = sources.bibles().associateBy { it.initials }
        assertEquals("German", list.getValue("FinRK").v11nName)
        assertEquals(kjv.v11nName, list.getValue("KJV").v11nName)
        assertEquals(list.getValue("KJV"), sources.bible("KJV"))
        assertNull(sources.bible("GONE"))
    }
}
