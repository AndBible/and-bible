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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.bookAndKeyListOf
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.DefaultLeafKeyList
import org.crosswire.jsword.passage.VerseRangeFactory
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 19 (T9 findings-fix batch, phase G): a `BookAndKeyList` is ASSEMBLED, not asked for by the
 * user -- `LinkControl.getRobinsonMorphologyKey` (`LinkControl.kt:329`) builds one `BookAndKey` per
 * document `SwordDocumentFacade.defaultRobinsonGreekMorphology` returns, and that facade falls back
 * to `FakeBookFactory.giveDoesNotExist("Robinson", DICTIONARY)` -- a `Book` that is deliberately never
 * registered in `Books.installed()` -- when no real Robinson morphology module is installed. Before
 * this fix, `CurrentGeneralBookPage`'s `BookAndKeyList` arm caught every `OsisError` the same way and
 * rendered `SwordContentFacade.readOsisFragment`'s not-installed error card (with its own download
 * link) as a fragment the user never asked for.
 *
 * A key that is genuinely missing from an INSTALLED document is a different case: that omission is
 * information the multi-document view must still surface, not noise to hide. Both situations throw
 * the SAME exception class, `DocumentNotFound` (`SwordContentFacade.kt:170` for "not installed",
 * `:174` for "key not in document") -- so the fix cannot distinguish them by catch-type alone; it must
 * re-check `Books.installed()` for the specific document inside the catch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MultiFragmentUninstalledDocumentTest {

    private val fakeInstalledInitials = "FakeInstalledCommentary"

    private fun createPageManager(): CurrentPageManager =
        CurrentPageManager(
            mock(BibleTraverser::class.java),
            mock(BookmarkControl::class.java),
            mock(WindowControl::class.java),
        )

    /**
     * A book that IS registered in `Books.installed()` but whose backend is `NullBackend`, which
     * unconditionally answers `contains(key) == false` -- so ANY key on it is "not found in document",
     * never "not installed". This is how an installed document contributes a genuinely-missing-key
     * card, as opposed to `FakeBookFactory.giveDoesNotExist`, which is never registered at all.
     */
    private fun installedEmptyBook(): Book =
        FakeBookFactory.createFakeRepoBook(
            fakeInstalledInitials,
            """[$fakeInstalledInitials]
Description=$fakeInstalledInitials
Abbreviation=$fakeInstalledInitials
Category=Commentaries
Encoding=UTF-8
LCSH=Bible--Commentaries.
Versification=KJVA""",
            "",
        ).also { Books.installed().addBook(it) }

    @After
    fun tearDown() {
        Books.installed().getBook(fakeInstalledInitials)?.let { Books.installed().removeBook(it) }
        DatabaseResetter.resetDatabase()
    }

    private fun kjv(): SwordBook = Books.installed().getBook("KJV") as SwordBook

    private fun fragmentCount(osisFragmentsJson: String): Int =
        Regex("'bookInitials':").findAll(osisFragmentsJson).count()

    private fun osisFragmentsJson(page: CurrentGeneralBookPage): String {
        val doc = page.currentPageContent as MultiFragmentDocument
        return doc.asHashMap["osisFragments"] as String
    }

    /**
     * The Robinson card: one installed document (KJV, at a real verse) plus one uninstalled document
     * (the fallback Robinson dictionary) must produce ONE fragment -- the uninstalled entry drops out
     * silently, contributing no error card.
     */
    @Test
    fun anUninstalledDocumentDropsOutSilentlyLeavingOnlyTheInstalledFragment() {
        val robinson = FakeBookFactory.giveDoesNotExist("Robinson", BookCategory.DICTIONARY)
        assertThat(
            "sanity: Robinson must genuinely be absent from the installed registry, or this test " +
                "proves nothing",
            Books.installed().getBook("Robinson"), nullValue(),
        )

        val page = createPageManager().currentGeneralBook
        val kjv = kjv()
        val genesis11 = VerseRangeFactory.fromString(kjv.versification, "Gen.1.1")
        page.doSetKey(
            bookAndKeyListOf(
                listOf(
                    BookAndKey(genesis11, kjv),
                    BookAndKey(DefaultLeafKeyList("some-entry", "some-entry"), robinson),
                )
            )
        )

        val fragmentsJson = osisFragmentsJson(page)
        assertThat(
            "the uninstalled Robinson entry must drop out, leaving only the KJV fragment. " +
                "fragments: $fragmentsJson",
            fragmentCount(fragmentsJson), equalTo(1),
        )
        assertThat(fragmentsJson, containsString("'bookInitials': `KJV`"))
        assertThat(fragmentsJson, not(containsString("Robinson")))
    }

    /**
     * A key genuinely missing from an INSTALLED document must still produce its card -- that is
     * information the reader needs, not noise from an assembled key.
     */
    @Test
    fun aKeyGenuinelyMissingFromAnInstalledDocumentStillProducesItsCard() {
        val book = installedEmptyBook()
        assertThat(
            "sanity: the fake commentary must genuinely be registered as installed, or this test " +
                "proves nothing",
            Books.installed().getBook(book.initials), notNullValue(),
        )

        val page = createPageManager().currentGeneralBook
        page.doSetKey(
            bookAndKeyListOf(listOf(BookAndKey(DefaultLeafKeyList("missing-entry", "missing-entry"), book)))
        )

        val fragmentsJson = osisFragmentsJson(page)
        assertThat(
            "an installed document whose key is missing is information, not noise -- it must still " +
                "produce a card. fragments: $fragmentsJson",
            fragmentCount(fragmentsJson), equalTo(1),
        )
        assertThat(fragmentsJson, containsString("was not found in document"))
    }
}
