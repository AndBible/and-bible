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
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PassageFinderViewModel]: the selection state machine, the clamping it
 * applies when a book or chapter change makes the current selection impossible, and the
 * reader-follow path that keeps the widget showing whatever is on screen behind it.
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

    /**
     * Builds the loaded book list the launcher would hand to [PassageFinderViewModel.show],
     * deriving each chapter count from the stubbed data source.
     */
    private fun bookList(
        books: List<PassageFinderDataSource.BookInfo> = testBooks,
    ) = PassageFinderDataSource.BookList(
        books,
        IntArray(books.size) { dataSource.getChapterCount(books[it].book) },
    )

    // ---- followCurrentVerse: the widget tracking the reader scrolling behind it -------

    @Test
    fun `followCurrentVerse moves the selection and the open-book marker to the reader`() = runTest {
        viewModel.show(bookList())
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.MATT, 5, 9))

        viewModel.followCurrentVerse()

        val state = viewModel.uiState.value
        assertEquals(3, state.selectedBookIndex)
        assertEquals(5, state.selectedChapter)
        assertEquals(9, state.selectedVerse)
        // The marker for "what the reader is actually showing" has to move too, or it
        // would keep pointing at the book the reader has scrolled away from.
        assertEquals(3, state.openBookIndex)
        assertEquals(28, state.chapterCount)
    }

    @Test
    fun `followCurrentVerse is ignored while the widget is hidden`() = runTest {
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.MATT, 5, 9))

        viewModel.followCurrentVerse()

        assertFalse(viewModel.uiState.value.visible)
        assertEquals(0, viewModel.uiState.value.selectedBookIndex)
    }

    @Test
    fun `followCurrentVerse ignores a book the module does not contain`() = runTest {
        viewModel.show(bookList())
        viewModel.onBookSelected(1)
        // Reader is on a book absent from this module's list — leave the widget alone
        // rather than snapping it to an arbitrary index.
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.JOHN, 3, 16))

        viewModel.followCurrentVerse()

        assertEquals(1, viewModel.uiState.value.selectedBookIndex)
    }

    @Test
    fun `followCurrentVerse clamps a verse beyond the chapter`() = runTest {
        viewModel.show(bookList())
        whenever(dataSource.getVerseCount(eq(BibleBook.LEV), eq(2))).thenReturn(16)
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.LEV, 2, 99))

        viewModel.followCurrentVerse()

        val state = viewModel.uiState.value
        assertEquals(2, state.selectedBookIndex)
        assertEquals(16, state.selectedVerse)
    }

    @Test
    fun `followCurrentVerse leaves state untouched when nothing changed`() = runTest {
        viewModel.show(bookList())
        val before = viewModel.uiState.value

        viewModel.followCurrentVerse()

        // Same instance, not merely an equal copy: re-emitting would make the view treat
        // it as a fresh selection and re-centre strips that are already in place.
        assertSame(before, viewModel.uiState.value)
    }

    @Test
    fun `confirmSelection emits Verse with correct book chapter verse`() = runTest {
        viewModel.show(bookList())

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
        viewModel.show(bookList())
        assertTrue(viewModel.uiState.value.visible)

        val deferred = async { viewModel.selectionConfirmed.first() }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmSelection()
        testDispatcher.scheduler.advanceUntilIdle()
        deferred.await()

        assertFalse(viewModel.uiState.value.visible)
    }

    @Test
    fun `show stays hidden when the module has no books`() = runTest {
        val vm = PassageFinderViewModel(dataSource)
        vm.show(bookList(emptyList()))
        assertFalse(
            "widget must not become visible when there are no books to navigate",
            vm.uiState.value.visible,
        )
        assertTrue(vm.uiState.value.books.isEmpty())
    }

    @Test
    fun `show clears stale preview verse text`() = runTest {
        // The stale value has to be real, or this test passes against a show() that never
        // clears anything: an unstubbed getVerseText leaves the preview null throughout.
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("In the beginning")

        // Let the ViewModel's verse-text collector actually start before emitting into it.
        // Its flow has no replay, so a selection made while the collector is still only
        // queued on the test dispatcher would be dropped and the preview would stay null —
        // which would quietly turn this into a test that asserts nothing.
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()                      // BOOK -> CHAPTER
        viewModel.drillDown()                      // CHAPTER -> VERSE
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(PreviewVerseText.Ready("In the beginning"), viewModel.previewVerseText.value)

        viewModel.dismiss()
        viewModel.show(bookList())

        assertEquals(PreviewVerseText.None, viewModel.previewVerseText.value)
    }

    // ---- Pending preview state: dots instead of the previous verse's words ------------

    @Test
    fun `picking a new verse drops the loaded text for the pending state`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("In the beginning")
        // Let the verse-text collector start before emitting into it; its flow has no
        // replay, so a selection made while the collector is still queued on the test
        // dispatcher is dropped and the assertions below would test nothing.
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()                      // BOOK -> CHAPTER
        viewModel.drillDown()                      // CHAPTER -> VERSE
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(PreviewVerseText.Ready("In the beginning"), viewModel.previewVerseText.value)

        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("And God said")
        viewModel.onVerseSelected(6)

        // Scheduler deliberately NOT advanced: this is the window the bug lived in, where
        // the reference line already said verse 6 while the bubble still showed verse 5's
        // words — a wrong answer rather than a pending one.
        assertEquals(PreviewVerseText.Loading, viewModel.previewVerseText.value)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(PreviewVerseText.Ready("And God said"), viewModel.previewVerseText.value)
    }

    @Test
    fun `drilling into VERSE level leaves the preview pending`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("In the beginning")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()                      // BOOK -> CHAPTER
        viewModel.drillDown()                      // CHAPTER -> VERSE

        assertEquals(
            "drillDown requests verse text, so it must arm the pending state too",
            PreviewVerseText.Loading,
            viewModel.previewVerseText.value,
        )
    }

    @Test
    fun `onChapterSelected leaves the preview pending`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("In the beginning")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.onChapterSelected(3)

        assertEquals(PreviewVerseText.Loading, viewModel.previewVerseText.value)
    }

    @Test
    fun `a failed verse read ends in the no-text state`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any()))
            .thenThrow(RuntimeException("module read failed"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()
        viewModel.drillDown()
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()

        // A read that throws must land in None, never stay pending: the placeholder would
        // otherwise sit in the bubble forever waiting for text that is never coming.
        assertEquals(PreviewVerseText.None, viewModel.previewVerseText.value)
    }

    @Test
    fun `a blank verse read ends in the no-text state`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()
        viewModel.drillDown()
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()

        // Blank is nothing to preview, not something still loading.
        assertEquals(PreviewVerseText.None, viewModel.previewVerseText.value)
    }

    @Test
    fun `drillUp out of VERSE level clears the preview to the no-text state`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("In the beginning")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()
        viewModel.drillDown()
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(PreviewVerseText.Ready("In the beginning"), viewModel.previewVerseText.value)

        viewModel.drillUp()

        // Retreating out of verse level is not a pending read — there is nothing to
        // preview at chapter level, so the placeholder must not appear.
        assertEquals(PreviewVerseText.None, viewModel.previewVerseText.value)
    }

    @Test
    fun `dismiss keeps the loaded preview text so the bubble does not shrink mid-fade`() = runTest {
        whenever(dataSource.getVerseText(any(), any(), any())).thenReturn("In the beginning")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.show(bookList())
        viewModel.drillDown()
        viewModel.drillDown()
        viewModel.onVerseSelected(5)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismiss()

        // Deliberate: the bubble is still fading out when dismiss lands. Dropping its text
        // here would make it visibly collapse to reference-only halfway through the fade.
        // show() is what clears the preview, on the way back in.
        assertEquals(PreviewVerseText.Ready("In the beginning"), viewModel.previewVerseText.value)
    }

    @Test
    fun `confirmSelection does nothing when books list is empty`() = runTest {
        // Don't call show() -- books list is empty, selectedBookIndex is 0 but out of range
        assertTrue(viewModel.uiState.value.books.isEmpty())

        // Actually watch the channel: asserting on `visible` alone would pass even if a
        // verse were emitted, since it was never true to begin with.
        val emissions = mutableListOf<Verse>()
        val collector = launch { viewModel.selectionConfirmed.collect { emissions.add(it) } }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("no verse may be confirmed with no books to confirm", emissions.isEmpty())
        assertFalse(viewModel.uiState.value.visible)
        collector.cancel()
    }

    @Test
    fun `onBookSelected clamps selectedVerse to new books verseCount`() = runTest {
        // Start in Genesis on a deep verse, then switch to a book whose chapter 1
        // has fewer verses. selectedVerse must be clamped or VerseStrip's
        // scrollToItem(selectedVerse - 1) would index out of range.
        whenever(dataSource.getChapterCount(eq(BibleBook.GEN))).thenReturn(50)
        whenever(dataSource.getVerseCount(eq(BibleBook.GEN), eq(1))).thenReturn(31)
        whenever(dataSource.getVerseCount(eq(BibleBook.MATT), eq(1))).thenReturn(25)
        whenever(dataSource.getCurrentVerse()).thenReturn(Verse(v11n, BibleBook.GEN, 1, 31))

        val vm = PassageFinderViewModel(dataSource)
        vm.show(bookList())
        assertEquals(31, vm.uiState.value.selectedVerse)
        vm.onBookSelected(3)  // switch to Matthew
        assertEquals(
            "selectedVerse should reset to 1 on book change so it can't outrun the new verseCount",
            1,
            vm.uiState.value.selectedVerse,
        )
    }

    @Test
    fun `single-chapter book skips chapter level on drillDown`() = runTest {
        // Obadiah-shaped book with 1 chapter -- drillDown from BOOK should go
        // straight to VERSE level rather than landing on CHAPTER.
        val singleChapterBooks = testBooks + PassageFinderDataSource.BookInfo(
            BibleBook.OBAD, "Obad", "Obadiah", BookCategory.MINOR_PROPHETS,
        )
        whenever(dataSource.getChapterCount(eq(BibleBook.OBAD))).thenReturn(1)
        whenever(dataSource.getVerseCount(eq(BibleBook.OBAD), eq(1))).thenReturn(21)

        val vm = PassageFinderViewModel(dataSource)
        vm.show(bookList(singleChapterBooks))
        vm.onBookSelected(4)
        vm.drillDown()

        val state = vm.uiState.value
        assertEquals(NavigationLevel.VERSE, state.currentLevel)
        assertEquals(1, state.selectedChapter)
        assertEquals(1, state.chapterCount)
    }

    @Test
    fun `drillDown preserves chapter picked on a non-open book`() = runTest {
        // Open book is Genesis (index 0). Move to Exodus (a non-open book), scroll the
        // chapter strip to chapter 5 while still at BOOK level, then drill down.
        // drillDown must keep chapter 5 rather than snapping back to chapter 1.
        viewModel.show(bookList())
        viewModel.onBookSelected(1)            // -> Exodus, selectedChapter reset to 1
        viewModel.onChapterSelected(5)         // user scrolls chapter strip to chapter 5
        assertEquals(5, viewModel.uiState.value.selectedChapter)

        viewModel.drillDown()                  // BOOK -> CHAPTER

        val state = viewModel.uiState.value
        assertEquals(NavigationLevel.CHAPTER, state.currentLevel)
        assertEquals(
            "chapter chosen on a non-open book must survive drillDown",
            5,
            state.selectedChapter,
        )
    }

    @Test
    fun `drillUp from BOOK level returns false`() {
        viewModel.show(bookList())
        assertFalse(
            "drillUp at BOOK level should signal dismiss",
            viewModel.drillUp(),
        )
    }

    @Test
    fun `drillDown clamps at VERSE level`() {
        viewModel.show(bookList())
        viewModel.drillDown()  // BOOK -> CHAPTER
        viewModel.drillDown()  // CHAPTER -> VERSE
        val before = viewModel.uiState.value.currentLevel
        viewModel.drillDown()  // already at VERSE, must not crash or change
        assertEquals(before, viewModel.uiState.value.currentLevel)
    }
}
