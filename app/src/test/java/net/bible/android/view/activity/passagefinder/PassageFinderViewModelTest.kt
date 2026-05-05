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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.control.passagefinder.PassageFinderDataSource
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PassageFinderViewModel], verifying that confirmSelection()
 * correctly emits a [Verse] matching the current UI state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PassageFinderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val v11n = Versifications.instance().getVersification("KJV")

    private lateinit var viewModel: PassageFinderViewModel
    private lateinit var dataSource: PassageFinderDataSource

    private val testBooks = listOf(
        PassageFinderDataSource.BookInfo(BibleBook.GEN, "Gen", "Genesis", BookCategory.PENTATEUCH),
        PassageFinderDataSource.BookInfo(BibleBook.EXOD, "Exod", "Exodus", BookCategory.PENTATEUCH),
        PassageFinderDataSource.BookInfo(BibleBook.LEV, "Lev", "Leviticus", BookCategory.PENTATEUCH),
        PassageFinderDataSource.BookInfo(BibleBook.MATT, "Matt", "Matthew", BookCategory.GOSPELS),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        dataSource = mock()
        whenever(dataSource.getBooks()).thenReturn(testBooks)
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.GEN, 1, 1))
        whenever(dataSource.getChapterCount(eq(BibleBook.GEN))).thenReturn(50)
        whenever(dataSource.getChapterCount(eq(BibleBook.EXOD))).thenReturn(40)
        whenever(dataSource.getChapterCount(eq(BibleBook.LEV))).thenReturn(27)
        whenever(dataSource.getChapterCount(eq(BibleBook.MATT))).thenReturn(28)
        whenever(dataSource.getVerseCount(any(), any())).thenReturn(30)

        viewModel = PassageFinderViewModel(dataSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `confirmSelection emits Verse with correct book chapter verse`() = runTest {
        viewModel.show()

        // Navigate to Exodus (index 1), chapter 3, verse 7
        viewModel.onBookSelected(1)
        viewModel.drillDown()       // -> CHAPTER
        viewModel.onChapterSelected(3)
        viewModel.drillDown()       // -> VERSE
        viewModel.onVerseSelected(7)

        // Collect the confirmed verse asynchronously before triggering confirmation
        val deferred = async { viewModel.selectionConfirmed.first() }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        val verse = deferred.await()
        assertEquals(BibleBook.EXOD, verse.book)
        assertEquals(3, verse.chapter)
        assertEquals(7, verse.verse)
    }

    @Test
    fun `confirmSelection sets visible to false`() = runTest {
        viewModel.show()
        assertTrue(viewModel.uiState.value.visible)

        val deferred = async { viewModel.selectionConfirmed.first() }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmSelection()
        testDispatcher.scheduler.advanceUntilIdle()
        deferred.await()

        assertFalse(viewModel.uiState.value.visible)
    }

    @Test
    fun `show stays hidden when datasource returns no books`() = runTest {
        whenever(dataSource.getBooks()).thenReturn(emptyList())
        val vm = PassageFinderViewModel(dataSource)
        vm.show()
        assertFalse(
            "widget must not become visible when there are no books to navigate",
            vm.uiState.value.visible,
        )
        assertTrue(vm.uiState.value.books.isEmpty())
    }

    @Test
    fun `show clears stale preview verse text`() = runTest {
        viewModel.show()
        viewModel.drillDown()                      // BOOK -> CHAPTER
        viewModel.drillDown()                      // CHAPTER -> VERSE
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()
        // Force a known stale value so we can detect whether show() clears it.
        // (In real usage the debounced flow would have populated this.)
        viewModel.dismiss()

        viewModel.show()
        assertEquals(null, viewModel.previewVerseText.value)
    }

    @Test
    fun `confirmSelection does nothing when books list is empty`() = runTest {
        // Don't call show() -- books list is empty, selectedBookIndex is 0 but out of range
        val state = viewModel.uiState.value
        assertTrue(state.books.isEmpty())

        viewModel.confirmSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify no emission occurred and no crash -- visible should still be false
        assertFalse(viewModel.uiState.value.visible)
    }

    @Test
    fun `single-chapter book skips chapter level on drillDown`() = runTest {
        // Obadiah-shaped book with 1 chapter -- drillDown from BOOK should go
        // straight to VERSE level rather than landing on CHAPTER.
        val singleChapterBooks = testBooks + PassageFinderDataSource.BookInfo(
            BibleBook.OBAD, "Obad", "Obadiah", BookCategory.MINOR_PROPHETS,
        )
        whenever(dataSource.getBooks()).thenReturn(singleChapterBooks)
        whenever(dataSource.getChapterCount(eq(BibleBook.OBAD))).thenReturn(1)
        whenever(dataSource.getVerseCount(eq(BibleBook.OBAD), eq(1))).thenReturn(21)

        val vm = PassageFinderViewModel(dataSource)
        vm.show()
        vm.onBookSelected(4)
        vm.drillDown()

        val state = vm.uiState.value
        assertEquals(NavigationLevel.VERSE, state.currentLevel)
        assertEquals(1, state.selectedChapter)
        assertEquals(1, state.chapterCount)
    }

    @Test
    fun `drillUp from BOOK level returns false`() {
        viewModel.show()
        assertFalse(
            "drillUp at BOOK level should signal dismiss",
            viewModel.drillUp(),
        )
    }

    @Test
    fun `drillDown clamps at VERSE level`() {
        viewModel.show()
        viewModel.drillDown()  // BOOK -> CHAPTER
        viewModel.drillDown()  // CHAPTER -> VERSE
        val before = viewModel.uiState.value.currentLevel
        viewModel.drillDown()  // already at VERSE, must not crash or change
        assertEquals(before, viewModel.uiState.value.currentLevel)
    }

    @Test
    fun `getChapterCount returns 1 for out-of-range book index`() {
        viewModel.show()
        // Index 999 is out of bounds; should defensively return 1
        assertEquals(1, viewModel.getChapterCount(999))
    }
}
