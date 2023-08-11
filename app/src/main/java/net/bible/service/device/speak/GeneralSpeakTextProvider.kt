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

import android.content.res.Resources
import android.os.Build
import android.util.Log
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakProgressEvent
import net.bible.service.sword.SwordContentFacade
import net.bible.android.activity.R
import org.crosswire.jsword.book.Books
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.service.common.AdvancedSpeakSettings
import net.bible.service.common.getBookAndKey
import net.bible.service.common.next
import net.bible.service.common.ordinalRangeFor
import net.bible.service.common.prev
import net.bible.service.sword.BookAndKey
import org.crosswire.jsword.book.Book
import kotlin.collections.HashMap

class GeneralSpeakTextProvider(
    private val bookmarkControl: BookmarkControl,
    initialBook: Book
) : SpeakTextProvider  {
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
    private val currentCommands = SpeakCommandArray()

    private lateinit var localizedResources: Resources

    init {
        setupBook(initialBook)
    }

    private var readList = SpeakCommandArray()
    internal var settings = SpeakSettings.load()

    private val currentState: State get() = utteranceState[currentUtteranceId] ?: State(book, startKey, endKey, currentKey)

    override fun updateSettings(speakSettingsChangedEvent: SpeakSettingsChangedEvent) {
        this.settings = speakSettingsChangedEvent.speakSettings
        Log.i(TAG, "SpeakSettings updated: $speakSettingsChangedEvent")
        val bookmark = bookmark
        if(speakSettingsChangedEvent.updateBookmark && bookmark != null) {
            // If playback is paused or we are speaking, we need to update bookmark that is upon startVerse
            // (of which we will continue playback if unpaused)

            val oldPlaybackSettings = bookmark.playbackSettings
            val newPlaybackSettings = speakSettingsChangedEvent.speakSettings.playbackSettings
            // Let's retain bookId and bookmarkWasCreated

            if (oldPlaybackSettings != null) {
                newPlaybackSettings.apply {
                    bookId = oldPlaybackSettings.bookId
                    bookmarkWasCreated = oldPlaybackSettings.bookmarkWasCreated
                }
            }
            bookmark.playbackSettings = newPlaybackSettings
            this.bookmark = bookmarkControl.addOrUpdateGenericBookmark(bookmark)
        }
    }

    private fun setupBook(book: Book) {
        this.book = book
        localizedResources = BibleApplication.application.getLocalizedResources(book.language.code)
    }

    fun setupReading(key: BookAndKey) {
        reset()
        setupBook(key.document!!)

        currentKey = key
        startKey = key
        endKey = key
        stopOrdinal = key.ordinal!!.end
    }

    private fun getCommandsForKey(prevKey: BookAndKey, key: BookAndKey): SpeakCommandArray {
        val cmds = SpeakCommandArray()
        val res = localizedResources

        if(prevKey.osisRef != key.osisRef) {
            if(settings.playbackSettings.speakChapterChanges) {
                cmds.add(PreChapterChangeCommand(settings))
                cmds.add(TextCommand("${res.getString(R.string.speak_chapter_changed)} ${key.name}. "))
                cmds.add(SilenceCommand())
            }
        }
        cmds.addAll(getSpeakCommandsForKey(key))
        return cmds
    }

    override fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean): SpeakCommand {
        while(currentCommands.isEmpty()) {
            currentCommands.addAll(getMoreSpeakCommands())
        }
        val cmd = currentCommands.removeAt(0)
        if(isCurrent) {
            currentUtteranceId = utteranceId
            utteranceState.clear()
            Log.i(TAG, "Marked current utteranceID $utteranceId")
        }
        utteranceState[utteranceId] = State(book, startKey, endKey, currentKey, cmd)
        return cmd
    }

    private fun needToStop(key: BookAndKey): Boolean {
        val stopOrd = stopOrdinal
        if (stopOrd != null) {
            return key.ordinal!! > stopOrd
        }
        return false
    }


    private fun getMoreSpeakCommands(): SpeakCommandArray {
        val cmds = SpeakCommandArray()

        var key = limitToRange(currentKey)
        startKey = key

        if(readList.isNotEmpty()) {
            cmds.addAll(readList)
            readList.clear()
            key = getNextKey(key)
        }

        cmds.addAll(getCommandsForKey(endKey, key))

        val rest = SpeakCommandArray()

        while (!cmds.endsSentence) {
            val nextKey = getNextKey(key)
            if(needToStop(nextKey)) break
            if(nextKey.ordinal!! < key.ordinal!!) {
                break
            }
            val nextCommands = getCommandsForKey(key, nextKey)
            cmds.addUntilSentenceBreak(nextCommands, rest)
            key = nextKey
        }

        currentKey = if (rest.isNotEmpty()) {
            readList.addAll(rest)
            key
        } else {
            getNextKey(key)
        }

        endKey = key

        return cmds
    }

    override fun getStatusText(showFlag: Int): String {
        var result = this.currentState.currentKey.name

        if(showFlag and FLAG_SHOW_STATUSITEMS != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(settings.sleepTimer > 0) {
                result += " ⌛"
            }
        }

        if(showFlag and FLAG_SHOW_BOOK != 0) {
            result += " ${currentState.book.abbreviation}"
        }
        return result
    }

    override fun getText(utteranceId: String): String = currentState.command.toString()

    private fun getSpeakCommandsForKey(key: BookAndKey): SpeakCommandArray =
        SwordContentFacade.getGenBookSpeakCommands(key)

    override fun pause() {
        reset()
        currentKey = startKey
        updateBookmark()
        isSpeaking = false
    }

    private fun updateBookmark() {
        if(stopOrdinal != null) return
        removeBookmark()
        saveBookmark()
    }

    override fun savePosition(fractionCompleted: Double) {}

    override var isSpeaking: Boolean = false

    override fun stop() {
        reset()
        if(isSpeaking) {
            updateBookmark()
        }
        isSpeaking = false
        bookmark = null
    }

    override fun prepareForStartSpeaking() {
        readBookmark()
        isSpeaking = true
    }

    private fun readBookmark() {
        if(AdvancedSpeakSettings.autoBookmark && stopOrdinal == null) {
            val key = currentKey

            val bookmark: GenericBookmarkWithNotes = bookmarkControl.speakBookmarkForKey(key)?: return
            val labelList = bookmarkControl.labelsForBookmark(bookmark)
            val speakLabel = bookmarkControl.speakLabel
            val ttsLabel = labelList.find { it.id == speakLabel.id }

            if(ttsLabel != null) {
                Log.i(TAG, "Bookmark book ${bookmark.book}")
                val playbackSettings = bookmark.playbackSettings?.copy()
                if(playbackSettings != null && AdvancedSpeakSettings.restoreSettingsFromBookmarks) {
                    playbackSettings.bookmarkWasCreated = null
                    playbackSettings.bookId = null
                    settings.playbackSettings = playbackSettings
                    settings.save()
                    Log.i("SpeakBookmark", "Loaded bookmark from $bookmark ${settings.playbackSettings.speed}")
                }
                this.bookmark = bookmark
            }
        }
    }

    private fun removeBookmark() {
        var bookmark: GenericBookmarkWithNotes = this.bookmark ?: return

        val labelList = bookmarkControl.labelsForBookmark(bookmark).toMutableList()
        val speakLabel = bookmarkControl.speakLabel
        val ttsLabel = labelList.find { it.id == speakLabel.id }

        if(ttsLabel != null) {
            if(labelList.size > 1 || bookmark.playbackSettings?.bookmarkWasCreated == false) {
                labelList.remove(ttsLabel)
                bookmark.playbackSettings = null
                bookmark = bookmarkControl.addOrUpdateGenericBookmark(bookmark)
                bookmarkControl.setLabelsForBookmark(bookmark, labelList)
                Log.i("SpeakBookmark", "Removed speak label from bookmark $bookmark")
            }
            else {
                bookmarkControl.deleteBookmark(bookmark)
                Log.i("SpeakBookmark", "Removed bookmark from $bookmark")
            }
            this.bookmark = null
        }
    }

    private fun saveBookmark() {
        val labelList = mutableSetOf<Label>()
        if(AdvancedSpeakSettings.autoBookmark) {
            var bookmark: GenericBookmarkWithNotes? = bookmarkControl.firstGenericBookmarkStartingAtKey(startKey)?.run {
                if(textRange != null) null else this
            }

            val playbackSettings = settings.playbackSettings.copy()
            playbackSettings.bookId = book.initials

            if(bookmark == null) {
                playbackSettings.bookmarkWasCreated = true
                bookmark = GenericBookmarkWithNotes(startKey.key, book, null, startKey.ordinal!!.start)
                bookmark.playbackSettings = playbackSettings
                bookmark = bookmarkControl.addOrUpdateGenericBookmark(bookmark)
            }
            else {
                playbackSettings.bookmarkWasCreated = bookmark.playbackSettings?.bookmarkWasCreated ?: false
                labelList.addAll(bookmarkControl.labelsForBookmark(bookmark))
                bookmark.playbackSettings = playbackSettings
                bookmark = bookmarkControl.addOrUpdateGenericBookmark(bookmark)
            }

            labelList.add(bookmarkControl.speakLabel)

            bookmarkControl.setLabelsForBookmark(bookmark, labelList.toList())
            Log.i("SpeakBookmark", "Saved bookmark into $bookmark, ${settings.playbackSettings.speed}")
            this.bookmark = bookmark
        }
    }

    private fun limitToRange(key: BookAndKey): BookAndKey = key // If we want to implement repeat

    private fun getNextOrdinal(key: BookAndKey): BookAndKey {
        val nextOrdinal = key.ordinal!!.start + 1
        if(nextOrdinal > book.ordinalRangeFor(key.key).last) {
            return key.next
        }
        return BookAndKey(key.key, book, OrdinalRange(nextOrdinal))
    }

    private fun getPrevOrdinal(key: BookAndKey): BookAndKey {
        val nextOrdinal = key.ordinal!!.start - 1
        if(nextOrdinal < book.ordinalRangeFor(key.key).first) {
            val prevKey = key.prev
            return BookAndKey(prevKey.key, book, OrdinalRange(book.ordinalRangeFor(prevKey.key).last))
        }
        return BookAndKey(key.key, book, OrdinalRange(nextOrdinal))
    }

    private fun getNextKey(key: BookAndKey): BookAndKey = limitToRange(getNextOrdinal(key))
    override fun rewind(amount: SpeakSettings.RewindAmount?) = rewind(amount, false)

    fun rewind(amount: SpeakSettings.RewindAmount?, autoRewind: Boolean) {
        val lastTitle = this.lastVerseWithTitle
        reset()
        val rewindAmount = amount?: SpeakSettings.RewindAmount.SMART
        val minimumOrdinal = book.ordinalRangeFor(startKey.key).first

        when(rewindAmount) {
         SpeakSettings.RewindAmount.SMART -> {
             currentKey = if(lastTitle != null && lastTitle != startKey) {
                 lastTitle
             } else {
                 if (startKey.ordinal!! <= minimumOrdinal) {
                     startKey.prev
                 } else {
                     startKey
                 }
             }
         }
         SpeakSettings.RewindAmount.ONE_VERSE -> {
             currentKey = getPrevOrdinal(startKey)
         }
         SpeakSettings.RewindAmount.TEN_VERSES -> {
            currentKey = startKey
            for(i in 1..10) {
                currentKey = getPrevOrdinal(currentKey)
            }
         }
         SpeakSettings.RewindAmount.NONE -> {}
        }

        if(autoRewind && currentKey.ordinal!! < minimumOrdinal) {
            currentKey = BookAndKey(startKey.key, book, OrdinalRange(minimumOrdinal))
        }

        if(lastTitle == null || currentKey.ordinal!! < lastTitle.ordinal!!) {
            clearNotificationAndWidgetTitles()
        }

        startKey = currentKey
        endKey = currentKey

        ABEventBus.post(SpeakProgressEvent(book, currentKey, null))
    }

    private fun clearNotificationAndWidgetTitles() {
        // Clear title and text from widget and notification.
        ABEventBus.post(SpeakProgressEvent(book, startKey,
                TextCommand("", type=TextCommand.TextType.TITLE)))
        ABEventBus.post(SpeakProgressEvent(book, startKey,
                TextCommand("", type=TextCommand.TextType.NORMAL)))
    }

    override fun forward(amount: SpeakSettings.RewindAmount?) {
        reset()
        val rewindAmount = amount?: SpeakSettings.RewindAmount.SMART
        when(rewindAmount) {
            SpeakSettings.RewindAmount.SMART -> currentKey = startKey.next
            SpeakSettings.RewindAmount.ONE_VERSE -> currentKey = getNextOrdinal(startKey)
            SpeakSettings.RewindAmount.TEN_VERSES -> {
                currentKey = startKey
                for (i in 1..10) {
                    currentKey = getNextOrdinal(currentKey)
                }
            }
            SpeakSettings.RewindAmount.NONE -> throw RuntimeException("Invalid settings")
        }
        startKey = currentKey
        endKey = currentKey
        clearNotificationAndWidgetTitles()
        ABEventBus.post(SpeakProgressEvent(book, currentKey, null))
    }

    override fun finishedUtterance(utteranceId: String) {}

    override fun startUtterance(utteranceId: String) {
        val state = utteranceState[utteranceId]
        currentUtteranceId = utteranceId
        if(state != null) {
            Log.i(TAG, "startUtterance $utteranceId $state")
            if(state.command is TextCommand && state.command.type == TextCommand.TextType.TITLE) {
                lastVerseWithTitle = state.startKey
            }
            ABEventBus.post(SpeakProgressEvent(
                book = state.book,
                key = BookAndKey(
                    state.startKey.key,
                    state.book,
                    OrdinalRange(state.startKey.ordinal!!.start, state.endKey.ordinal!!.start),
                ),
                speakCommand = state.command!!,
                forceFollow = stopOrdinal != null
            ))
        }
    }

    override fun reset() {
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

    override fun getCurrentlyPlayingKey(): BookAndKey = startKey
    override fun getCurrentlyPlayingBook(): Book = book

    override fun persistState() {
        CommonUtils.settings.apply {
            setString(PERSIST_BOOK, book.abbreviation)
            setString(PERSIST_KEY, startKey.osisID)
        }
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = CommonUtils.settings
        if(sharedPreferences.getString(PERSIST_BOOK) != null) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            val book = Books.installed().getBook(bookStr)
            if(book is Book) {
                this.book = book
            }
        }
        if(sharedPreferences.getString(PERSIST_KEY) != null) {
            val keyStr = sharedPreferences.getString(PERSIST_KEY, "")!!
            startKey = book.getBookAndKey(keyStr)?: return false
            endKey = startKey
            currentKey = startKey
            return true
        }
        return false
    }

    override fun clearPersistedState() {
        CommonUtils.settings.apply {
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
