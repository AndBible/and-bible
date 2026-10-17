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

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.sharedcore.platform.AppSettings
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakPhrases
import net.bible.sharedcore.log.Log
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.SpeakSettingsChange
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.service.common.next
import net.bible.service.common.ordinalRangeFor
import net.bible.service.common.prev
import net.bible.service.common.shortName
import net.bible.service.common.tinyName
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeySerialized
import org.crosswire.jsword.book.Book
import kotlin.collections.HashMap
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class GeneralSpeakTextProvider(
    private val bookmarkControl: BookmarkControl,
    initialBook: Book,
    private val platform: SpeakPlatform,
    settings: AppSettings,
    private val advanced: AdvancedSpeakSettings,
    private val launcher: OrderedLauncher,
    private val fakeBookFactory: net.bible.service.download.FakeBookFactory,
    private val swordContentFacade: SwordContentFacade,
    private val strings: net.bible.sharedcore.platform.CoreStrings,
    private val traverser: net.bible.android.control.versification.BibleTraverser,
) : SpeakTextProvider {
    private val appSettings = settings
    // no suspend calls inside withLock (atomicfu withLock lacks the compiler's suspension check)
    private val lock = reentrantLock()
    private var revision = 0L
    private fun checkRevision(expected: Long) {
        if (expected != revision) throw kotlinx.coroutines.CancellationException("Obsolete speech cursor")
    }
    private data class State(
        val book: Book,
        val startKey: BookAndKey,
        val endKey: BookAndKey,
        val currentKey: BookAndKey,
        val command: SpeakCommand? = null,
    )

    companion object {
        const val FLAG_SHOW_PERCENT: Int = 0b1
        const val FLAG_SHOW_BOOK: Int = 0b10
        const val FLAG_SHOW_STATUSITEMS: Int = 0b001
        const val FLAG_SHOW_ALL = FLAG_SHOW_BOOK or FLAG_SHOW_PERCENT or FLAG_SHOW_STATUSITEMS
        private const val TAG = "Speak"
    }

    override val numItemsToTts = 20
    private lateinit var book: Book
    private lateinit var startKey: BookAndKey
    private lateinit var endKey: BookAndKey
    private var stopOrdinal: Int? = null
    
    private var bookmark : GenericBookmarkWithNotes? = null
    private lateinit var currentKey: BookAndKey

    private var lastVerseWithTitle: BookAndKey? = null
    private val utteranceState = HashMap<String, State>()
    private var currentUtteranceId = ""
    private val currentCommands = SpeakCommandArray(platform.maxSpeechInputLength)

    private lateinit var phrases: SpeakPhrases

    init {
        setupBook(initialBook)
    }

    private var readList = SpeakCommandArray(platform.maxSpeechInputLength)
    @VisibleForTesting
    var settings = SpeakSettings.load()

    private val currentState: State get() = utteranceState[currentUtteranceId] ?: State(book, startKey, endKey, currentKey)

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
                    bookmarkControl.addOrUpdateGenericBookmark(bookmark)
                }
            }
        }
    }

    private fun setupBook(book: Book) {
        this.book = book
        phrases = platform.phrasesFor(book.language.code)
    }

    fun setupReading(key: BookAndKey) {
        lock.withLock {
            reset()
            loadedBookmark = null
            setupBook(key.document!!)

            currentKey = key
            startKey = key
            endKey = key
            stopOrdinal = key.ordinal!!.end
        }
    }

    private fun getCommandsForKey(prevKey: BookAndKey, key: BookAndKey, readingSettings: SpeakSettings, readingPhrases: SpeakPhrases): SpeakCommandArray {
        val cmds = SpeakCommandArray(platform.maxSpeechInputLength)

        if(prevKey.getOsisRef() != key.getOsisRef()) {
            if(readingSettings.playbackSettings.speakChapterChanges) {
                cmds.add(PreChapterChangeCommand(readingSettings))
                cmds.add(TextCommand("${readingPhrases.chapterChanged} ${key.getName()}. "))
                cmds.add(SilenceCommand())
            }
        }
        cmds.addAll(getSpeakCommandsForKey(key))
        return cmds
    }

    private data class CursorSnapshot(
        val expected: Long,
        val cursor: State,
        val pending: SpeakCommandArray,
        val remaining: SpeakCommandArray,
        val readingSettings: SpeakSettings,
        val readingPhrases: SpeakPhrases,
        val stop: Int?,
    )

    override suspend fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean, isValid: () -> Boolean): SpeakCommand {
        val (expected, cursor, pending, remaining, readingSettings, readingPhrases, stop) = lock.withLock {
            CursorSnapshot(
                revision,
                State(book, startKey, endKey, currentKey),
                currentCommands.copy(),
                readList.copy(),
                settings.copy(playbackSettings = settings.playbackSettings.copy()),
                phrases,
                stopOrdinal,
            )
        }
        var calculated = cursor
        while (pending.isEmpty()) {
            val result = getMoreSpeakCommands(calculated, remaining, readingSettings, readingPhrases, stop)
            calculated = result.first
            pending.addAll(result.second)
        }
        val cmd = pending.removeAt(0)
        return lock.withLock {
            checkRevision(expected)
            if (!isValid()) throw kotlinx.coroutines.CancellationException("Obsolete speech request")
            startKey = calculated.startKey
            endKey = calculated.endKey
            currentKey = calculated.currentKey
            readList = remaining
            currentCommands.clear()
            currentCommands.addAll(pending)
            if (isCurrent) {
                currentUtteranceId = utteranceId
                utteranceState.clear()
            }
            utteranceState[utteranceId] = calculated.copy(command = cmd)
            cmd
        }
    }

    private fun needToStop(key: BookAndKey): Boolean {
        val stopOrd = stopOrdinal
        if (stopOrd != null) {
            return key.ordinal!! > stopOrd
        }
        return false
    }


    private suspend fun getMoreSpeakCommands(cursor: State, remaining: SpeakCommandArray, readingSettings: SpeakSettings, readingPhrases: SpeakPhrases, stop: Int?): Pair<State, SpeakCommandArray> {
        val cmds = SpeakCommandArray(platform.maxSpeechInputLength)
        var key = limitToRange(cursor.currentKey)
        val start = key
        if (remaining.isNotEmpty()) {
            cmds.addAll(remaining)
            remaining.clear()
            key = getNextKey(key)
        }
        cmds.addAll(getCommandsForKey(cursor.endKey, key, readingSettings, readingPhrases))
        val rest = SpeakCommandArray(platform.maxSpeechInputLength)
        while (!cmds.endsSentence) {
            val nextKey = getNextKey(key)
            if ((stop != null && nextKey.ordinal!! > stop) || nextKey.ordinal!! < key.ordinal!!) break
            cmds.addUntilSentenceBreak(getCommandsForKey(key, nextKey, readingSettings, readingPhrases), rest)
            key = nextKey
        }
        val next = if (rest.isNotEmpty()) {
            remaining.addAll(rest)
            key
        } else getNextKey(key)
        return State(cursor.book, start, key, next) to cmds
    }

    override suspend fun getStatusText(showFlag: Int): String = getStatusTexts().getValue(showFlag and 3)

    override suspend fun getStatusTexts(): Map<Int, String> {
        val (state, expected, sleep) = lock.withLock { Triple(currentState, revision, settings.sleepTimer > 0) }
        val range = state.book.ordinalRangeFor(state.currentKey.key, strings)
        val percent = if (range.last == range.first) 0 else
            (((state.currentKey.ordinal!!.start - range.first).toFloat() / (range.last - range.first)) * 100).toInt()
        val name = state.currentKey.shortName
        val values = (0..3).associateWith { flag ->
            buildString {
                append(name)
                if (flag and FLAG_SHOW_STATUSITEMS != 0 && sleep) append(" ⌛")
                if (flag and FLAG_SHOW_PERCENT != 0) append(" $percent%")
                if (flag and FLAG_SHOW_BOOK != 0) append(" ${state.book.abbreviation}")
            }
        }
        lock.withLock { checkRevision(expected) }
        return values
    }

    override fun getText(utteranceId: String): String = currentState.command.toString()

    private fun getSpeakCommandsForKey(key: BookAndKey): SpeakCommandArray =
        swordContentFacade.getGenBookSpeakCommands(key)

    override fun pause() {
        lock.withLock {
            reset()
            currentKey = startKey
            updateBookmark()
            isSpeaking = false
        }
    }

    private fun updateBookmark() {
        if(stopOrdinal != null) return
        val previous = bookmark?.copy(playbackSettings = bookmark?.playbackSettings?.copy())
        val target = GenericBookmarkWithNotes(startKey.key, book, null, startKey.ordinal!!.start)
        val playback = settings.playbackSettings.copy()
        playback.bookId = book.initials
        playback.bookmarkWasCreated = true
        target.playbackSettings = playback
        val autoBookmark = advanced.autoBookmark
        // Publish the intended position on the caller; the worker must not resurrect it after stop().
        bookmark = if (autoBookmark || previous != null) target else null
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

    private var loadedBookmark: GenericBookmarkWithNotes? = null

    override suspend fun loadBookmarkState() {
        val (expected, key, limited) = lock.withLock { Triple(revision, currentKey, stopOrdinal != null) }
        val found = if (!limited) bookmarkControl.speakBookmarkForKey(key) else null
        val loaded = if (found != null && bookmarkControl.labelsForBookmark(found).any { it.id == bookmarkControl.speakLabel().id }) found else null
        lock.withLock { checkRevision(expected); loadedBookmark = loaded }
    }

    private suspend fun removeBookmark(previous: GenericBookmarkWithNotes?): Boolean {
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
                bookmark = bookmarkControl.addOrUpdateGenericBookmark(bookmark)
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

    private suspend fun saveBookmark(wasRemoved: Boolean, target: GenericBookmarkWithNotes, autoBookmark: Boolean) {
        if (autoBookmark || wasRemoved) {
            val saved = bookmarkControl.addOrUpdateGenericBookmark(target)
            bookmarkControl.setLabelsForBookmark(saved, listOf(bookmarkControl.speakLabel()))
            lock.withLock { if (bookmark === target) bookmark = saved }
        }
    }

    private fun limitToRange(key: BookAndKey): BookAndKey = key // If we want to implement repeat

    private suspend fun getNextOrdinal(key: BookAndKey): BookAndKey {
        val nextOrdinal = key.ordinal!!.start + 1
        if(nextOrdinal > key.document!!.ordinalRangeFor(key.key, strings).last) {
            return key.next(strings, traverser)
        }
        return BookAndKey(key.key, key.document, OrdinalRange(nextOrdinal))
    }

    private suspend fun getPrevOrdinal(key: BookAndKey): BookAndKey {
        val nextOrdinal = key.ordinal!!.start - 1
        if(nextOrdinal < key.document!!.ordinalRangeFor(key.key, strings).first) {
            val prevKey = key.prev(strings, traverser)
            return BookAndKey(prevKey.key, key.document, OrdinalRange(key.document!!.ordinalRangeFor(prevKey.key, strings).last))
        }
        return BookAndKey(key.key, key.document, OrdinalRange(nextOrdinal))
    }

    private suspend fun getNextKey(key: BookAndKey): BookAndKey = limitToRange(getNextOrdinal(key))
    override suspend fun rewind(amount: SpeakSettings.RewindAmount?, isValid: () -> Boolean) = rewind(amount, false, isValid)

    suspend fun rewind(amount: SpeakSettings.RewindAmount?, autoRewind: Boolean, isValid: () -> Boolean = { true }) {
        val (expected, origin, lastTitle) = lock.withLock { Triple(revision, currentState, lastVerseWithTitle) }
        val minimumOrdinal = origin.book.ordinalRangeFor(origin.startKey.key, strings).first
        var target = when (amount ?: SpeakSettings.RewindAmount.SMART) {
            SpeakSettings.RewindAmount.SMART -> if (lastTitle != null && lastTitle != origin.startKey) lastTitle
                else if (origin.startKey.ordinal!! <= minimumOrdinal) origin.startKey.prev(strings, traverser)
                else {
                    var cur = origin.startKey
                    repeat(10) { cur = getPrevOrdinal(cur) }
                    cur
                }
            SpeakSettings.RewindAmount.ONE_VERSE -> getPrevOrdinal(origin.startKey)
            SpeakSettings.RewindAmount.NONE -> origin.currentKey
        }
        if (autoRewind && target.ordinal!! < minimumOrdinal)
            target = BookAndKey(origin.startKey.key, origin.book, OrdinalRange(minimumOrdinal))
        lock.withLock {
            checkRevision(expected)
            if (!isValid()) throw kotlinx.coroutines.CancellationException("Obsolete speech request")
            reset()
            currentKey = target
            startKey = target
            endKey = target
            if (lastTitle == null || target.ordinal!! < lastTitle.ordinal!!) clearNotificationAndWidgetTitles()
            SpeakChanges.notifyProgress(SpeakChange.Progress(origin.book, target, null))
        }
    }

    private fun clearNotificationAndWidgetTitles() {
        // Clear title and text from widget and notification.
        val titleCommand = TextCommand("", type=TextCommand.TextType.TITLE)
        SpeakChanges.notifyProgress(SpeakChange.Progress(book, startKey, titleCommand))
        val normalCommand = TextCommand("", type=TextCommand.TextType.NORMAL)
        SpeakChanges.notifyProgress(SpeakChange.Progress(book, startKey, normalCommand))
    }

    override suspend fun forward(amount: SpeakSettings.RewindAmount?, isValid: () -> Boolean) {
        val (expected, origin) = lock.withLock { Pair(revision, currentState) }
        var target = origin.startKey
        when (amount ?: SpeakSettings.RewindAmount.SMART) {
            SpeakSettings.RewindAmount.SMART -> repeat(10) { target = getNextOrdinal(target) }
            SpeakSettings.RewindAmount.ONE_VERSE -> target = getNextOrdinal(target)
            SpeakSettings.RewindAmount.NONE -> throw RuntimeException("Invalid settings")
        }
        lock.withLock {
            checkRevision(expected)
            if (!isValid()) throw kotlinx.coroutines.CancellationException("Obsolete speech request")
            reset()
            currentKey = target
            startKey = target
            endKey = target
            clearNotificationAndWidgetTitles()
            SpeakChanges.notifyProgress(SpeakChange.Progress(origin.book, target, null))
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
                    lastVerseWithTitle = state.startKey
                }
                val key = BookAndKey(
                    state.startKey.key,
                    state.book,
                    OrdinalRange(state.startKey.ordinal!!.start, state.endKey.ordinal!!.start),
                )
                val command = state.command!!
                val forceFollow = stopOrdinal != null
                SpeakChanges.notifyProgress(SpeakChange.Progress(state.book, key, command, forceFollow))
            }
        }
    }

    override fun reset() {
        lock.withLock {
            revision++
            val state = utteranceState[currentUtteranceId]
            Log.i(TAG, "Resetting. state: $currentUtteranceId $state")
            if(state != null) {
                startKey = state.startKey
                currentKey = state.currentKey
                endKey = state.endKey
                book = state.book
            }
            lastVerseWithTitle = null
            if(this::startKey.isInitialized) {
                endKey = startKey
            }
            readList.clear()
            currentCommands.clear()
            utteranceState.clear()
            currentUtteranceId = ""
        }
    }

    override fun getCurrentlyPlayingKey(): BookAndKey = startKey
    override fun getCurrentlyPlayingBook(): Book = book

    override fun persistState() {
        appSettings.apply {
            setString(PERSIST_BOOK, book.abbreviation)
            setString(PERSIST_KEY, startKey.serialized)
        }
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = appSettings
        if(sharedPreferences.getString(PERSIST_BOOK) != null) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            val book = Books.installed().getBook(bookStr)
            if(book is Book) {
                this.book = book
            }
        }
        if(sharedPreferences.getString(PERSIST_KEY) != null) {
            val keyStr = sharedPreferences.getString(PERSIST_KEY, "")!!
            startKey = try { BookAndKeySerialized.fromJSON(keyStr).resolve(fakeBookFactory) } catch (e: Exception) {
                Log.e(TAG, "Can't restore state", e)
                return false
            }
            endKey = startKey
            currentKey = startKey
            return true
        }
        return false
    }

    override fun clearPersistedState() {
        appSettings.apply {
            removeString(PERSIST_BOOK)
            removeString(PERSIST_KEY)
        }
    }

    override fun getTotalChars(): Long = 0
    override fun getSpokenChars(): Long = 0
    override fun isMoreTextToSpeak(): Boolean = !needToStop(currentKey)
}

private const val PERSIST_BOOK = "SpeakGenBook"
private const val PERSIST_KEY = "SpeakGenKey"
