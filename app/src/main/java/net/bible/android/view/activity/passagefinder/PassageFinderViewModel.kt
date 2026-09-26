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

/**
 * What the preview bubble currently knows about the selected verse's text.
 *
 * A single nullable String cannot say whether "no text" means nothing is being previewed
 * or a read is still in flight, and only the latter may show a placeholder: the reference
 * line updates synchronously as the user dials the verse strip, while the text is 150 ms
 * of debounce plus a disk read behind it. Without the distinction the bubble either pairs
 * the new reference with the previous verse's words, or shows a placeholder in states —
 * a cold open, a retreat out of verse level — where nothing is coming at all.
 *
 * [None] and [Loading] are objects; [Ready] is the only allocating case and is built only
 * on the debounced arrival path, a handful of times per scroll.
 */
sealed interface PreviewVerseText {
    /** Nothing to preview: the widget just opened, or the user retreated out of verse level. */
    data object None : PreviewVerseText

    /** A verse is picked and its text is still being read; the bubble shows the placeholder. */
    data object Loading : PreviewVerseText

    /** The verse text is loaded and non-blank. */
    data class Ready(val text: String) : PreviewVerseText
}

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
    /** Whether the verse-preview bubble should show. True after any chapter/verse scroll;
     *  cleared when the book changes (book scroll has no preview). */
    val showPreview: Boolean = false,
)

/**
 * State machine for the PassageFinder widget.
 *
 * Owns the book list and current selection state. Scroll offsets are deliberately NOT
 * stored here: they live in [PassageFinderView] alongside the scroll animations that
 * drive them. Hoisting them would create a write-back loop, where committing a settled
 * offset to state would in turn re-target the animation that produced it.
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

    /** Emits the confirmed [Verse] when the user finalizes their selection. */
    val selectionConfirmed: SharedFlow<Verse> = _selectionConfirmed.asSharedFlow()

    private val _previewVerseText = MutableStateFlow<PreviewVerseText>(PreviewVerseText.None)
    val previewVerseText: StateFlow<PreviewVerseText> = _previewVerseText.asStateFlow()

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
            .onEach { text ->
                // Blank or null lands in None rather than staying pending. The catch above
                // already degrades a failed read to null, so routing that here is what
                // stops a failed read from leaving the placeholder up forever.
                _previewVerseText.value =
                    if (text.isNullOrBlank()) PreviewVerseText.None
                    else PreviewVerseText.Ready(text)
            }
            .launchIn(viewModelScope)
    }

    /**
     * Requests the text of [book] [chapter]:[verse] and arms the preview placeholder.
     *
     * Every one of the caller sites updates `_uiState` immediately before calling this, so
     * the bubble's reference line changes in the very next frame while the text is a
     * debounce plus a disk read behind it. Arming [PreviewVerseText.Loading] in the same
     * helper that requests the text is what keeps a call site from being able to ask for
     * text without arming the placeholder — which is exactly how the bubble came to pair a
     * new reference with the previous verse's words.
     */
    private fun requestVerseText(book: BibleBook, chapter: Int, verse: Int) {
        _previewVerseText.value = PreviewVerseText.Loading
        verseSelectionFlow.tryEmit(Triple(book, chapter, verse))
    }

    /**
     * Marks the widget as opening while its book list is still being loaded.
     *
     * The view renders a placeholder in this state. It exists so that the tap can put
     * something on screen in the very next frame even when the module's book list has
     * not been read from disk yet — [show] then fills in the real content.
     */
    fun showLoading() {
        // Unrelated to PreviewVerseText.Loading: this is the widget's own skeleton while
        // the book list is read, and nothing has been selected to preview yet.
        _previewVerseText.value = PreviewVerseText.None
        _uiState.value = PassageFinderUiState(visible = true)
    }

    /**
     * Makes the widget visible on [bookList], centered on the currently active book.
     *
     * The book list is passed in rather than fetched here because loading it touches
     * disk; [PassageFinderLauncher] obtains it from the cache or off the main thread.
     */
    fun show(bookList: PassageFinderDataSource.BookList) {
        val books = bookList.books
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
        val chapterCount = bookList.chapterCounts.getOrNull(currentBookIndex)
            ?: dataSource.getChapterCount(currentBook)
        val chapter = currentVerse.chapter.coerceIn(1, chapterCount)
        val verseCount = dataSource.getVerseCount(currentBook, chapter)

        // Drop any preview text from a previous session — the debounced verse-text
        // flow won't repopulate it until the user actually scrolls.
        _previewVerseText.value = PreviewVerseText.None

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
        )
    }

    /**
     * Re-centres the widget on wherever the reader has scrolled to.
     *
     * Called as the Bible view reports its scroll position, so the widget always shows
     * the passage actually on screen behind it — whether it was opened mid-fling or the
     * user scrolled the text through the overlay afterwards.
     *
     * There is no need to stop following once the user has picked something by hand.
     * The two can only disagree while the text is moving, and putting a finger on the
     * widget halts the reader, so a hand-made selection is never overwritten.
     */
    fun followCurrentVerse() {
        val state = _uiState.value
        if (!state.visible || state.books.isEmpty()) return
        val current = dataSource.getCurrentVerse()
        val bookIndex = state.books.indexOfFirst { it.book == current.book }
        if (bookIndex < 0) return
        val chapterCount = dataSource.getChapterCount(current.book)
        val chapter = current.chapter.coerceIn(1, chapterCount)
        val verseCount = dataSource.getVerseCount(current.book, chapter)
        val verse = current.verse.coerceIn(1, verseCount)
        if (bookIndex == state.selectedBookIndex &&
            chapter == state.selectedChapter &&
            verse == state.selectedVerse
        ) return
        _uiState.value = state.copy(
            // The open-book marker tracks the reader too, so it keeps pointing at
            // whatever is actually on screen behind the overlay.
            openBookIndex = bookIndex,
            selectedBookIndex = bookIndex,
            selectedChapter = chapter,
            selectedVerse = verse,
            chapterCount = chapterCount,
            verseCount = verseCount,
        )
    }

    /** Hide the widget. */
    fun dismiss() {
        _uiState.value = _uiState.value.copy(visible = false, showPreview = false)
    }

    /**
     * Confirm the current selection and dismiss the widget.
     *
     * Builds a [Verse] from the current UI state and emits it to [selectionConfirmed]
     * for the navigation layer to act on. Then dismisses the widget.
     */
    fun confirmSelection() {
        val state = _uiState.value
        val book = state.books.getOrNull(state.selectedBookIndex)?.book ?: return
        val versification = dataSource.getCurrentVerse().versification
        val verse = Verse(versification, book, state.selectedChapter, state.selectedVerse)
        _selectionConfirmed.tryEmit(verse)
        dismiss()
    }

    /** Update the selected book index once a book scroll settles on a new spine. */
    fun onBookSelected(index: Int) {
        val state = _uiState.value
        if (index in state.books.indices) {
            val bookChanged = index != state.selectedBookIndex
            val book = state.books[index].book
            val chapterCount = dataSource.getChapterCount(book)
            // When the book changes we snap back to chapter 1 / verse 1; otherwise
            // keep the user's current chapter and clamp selectedVerse against the
            // new book's verse count so the verse strip cannot centre a verse that
            // does not exist there.
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
                    requestVerseText(book.book, 1, initialVerse)
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
                requestVerseText(book.book, state.selectedChapter, initialVerse)
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
                // Nothing is being previewed at chapter level, so this is None rather than
                // a pending read — no placeholder may appear on the way back out.
                _previewVerseText.value = PreviewVerseText.None
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
            showPreview = true,
        )
        requestVerseText(book.book, chapter, 1)
    }

    /** Update the selected verse and trigger async verse text loading. */
    fun onVerseSelected(verse: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedVerse = verse,
            showPreview = true,
        )
        val book = state.books.getOrNull(state.selectedBookIndex)?.book ?: return
        requestVerseText(book, state.selectedChapter, verse)
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
