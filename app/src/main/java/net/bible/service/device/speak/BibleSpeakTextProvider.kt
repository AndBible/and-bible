/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.device.speak

import org.crosswire.common.util.KLocale

import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.sharedcore.platform.AppSettings
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakPhrases
import net.bible.sharedcore.log.Log
import androidx.collection.LruCache
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.SpeakSettingsChange
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.control.speak.AdvancedSpeakSettings
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import kotlin.collections.HashMap
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class BibleSpeakTextProvider(
    private val bibleTraverser: BibleTraverser,
    private val bookmarkControl: BookmarkControl,
    initialBook: SwordBook,
    private val platform: SpeakPlatform,
    settings: AppSettings,
    private val advanced: AdvancedSpeakSettings,
    private val launcher: OrderedLauncher,
    private val swordContentFacade: SwordContentFacade,
) : SpeakTextProvider {
    private val appSettings = settings
    private val lock = reentrantLock()
    private var revision = 0L
    private fun checkRevision(expected: Long) {
        if (revision != expected) throw kotlinx.coroutines.CancellationException("Obsolete Bible cursor")
    }
    private data class State(val book: SwordBook,
                             val startVerse: Verse,
                             val endVerse: Verse,
                             val currentVerse: Verse,
                             val command: SpeakCommand? = null)

    companion object {
        private const val PERSIST_BOOK = "SpeakBibleBook"
        private const val PERSIST_VERSE = "SpeakBibleVerse"
        const val FLAG_SHOW_PERCENT: Int = 0b1
        const val FLAG_SHOW_BOOK: Int = 0b10
        const val FLAG_SHOW_STATUSITEMS: Int = 0b001
        const val FLAG_SHOW_ALL = FLAG_SHOW_BOOK or FLAG_SHOW_PERCENT or FLAG_SHOW_STATUSITEMS
        private const val TAG = "Speak"
    }

    override val numItemsToTts = 20
    private lateinit var book: SwordBook
    private lateinit var startVerse: Verse
    private lateinit var endVerse: Verse
    private var bookmark : BibleBookmarkWithNotes? = null
    private lateinit var _currentVerse: Verse
    private var currentVerse: Verse
        get() = _currentVerse
        set(newValue) {
            // Skip verse 0, as we merge verse 0 to verse 1 in getSpeakCommands
            _currentVerse = nextIfZero(newValue)

        }

    private var lastVerseWithTitle: Verse? = null
    private val utteranceState = HashMap<String, State>()
    private var currentUtteranceId = ""
    private val currentCommands = SpeakCommandArray(platform.maxSpeechInputLength)

    private val bibleBooks = HashMap<String, String>()
    private val verseRenderLruCache = LruCache<Pair<SwordBook, Verse>, SpeakCommandArray>(100)
    private lateinit var phrases: SpeakPhrases

    init {
        setupBook(initialBook)
    }

    private fun nextIfZero(verse: Verse) =
        if(verse.verse == 0) {
            bibleTraverser.getNextVerse(book, verse)
        } else {
            verse
        }
    private var readList = SpeakCommandArray(platform.maxSpeechInputLength)
    internal var settings = SpeakSettings.load()

    private val currentState: State get() = utteranceState[currentUtteranceId] ?: State(book, startVerse, endVerse, currentVerse)

    override fun updateSettings(change: SpeakSettingsChange) {
        lock.withLock {
            this.settings = change.speakSettings
            Log.i(TAG, "SpeakSettings updated: $change")
            val bookmark = bookmark?.copy(playbackSettings = bookmark?.playbackSettings?.copy())
            if(change.updateBookmark && bookmark != null) {
                // If playback is paused or we are speaking, we need to update bookmark that is upon startVerse
                // (of which we will continue playback if unpaused)

                val oldPlaybackSettings = bookmark.playbackSettings
                val newPlaybackSettings = change.speakSettings.playbackSettings.copy()
                // Let's retain bookId and bookmarkWasCreated

                if (oldPlaybackSettings != null) {
                    newPlaybackSettings.apply {
                        bookId = oldPlaybackSettings.bookId
                        bookmarkWasCreated = oldPlaybackSettings.bookmarkWasCreated
                    }
                }
                bookmark.playbackSettings = newPlaybackSettings
                launcher.launch(SPEAK_KEY) {
                    bookmark.new = !bookmarkControl.isBookmarkPersisted(bookmark.id)
                    bookmarkControl.addOrUpdateBibleBookmark(bookmark)
                }
            }
        }
    }

    private fun setupBook(book: SwordBook) {
        this.book = book
        phrases = platform.phrasesFor(book.language.code)

        val locale = KLocale(book.language.code)
        bibleBooks.clear()

        for(bibleBook in book.versification.bookIterator) {
            var bookName = BibleNames.instance().getPreferredNameInLocale(bibleBook, locale)
            bookName = bookName.replace("1.", phrases.first)
            bookName = bookName.replace("2.", phrases.second)
            bookName = bookName.replace("3.", phrases.third)
            bookName = bookName.replace("4.", phrases.fourth)
            bookName = bookName.replace("5.", phrases.fifth)
            bibleBooks[bibleBook.osis] = bookName
        }
    }

    fun setupReading(book: SwordBook, verse: Verse) {
        lock.withLock {
            reset()
            loadedBookmark = null
            setupBook(book)

            currentVerse = verse
            startVerse = verse
            endVerse = verse
        }
    }

    // For tests. In production this is always null.
    var mockedBooks: ArrayList<SwordBook>? = null

    private inner class Calculation(val state: State, val readingSettings: SpeakSettings,
                                    val readingPhrases: SpeakPhrases, val names: Map<String, String>) {
        fun limit(verse: Verse): Verse {
            val range = readingSettings.playbackSettings.verseRange
            val result = if (range != null && (verse.ordinal > range.end.ordinal || verse.ordinal < range.start.ordinal)) range.start else verse
            return if (result.verse == 0) bibleTraverser.getNextVerse(state.book, result) else result
        }
        fun next(verse: Verse): Verse = limit(bibleTraverser.getNextVerse(state.book, verse))
        val rendered = mutableMapOf<Pair<SwordBook, Verse>, SpeakCommandArray>()
        fun render(verse: Verse): SpeakCommandArray {
            val key = state.book to verse
            val commands = rendered.getOrPut(key) {
                verseRenderLruCache.get(key) ?: swordContentFacade.getBibleSpeakCommands(readingSettings, state.book, verse)
            }
            return commands.copy()
        }
        fun commands(previous: Verse, verse: Verse): SpeakCommandArray {
            val result = SpeakCommandArray(platform.maxSpeechInputLength)
            val name = names[verse.book.osis]
            when {
                previous.book != verse.book -> {
                    result.add(PreBookChangeCommand())
                    result.add(TextCommand("${readingPhrases.bookChanged} $name ${readingPhrases.chapterChanged} ${verse.chapter}. "))
                    result.add(SilenceCommand())
                }
                previous.chapter != verse.chapter -> if (readingSettings.playbackSettings.speakChapterChanges) {
                    result.add(PreChapterChangeCommand(readingSettings))
                    result.add(TextCommand("${readingPhrases.chapterChanged} ${verse.chapter}. "))
                    result.add(SilenceCommand())
                }
                verse.ordinal < previous.ordinal -> {
                    if (readingSettings.playbackSettings.isMemorizationLoop) {
                        result.add(SilenceCommand())
                        result.add(SilenceCommand())
                    } else {
                        result.add(TextCommand("$name ${readingPhrases.chapterChanged} ${verse.chapter}. "))
                        result.add(SilenceCommand())
                    }
                }
            }
            result.addAll(render(verse))
            return result
        }
        fun more(cursor: State, remaining: SpeakCommandArray): Pair<State, SpeakCommandArray> {
            val commands = SpeakCommandArray(platform.maxSpeechInputLength)
            var verse = limit(cursor.currentVerse)
            val start = verse
            if (remaining.isNotEmpty()) {
                commands.addAll(remaining)
                remaining.clear()
                verse = next(verse)
            }
            while (render(verse).isEmpty()) verse = next(verse)
            commands.addAll(commands(cursor.endVerse, verse))
            val rest = SpeakCommandArray(platform.maxSpeechInputLength)
            while (!commands.endsSentence) {
                val next = next(verse)
                if (next.ordinal < verse.ordinal) break
                commands.addUntilSentenceBreak(commands(verse, next), rest)
                verse = next
            }
            val current = if (rest.isNotEmpty()) { remaining.addAll(rest); verse } else next(verse)
            return State(cursor.book, start, verse, current) to commands
        }
    }

    private data class CursorSnapshot(
        val expected: Long,
        val calculation: Calculation,
        val pending: SpeakCommandArray,
        val remaining: SpeakCommandArray,
    )

    override suspend fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean, isValid: () -> Boolean): SpeakCommand {
        val (expected, calculation, pending, remaining) = lock.withLock {
            CursorSnapshot(
                revision,
                Calculation(State(book, startVerse, endVerse, currentVerse),
                    settings.copy(playbackSettings = settings.playbackSettings.copy()), phrases, bibleBooks.toMap()),
                currentCommands.copy(),
                readList.copy(),
            )
        }
        var cursor = calculation.state
        while (pending.isEmpty()) {
            val result = calculation.more(cursor, remaining)
            cursor = result.first
            pending.addAll(result.second)
        }
        val command = pending.removeAt(0)
        return lock.withLock {
            checkRevision(expected)
            if (!isValid()) throw kotlinx.coroutines.CancellationException("Obsolete Bible request")
            calculation.rendered.forEach { (key, value) -> verseRenderLruCache.put(key, value) }
            startVerse = cursor.startVerse
            endVerse = cursor.endVerse
            _currentVerse = cursor.currentVerse
            readList = remaining
            currentCommands.clear()
            currentCommands.addAll(pending)
            if (isCurrent) { currentUtteranceId = utteranceId; utteranceState.clear() }
            utteranceState[utteranceId] = cursor.copy(command = command)
            command
        }
    }

    override suspend fun getStatusText(showFlag: Int): String {
        val verseRange = settings.playbackSettings.verseRange
        val percent = if(verseRange == null) {
            bibleTraverser.getPercentOfBook(currentState.startVerse)
        } else  {
            ((currentState.startVerse.ordinal - verseRange.start.ordinal).toFloat()
                    / (verseRange.end.ordinal-verseRange.start.ordinal) * 100).toInt()
        }
        var result = this.verseRange.getName()

        if(showFlag and FLAG_SHOW_STATUSITEMS != 0) {
            if(verseRange != null) {
                result += " \t\uD83D\uDD01"
            }
            if(settings.sleepTimer > 0) {
                result += " ⌛"
            }
        }

        if(showFlag and FLAG_SHOW_PERCENT != 0) {
            result += " $percent%"
        }
        if(showFlag and FLAG_SHOW_BOOK != 0) {
            result += " ${currentState.book.abbreviation}"
        }
        return result
    }

    override fun getText(utteranceId: String): String = currentState.command.toString()

    val verseRange: VerseRange get() = VerseRange(currentState.book.versification, currentState.startVerse, currentState.endVerse)

    override fun pause() {
        lock.withLock {
            reset()
            currentVerse = startVerse
            updateBookmark()
            isSpeaking = false
        }
    }

    private fun updateBookmark() {
        val previous = bookmark?.copy(playbackSettings = bookmark?.playbackSettings?.copy())
        val target = BibleBookmarkWithNotes(VerseRange(startVerse.versification, startVerse), null, true, null)
        val playback = settings.playbackSettings.copy()
        playback.bookId = book.initials
        playback.bookmarkWasCreated = true
        target.playbackSettings = playback
        val autoBookmark = advanced.autoBookmark
        // Publish the intended position on the caller; the worker must not resurrect it after stop().
        bookmark = if (!playback.isMemorizationLoop && (autoBookmark || previous != null)) target else null
        launcher.launch(SPEAK_KEY) {
            val wasRemoved = removeBookmark(previous)
            saveBookmark(wasRemoved, target, autoBookmark)
        }
    }

    override fun savePosition(fractionCompleted: Double) {}

    override var isSpeaking: Boolean = false

    override fun stop() {
        lock.withLock {
            reset()
            if(isSpeaking) {
                updateBookmark()
            }
            isSpeaking = false
            bookmark = null
        }
    }

    override fun prepareForStartSpeaking() {
        lock.withLock {
            val loaded = loadedBookmark
            if (loaded != null) {
                val playback = loaded.playbackSettings?.copy()
                if (playback != null && advanced.restoreSettingsFromBookmarks) {
                    playback.bookId = null
                    playback.bookmarkWasCreated = null
                    settings.playbackSettings = playback
                    settings.save()
                }
                bookmark = loaded
            }
            isSpeaking = true
        }
    }

    private var loadedBookmark: BibleBookmarkWithNotes? = null

    override suspend fun loadBookmarkState() {
        val (expected, verse) = lock.withLock { Pair(revision, currentVerse) }
        val found = bookmarkControl.speakBookmarkForVerse(verse)
        val loaded = if (found != null && bookmarkControl.labelsForBookmark(found).any { it.id == bookmarkControl.speakLabel().id }) found else null
        lock.withLock { checkRevision(expected); loadedBookmark = loaded }
    }

    private suspend fun removeBookmark(previous: BibleBookmarkWithNotes?): Boolean {
        var bookmark = previous ?: return false

        val labelList = bookmarkControl.labelsForBookmark(bookmark).toMutableList()
        val speakLabel = bookmarkControl.speakLabel()
        val ttsLabel = labelList.find { it.id == speakLabel.id }
        var wasRemoved = false

        if(ttsLabel != null) {
            if(labelList.size > 1 || bookmark.playbackSettings?.bookmarkWasCreated == false) {
                labelList.remove(ttsLabel)
                bookmark.playbackSettings = null
                bookmark.new = !bookmarkControl.isBookmarkPersisted(bookmark.id)
                bookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
                bookmarkControl.setLabelsForBookmark(bookmark, labelList)
                Log.i("SpeakBookmark", "Removed speak label from bookmark $bookmark")
            }
            else {
                bookmarkControl.deleteBookmark(bookmark)
                Log.i("SpeakBookmark", "Removed bookmark from $bookmark")
            }
            wasRemoved = true
        }
        return wasRemoved
    }

    private suspend fun saveBookmark(wasRemoved: Boolean, target: BibleBookmarkWithNotes, autoBookmark: Boolean) {
        if (target.playbackSettings!!.isMemorizationLoop) return
        if (autoBookmark || wasRemoved) {
            val saved = bookmarkControl.addOrUpdateBibleBookmark(target)
            bookmarkControl.setLabelsForBookmark(saved, listOf(bookmarkControl.speakLabel()))
            lock.withLock { if (bookmark === target) bookmark = saved }
        }
    }

    override suspend fun rewind(amount: SpeakSettings.RewindAmount?, isValid: () -> Boolean) = rewind(amount, false, isValid)

    suspend fun rewind(amount: SpeakSettings.RewindAmount?, autoRewind: Boolean, isValid: () -> Boolean = { true }) {
        val (expected, origin, lastTitle) = lock.withLock { Triple(revision, currentState, lastVerseWithTitle) }
        val minimum = Verse(origin.startVerse.versification, origin.startVerse.book, 1, 1)
        var target = when (amount ?: SpeakSettings.RewindAmount.SMART) {
            SpeakSettings.RewindAmount.SMART -> if (lastTitle != null && lastTitle != origin.startVerse) lastTitle
                else if (origin.startVerse.verse <= 1) bibleTraverser.getPrevChapter(origin.book, origin.startVerse)
                else Verse(origin.startVerse.versification, origin.startVerse.book, origin.startVerse.chapter, 1)
            SpeakSettings.RewindAmount.ONE_VERSE -> bibleTraverser.getPrevVerse(origin.book, origin.startVerse)
            SpeakSettings.RewindAmount.NONE -> origin.currentVerse
        }
        if (autoRewind && target.ordinal < minimum.ordinal) target = minimum
        if (target.verse == 0) target = bibleTraverser.getNextVerse(origin.book, target)
        lock.withLock {
            checkRevision(expected)
            if (!isValid()) throw kotlinx.coroutines.CancellationException("Obsolete Bible movement")
            reset()
            _currentVerse = target
            startVerse = target
            endVerse = target
            if (lastTitle == null || target.ordinal < lastTitle.ordinal) clearNotificationAndWidgetTitles()
            SpeakChanges.notifyProgress(SpeakChange.Progress(origin.book, verseRange, null))
        }
    }

    private fun clearNotificationAndWidgetTitles() {
        // Clear title and text from widget and notification.
        val titleCommand = TextCommand("", type=TextCommand.TextType.TITLE)
        SpeakChanges.notifyProgress(SpeakChange.Progress(book, startVerse, titleCommand))
        val normalCommand = TextCommand("", type=TextCommand.TextType.NORMAL)
        SpeakChanges.notifyProgress(SpeakChange.Progress(book, startVerse, normalCommand))
    }

    override suspend fun forward(amount: SpeakSettings.RewindAmount?, isValid: () -> Boolean) {
        val (expected, origin) = lock.withLock { Pair(revision, currentState) }
        var target = when (amount ?: SpeakSettings.RewindAmount.SMART) {
            SpeakSettings.RewindAmount.SMART -> bibleTraverser.getNextChapter(origin.book, origin.startVerse)
            SpeakSettings.RewindAmount.ONE_VERSE -> bibleTraverser.getNextVerse(origin.book, origin.startVerse)
            SpeakSettings.RewindAmount.NONE -> throw RuntimeException("Invalid settings")
        }
        if (target.verse == 0) target = bibleTraverser.getNextVerse(origin.book, target)
        lock.withLock {
            checkRevision(expected)
            if (!isValid()) throw kotlinx.coroutines.CancellationException("Obsolete Bible movement")
            reset()
            _currentVerse = target
            startVerse = target
            endVerse = target
            clearNotificationAndWidgetTitles()
            SpeakChanges.notifyProgress(SpeakChange.Progress(origin.book, verseRange, null))
        }
    }

    override fun finishedUtterance(utteranceId: String) {}

    override fun startUtterance(utteranceId: String) {
        lock.withLock {
            val state = utteranceState[utteranceId]
            currentUtteranceId = utteranceId
            if(state != null) {
                Log.i(TAG, "startUtterance $utteranceId $state")
                if(state.command is TextCommand && state.command.type == TextCommand.TextType.TITLE) {
                    lastVerseWithTitle = state.startVerse
                }
                val key = VerseRange(state.book.versification, state.startVerse, state.endVerse)
                val command = state.command!!
                SpeakChanges.notifyProgress(SpeakChange.Progress(state.book, key, command))
            }
        }
    }

    override fun reset() {
        lock.withLock {
            revision++
            val state = utteranceState[currentUtteranceId]
            Log.i(TAG, "Resetting. state: $currentUtteranceId $state")
            if(state != null) {
                startVerse = state.startVerse
                currentVerse = state.currentVerse
                endVerse = state.endVerse
                book = state.book
            }
            lastVerseWithTitle = null
            if(this::startVerse.isInitialized) {
                endVerse = startVerse
            }
            readList.clear()
            currentCommands.clear()
            utteranceState.clear()
            currentUtteranceId = ""
            verseRenderLruCache.evictAll()
        }
    }

    override fun getCurrentlyPlayingKey(): Verse = startVerse
    override fun getCurrentlyPlayingBook(): SwordBook = book

    override fun persistState() {
        appSettings.apply {
            setString(PERSIST_BOOK, book.abbreviation)
            setString(PERSIST_VERSE, startVerse.getOsisID())
        }
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = appSettings
        if(sharedPreferences.getString(PERSIST_BOOK) != null) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            val book = Books.installed().getBook(bookStr)
            if(book is SwordBook) {
                this.book = book
            }
        }
        if(sharedPreferences.getString(PERSIST_VERSE) != null) {
            val verseStr = sharedPreferences.getString(PERSIST_VERSE, "")!!
            startVerse = osisIdToVerse(verseStr)?: return false
            endVerse = startVerse
            currentVerse = startVerse
            return true
        }
        return false
    }

    private fun osisIdToVerse(osisId: String): Verse? {
        val verse = book.getKey(osisId) as RangedPassage?
        return verse?.getVerseAt(0)
    }

    override fun clearPersistedState() {
        appSettings.apply {
            removeString(PERSIST_BOOK)
            removeString(PERSIST_VERSE)
        }
    }

    override fun getTotalChars(): Long = 0
    override fun getSpokenChars(): Long = 0
    override fun isMoreTextToSpeak(): Boolean = true
}
