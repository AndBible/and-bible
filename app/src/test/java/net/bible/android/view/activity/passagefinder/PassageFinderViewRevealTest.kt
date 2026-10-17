/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.passagefinder.PassageFinderDataSource
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The verse strip's progressive reveal, driven through a real [PassageFinderView] and
 * [PassageFinderViewModel] wired the way [PassageFinderLauncher] wires them.
 *
 * A book change hides the verse strip until a chapter is picked. A one-chapter book has no
 * chapter level to pick from — drilling in lands straight on verse level — so unless that
 * drill reveals the strip, the user is left at verse level looking at no verses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class PassageFinderViewRevealTest {

    private val v11n = Versifications.instance().getVersification("KJV")

    private val books = listOf(
        PassageFinderDataSource.BookInfo(BibleBook.GEN, "Gen", "Genesis", BookCategory.PENTATEUCH),
        PassageFinderDataSource.BookInfo(BibleBook.EXOD, "Exod", "Exodus", BookCategory.PENTATEUCH),
        PassageFinderDataSource.BookInfo(BibleBook.JUDE, "Jude", "Jude", BookCategory.GENERAL_EPISTLES),
    )
    private val chapterCounts = intArrayOf(50, 40, 1)

    private lateinit var viewModel: PassageFinderViewModel
    private lateinit var view: PassageFinderView

    @Before
    fun setUp() {
        val dataSource: PassageFinderDataSource = mock()
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.GEN, 1, 1))
        books.forEachIndexed { i, info ->
            whenever(dataSource.getChapterCount(eq(info.book))).thenReturn(chapterCounts[i])
        }
        whenever(dataSource.getVerseCount(any(), any())).thenReturn(25)

        viewModel = PassageFinderViewModel(dataSource)
        view = PassageFinderView(RuntimeEnvironment.getApplication())
        view.setBooks(books, chapterCounts)
        // Synchronous stand-in for the launcher's state collector.
        val sync = { view.render(viewModel.uiState.value, viewModel.previewVerseText.value) }
        view.onBookSelected = { viewModel.onBookSelected(it); sync() }
        view.onChapterSelected = { viewModel.onChapterSelected(it); sync() }
        view.onDrillDown = { viewModel.drillDown(); sync() }
        viewModel.show(PassageFinderDataSource.BookList(books, chapterCounts))
        sync()
    }

    @Test
    fun `picking a one-chapter book reveals its verse strip`() {
        // The same path a tap on the spine takes: book change, then drill down.
        view.onAccessibilityAction(A11yTarget.Book(2))

        assertEquals(NavigationLevel.VERSE, viewModel.uiState.value.currentLevel)
        assertTrue("verse level must show its verses", view.isVerseStripRevealed)
    }

    @Test
    fun `picking a multi-chapter book still waits for a chapter`() {
        view.onAccessibilityAction(A11yTarget.Book(1))

        assertEquals(NavigationLevel.CHAPTER, viewModel.uiState.value.currentLevel)
        assertFalse(
            "a new multi-chapter book must not pre-commit to verse 1 before a chapter pick",
            view.isVerseStripRevealed,
        )

        view.onAccessibilityAction(A11yTarget.Chapter(3))
        assertTrue(view.isVerseStripRevealed)
    }
}
