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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import net.bible.android.control.passagefinder.PassageFinderDataSource
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook

/** The three navigation levels the user can drill through. */
enum class NavigationLevel { BOOK, CHAPTER, VERSE }

/** Represents the visible/hidden state and loaded data for the passage finder widget. */
data class PassageFinderUiState(
    val visible: Boolean = false,
    val books: List<PassageFinderDataSource.BookInfo> = emptyList(),
    /** Index of the book currently open in the Bible reader (stays fixed while scrolling). */
    val openBookIndex: Int = 0,
    /** Index of the book currently centered/selected in the scroller (changes as user scrolls). */
    val selectedBookIndex: Int = 0,
    /** Which strip level is currently active. */
    val currentLevel: NavigationLevel = NavigationLevel.BOOK,
    /** Currently selected chapter number (1-based). */
    val selectedChapter: Int = 1,
    /** Currently selected verse number (1-based). */
    val selectedVerse: Int = 1,
    /** Number of chapters in the currently selected book. */
    val chapterCount: Int = 0,
    /** Number of verses in the currently selected chapter. */
    val verseCount: Int = 0,
    /** Whether the user has interacted (scrolled) since the widget opened. */
    val hasInteracted: Boolean = false,
    /** Whether the verse-preview bubble should show. True after any chapter/verse scroll;
     *  cleared when the book changes (book scroll has no preview). */
    val showPreview: Boolean = false,
)

/**
 * State machine for the PassageFinder widget.
 *
 * Owns the book list and current selection state. Scroll offset is NOT stored here --
 * it is Compose-local state to avoid backward-write loops (see RESEARCH.md Pitfall 3).
 */
class PassageFinderViewModel(
    private val dataSource: PassageFinderDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassageFinderUiState())
    val uiState: StateFlow<PassageFinderUiState> = _uiState.asStateFlow()

    private val _selectionConfirmed = MutableSharedFlow<Verse>(
        extraBufferCapacity = 1,
        // Ensure the latest confirmation always wins: if the buffer is momentarily full
        // (e.g. rapid double-tap before the collector catches up), drop the stale value
        // rather than letting tryEmit fail and silently lose the user's selection.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Emits the confirmed [Verse] when the user finalizes their selection. Collected by Phase 4. */
    val selectionConfirmed: SharedFlow<Verse> = _selectionConfirmed.asSharedFlow()

    private val _previewVerseText = MutableStateFlow<String?>(null)
    val previewVerseText: StateFlow<String?> = _previewVerseText.asStateFlow()

    private val verseSelectionFlow = MutableSharedFlow<Triple<BibleBook, Int, Int>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        verseSelectionFlow
            .debounce(150)
            .mapLatest { (book, chapter, verse) ->
                try {
                    dataSource.getVerseText(book, chapter, verse)
                } catch (e: Exception) {
                    // Preview text is best-effort; degrade silently to no text rather
                    // than disrupting navigation, but log so failures stay diagnosable.
                    Log.d(TAG, "Failed to load preview verse text for $book $chapter:$verse", e)
                    null
                }
            }
            .onEach { text -> _previewVerseText.value = text }
            .launchIn(viewModelScope)
    }

    /**
     * Load book data and make the widget visible, centered on the currently active book.
     * Called from PassageFinderLauncher.show().
     */
    fun show() {
        val books = dataSource.getBooks()
        if (books.isEmpty()) {
            // Module yields no books — nothing to navigate. Keep the widget hidden
            // and let the caller fall back to the legacy passage chooser.
            Log.w(TAG, "Active module returned no books; not showing passage finder")
            _uiState.value = PassageFinderUiState(visible = false)
            return
        }
        val currentVerse = dataSource.getCurrentVerse()
        val currentBookIndex = books.indexOfFirst { it.book == currentVerse.book }
            .coerceAtLeast(0)
        val currentBook = books[currentBookIndex].book
        val chapterCount = dataSource.getChapterCount(currentBook)
        val chapter = currentVerse.chapter.coerceIn(1, chapterCount)
        val verseCount = dataSource.getVerseCount(currentBook, chapter)

        // Drop any preview text from a previous session — the debounced verse-text
        // flow won't repopulate it until the user actually scrolls.
        _previewVerseText.value = null

        _uiState.value = PassageFinderUiState(
            visible = true,
            books = books,
            openBookIndex = currentBookIndex,
            selectedBookIndex = currentBookIndex,
            currentLevel = NavigationLevel.BOOK,
            selectedChapter = chapter,
            selectedVerse = currentVerse.verse.coerceIn(1, verseCount),
            chapterCount = chapterCount,
            verseCount = verseCount,
            hasInteracted = false,
        )
    }

    /** Hide the widget. */
    fun dismiss() {
        _uiState.value = _uiState.value.copy(visible = false, hasInteracted = false, showPreview = false)
    }

    /**
     * Confirm the current selection and dismiss the widget.
     *
     * Builds a [Verse] from the current UI state and emits it to [selectionConfirmed]
     * for consumption by the navigation layer (Phase 4). Then dismisses the widget.
     */
    fun confirmSelection() {
        val state = _uiState.value
        val book = state.books.getOrNull(state.selectedBookIndex)?.book ?: return
        val versification = dataSource.getCurrentVerse().versification
        val verse = Verse(versification, book, state.selectedChapter, state.selectedVerse)
        _selectionConfirmed.tryEmit(verse)
        dismiss()
    }

    /**
     * Update the selected book index as the user scrolls.
     * Called from Compose via snapshotFlow on the derived selected index.
     */
    fun onBookSelected(index: Int) {
        val state = _uiState.value
        if (index in state.books.indices) {
            val bookChanged = index != state.selectedBookIndex
            val book = state.books[index].book
            val chapterCount = dataSource.getChapterCount(book)
            // When the book changes we snap back to chapter 1 / verse 1; otherwise
            // keep the user's current chapter and clamp selectedVerse against the
            // new book's verse count so VerseStrip's scrollToItem stays in range.
            val effectiveChapter = if (bookChanged) 1 else state.selectedChapter
            val verseCount = dataSource.getVerseCount(book, effectiveChapter)
            val effectiveVerse = if (bookChanged) 1
            else state.selectedVerse.coerceIn(1, verseCount.coerceAtLeast(1))
            _uiState.value = state.copy(
                selectedBookIndex = index,
                selectedChapter = effectiveChapter,
                selectedVerse = effectiveVerse,
                chapterCount = chapterCount,
                verseCount = verseCount,
                hasInteracted = true,
                // Book change invalidates any verse-level preview.
                showPreview = if (bookChanged) false else state.showPreview,
            )
        }
    }

    /**
     * Drill into the next navigation level.
     *
     * BOOK -> CHAPTER (or directly to VERSE if the book has only one chapter).
     * CHAPTER -> VERSE.
     * Does nothing if already at VERSE level.
     */
    fun drillDown() {
        val state = _uiState.value
        when (state.currentLevel) {
            NavigationLevel.BOOK -> {
                val book = state.books.getOrNull(state.selectedBookIndex) ?: return
                val chapterCount = dataSource.getChapterCount(book.book)
                // Always honor the current selection (clamped). It's already correct for
                // every book: onBookSelected resets chapter/verse to 1 on a book change,
                // and onChapterSelected/onVerseSelected track the user's picks afterwards.
                // Special-casing the open book here would discard a chapter the user just
                // scrolled to on a non-open book.
                val initialChapter = state.selectedChapter.coerceIn(1, chapterCount)

                if (chapterCount == 1) {
                    // Single-chapter book: skip chapter level, go directly to verse
                    val verseCount = dataSource.getVerseCount(book.book, 1)
                    val initialVerse = state.selectedVerse.coerceIn(1, verseCount.coerceAtLeast(1))
                    _uiState.value = state.copy(
                        currentLevel = NavigationLevel.VERSE,
                        selectedChapter = 1,
                        chapterCount = chapterCount,
                        selectedVerse = initialVerse,
                        verseCount = verseCount,
                    )
                    verseSelectionFlow.tryEmit(Triple(book.book, 1, initialVerse))
                } else {
                    val verseCount = dataSource.getVerseCount(book.book, initialChapter)
                    _uiState.value = state.copy(
                        currentLevel = NavigationLevel.CHAPTER,
                        selectedChapter = initialChapter,
                        chapterCount = chapterCount,
                        verseCount = verseCount,
                    )
                }
            }
            NavigationLevel.CHAPTER -> {
                val book = state.books.getOrNull(state.selectedBookIndex) ?: return
                val verseCount = dataSource.getVerseCount(book.book, state.selectedChapter)
                val initialVerse = state.selectedVerse.coerceIn(1, verseCount.coerceAtLeast(1))
                _uiState.value = state.copy(
                    currentLevel = NavigationLevel.VERSE,
                    selectedVerse = initialVerse,
                    verseCount = verseCount,
                )
                verseSelectionFlow.tryEmit(Triple(book.book, state.selectedChapter, initialVerse))
            }
            NavigationLevel.VERSE -> { /* Already at deepest level */ }
        }
    }

    /**
     * Retreat to the previous navigation level.
     *
     * @return true if the level changed, false if already at BOOK (caller should dismiss).
     */
    fun drillUp(): Boolean {
        val state = _uiState.value
        return when (state.currentLevel) {
            NavigationLevel.VERSE -> {
                // If this book has only one chapter, retreat all the way to BOOK
                val targetLevel = if (state.chapterCount == 1) {
                    NavigationLevel.BOOK
                } else {
                    NavigationLevel.CHAPTER
                }
                _uiState.value = state.copy(
                    currentLevel = targetLevel,
                    selectedVerse = 1,
                )
                _previewVerseText.value = null
                true
            }
            NavigationLevel.CHAPTER -> {
                _uiState.value = state.copy(
                    currentLevel = NavigationLevel.BOOK,
                    selectedChapter = 1,
                )
                true
            }
            NavigationLevel.BOOK -> false
        }
    }

    /** Update the selected chapter, snap the verse to 1, and trigger a verse-1 preview fetch.
     *  If currently at VERSE level, retreat to CHAPTER level (the user is now
     *  scrolling chapters, so the verse strip should disappear). */
    fun onChapterSelected(chapter: Int) {
        val state = _uiState.value
        val book = state.books.getOrNull(state.selectedBookIndex) ?: return
        val verseCount = dataSource.getVerseCount(book.book, chapter)
        _uiState.value = state.copy(
            selectedChapter = chapter,
            selectedVerse = 1,
            verseCount = verseCount,
            currentLevel = if (state.currentLevel == NavigationLevel.VERSE)
                NavigationLevel.CHAPTER else state.currentLevel,
            hasInteracted = true,
            showPreview = true,
        )
        verseSelectionFlow.tryEmit(Triple(book.book, chapter, 1))
    }

    /** Update the selected verse and trigger async verse text loading. */
    fun onVerseSelected(verse: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedVerse = verse,
            hasInteracted = true,
            showPreview = true,
        )
        val book = state.books.getOrNull(state.selectedBookIndex)?.book ?: return
        verseSelectionFlow.tryEmit(Triple(book, state.selectedChapter, verse))
    }

    /** Returns the chapter count for a book at the given index. Used as a lambda by strip composables. */
    fun getChapterCount(bookIndex: Int): Int {
        val books = _uiState.value.books
        if (bookIndex !in books.indices) return 1
        return dataSource.getChapterCount(books[bookIndex].book)
    }

    /** Returns the verse count for a book/chapter. Used as a lambda by strip composables. */
    fun getVerseCount(bookIndex: Int, chapter: Int): Int {
        val books = _uiState.value.books
        if (bookIndex !in books.indices) return 1
        return dataSource.getVerseCount(books[bookIndex].book, chapter)
    }

    companion object {
        private const val TAG = "PassageFinderVM"
    }
}

class PassageFinderViewModelFactory(
    private val dataSource: PassageFinderDataSource,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(PassageFinderViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return PassageFinderViewModel(dataSource) as T
    }
}
