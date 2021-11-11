/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.device.speak

import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.util.LruCache
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakProgressEvent
import net.bible.service.sword.SwordContentFacade
import net.bible.android.activity.R
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import java.util.Locale
import kotlin.collections.HashMap

class BibleSpeakTextProvider(private val bibleTraverser: BibleTraverser,
                             private val bookmarkControl: BookmarkControl,
                             private val windowRepository: WindowRepository,
                             initialBook: SwordBook,
                             initialVerse: Verse) : SpeakTextProvider {

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
    private var book = initialBook
    private var startVerse = initialVerse
    private var endVerse = initialVerse
    private var bookmark : Bookmark? = null
    private var _currentVerse = initialVerse
    private var currentVerse: Verse
        get() = _currentVerse
        set(newValue) {
            // Skip verse 0, as we merge verse 0 to verse 1 in getSpeakCommands
            _currentVerse = nextIfZero(newValue)

        }

    private var lastVerseWithTitle: Verse? = null
    private val utteranceState = HashMap<String, State>()
    private var currentUtteranceId = ""
    private val currentCommands = SpeakCommandArray()

    private val bibleBooks = HashMap<String, String>()
    private val verseRenderLruCache = LruCache<Pair<SwordBook, Verse>, SpeakCommandArray>(100)
    private lateinit var localizedResources: Resources

    init {
        setupBook(initialBook)
    }

    private fun nextIfZero(verse: Verse) =
        if(verse.verse == 0) {
            bibleTraverser.getNextVerse(book, verse)
        } else {
            verse
        }
    private var readList = SpeakCommandArray()
    internal var settings = SpeakSettings.load()

    private val currentState: State get() = utteranceState[currentUtteranceId] ?: State(book, startVerse, endVerse, currentVerse)

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
            this.bookmark = bookmarkControl.addOrUpdateBookmark(bookmark)
        }
    }

    private fun setupBook(book: SwordBook) {
        this.book = book
        localizedResources = BibleApplication.application.getLocalizedResources(book.language.code)

        val locale = Locale(book.language.code)
        bibleBooks.clear()

        for(bibleBook in book.versification.bookIterator) {
            var bookName = BibleNames.instance().getPreferredNameInLocale(bibleBook, locale)
            bookName = bookName.replace("1.", localizedResources.getString(R.string.speak_first))
            bookName = bookName.replace("2.", localizedResources.getString(R.string.speak_second))
            bookName = bookName.replace("3.", localizedResources.getString(R.string.speak_third))
            bookName = bookName.replace("4.", localizedResources.getString(R.string.speak_fourth))
            bookName = bookName.replace("5.", localizedResources.getString(R.string.speak_fifth))
            bibleBooks[bibleBook.osis] = bookName
        }
    }

    fun setupReading(book: SwordBook, verse: Verse) {
        reset()
        setupBook(book)

        currentVerse = verse
        startVerse = verse
        endVerse = verse
    }

    // For tests. In production this is always null.
    var mockedBooks: ArrayList<SwordBook>? = null

    private val currentBooks: ArrayList<SwordBook> get() {
        if(mockedBooks != null) {
            return mockedBooks as ArrayList<SwordBook>
        }
        val books = ArrayList<SwordBook>()
        val firstBook = windowRepository.activeWindow.pageManager.currentPage.currentDocument

        if(firstBook?.bookCategory == BookCategory.BIBLE) {
            books.add(firstBook as SwordBook)
        }

        for (w in windowRepository.visibleWindows) {
            val book = w.pageManager.currentPage.currentDocument
            if (book !== firstBook && book?.bookCategory == BookCategory.BIBLE) {
                books.add(book as SwordBook)
            }
        }
        return books
    }

    private fun skipEmptyVerses(verse: Verse): Verse {
        var cmds = getSpeakCommandsForVerse(verse)
        var result = verse
        while(cmds.isEmpty()) {
            result = getNextVerse(result)
            cmds = getSpeakCommandsForVerse(result)
        }
        return result
    }

    private fun getCommandsForVerse(prevVerse: Verse, verse: Verse): SpeakCommandArray {
        val cmds = SpeakCommandArray()
        val res = localizedResources
        val bookName = bibleBooks[verse.book.osis]

        if(prevVerse.book != verse.book) {
            cmds.add(PreBookChangeCommand())
            cmds.add(TextCommand("${res.getString(R.string.speak_book_changed)} $bookName ${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
            cmds.add(SilenceCommand())
        }
        else if(prevVerse.chapter != verse.chapter) {
            if(settings.playbackSettings.speakChapterChanges) {
                cmds.add(PreChapterChangeCommand(settings))
                cmds.add(TextCommand("${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
                cmds.add(SilenceCommand())
            }
        }
        else if(verse.ordinal < prevVerse.ordinal) {
            // TODO: we could say something special related to repeating
            cmds.add(TextCommand("$bookName ${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
            cmds.add(SilenceCommand())
        }
        cmds.addAll(getSpeakCommandsForVerse(verse))
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
        utteranceState[utteranceId] = State(book, startVerse, endVerse, currentVerse, cmd)
        return cmd
    }

    private fun getMoreSpeakCommands(): SpeakCommandArray {
        val cmds = SpeakCommandArray()

        var verse = limitToRange(currentVerse)
        startVerse = verse

        // If there's something left from splitted verse, then we'll speak that first.
        if(readList.isNotEmpty()) {
            cmds.addAll(readList)
            readList.clear()
            verse = getNextVerse(verse)
        }

        verse = skipEmptyVerses(verse)

        cmds.addAll(getCommandsForVerse(endVerse, verse))

        // If verse does not end in period, add the part before period to the current reading
        val rest = SpeakCommandArray()

        while (!cmds.endsSentence) {
            val nextVerse = getNextVerse(verse)
            // We can have infinite loop if we are in repeat passage mode
            if(nextVerse.ordinal < verse.ordinal) {
                break
            }
            val nextCommands = getCommandsForVerse(verse, nextVerse)

            cmds.addUntilSentenceBreak(nextCommands, rest)
            verse = nextVerse
        }

        currentVerse = if (rest.isNotEmpty()) {
            readList.addAll(rest)
            verse
        } else {
            getNextVerse(verse)
        }

        endVerse = verse

        return cmds
    }

    override fun getStatusText(showFlag: Int): String {
        val verseRange = settings.playbackSettings.verseRange
        val percent = if(verseRange == null) {
            bibleTraverser.getPercentOfBook(currentState.startVerse)
        } else  {
            ((currentState.startVerse.ordinal - verseRange.start.ordinal).toFloat()
                    / (verseRange.end.ordinal-verseRange.start.ordinal) * 100).toInt()
        }
        var result = this.verseRange.name

        if(showFlag and FLAG_SHOW_STATUSITEMS != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

    override fun getText(utteranceId: String): String {
        return currentState.command.toString()
    }

    val verseRange: VerseRange get() = VerseRange(currentState.book.versification, currentState.startVerse, currentState.endVerse)

    private fun getSpeakCommandsForVerse(verse: Verse, book: SwordBook? = null): SpeakCommandArray {
        val book_ = book ?: this.book
        var cmds = verseRenderLruCache.get(Pair(book_, verse))
        if(cmds == null) {
            cmds = SwordContentFacade.getSpeakCommands(settings, book_, verse)
            verseRenderLruCache.put(Pair(book_, verse), cmds)
        }
        return cmds.copy()
    }

    override fun pause() {
        reset()
        currentVerse = startVerse
        updateBookmark()
        isSpeaking = false
    }

    private fun updateBookmark() {
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
        if(settings.autoBookmark) {
            val verse = currentVerse

            val bookmark = bookmarkControl.speakBookmarkForVerse(verse)?: return
            val labelList = bookmarkControl.labelsForBookmark(bookmark)
            val speakLabel = bookmarkControl.speakLabel
            val ttsLabel = labelList.find { it.id == speakLabel.id }

            if(ttsLabel != null) {
                val playbackSettings = bookmark.playbackSettings?.copy()
                if(playbackSettings != null && settings.restoreSettingsFromBookmarks) {
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
        var bookmark: Bookmark = this.bookmark ?: return

        val labelList = bookmarkControl.labelsForBookmark(bookmark).toMutableList()
        val speakLabel = bookmarkControl.speakLabel
        val ttsLabel = labelList.find { it.id == speakLabel.id }

        if(ttsLabel != null) {
            if(labelList.size > 1 || bookmark.playbackSettings?.bookmarkWasCreated == false) {
                labelList.remove(ttsLabel)
                bookmark.playbackSettings = null
                bookmark = bookmarkControl.addOrUpdateBookmark(bookmark)
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
        if(settings.autoBookmark) {
            var bookmark = bookmarkControl.firstBookmarkStartingAtVerse(startVerse)?.run {
                if(textRange != null) null else this
            }

            val playbackSettings = settings.playbackSettings.copy()
            playbackSettings.bookId = book.initials

            if(bookmark == null) {
                playbackSettings.bookmarkWasCreated = true
                bookmark = Bookmark(VerseRange(startVerse.versification, startVerse), null, true, null)
                bookmark.playbackSettings = playbackSettings
                bookmark = bookmarkControl.addOrUpdateBookmark(bookmark)
            }
            else {
                playbackSettings.bookmarkWasCreated = bookmark.playbackSettings?.bookmarkWasCreated ?: false
                labelList.addAll(bookmarkControl.labelsForBookmark(bookmark))
                bookmark.playbackSettings = playbackSettings
                bookmark = bookmarkControl.addOrUpdateBookmark(bookmark)
            }

            labelList.add(bookmarkControl.speakLabel)

            bookmarkControl.setLabelsForBookmark(bookmark, labelList.toList())
            Log.i("SpeakBookmark", "Saved bookmark into $bookmark, ${settings.playbackSettings.speed}")
            this.bookmark = bookmark
        }
    }

    private fun limitToRange(verse: Verse): Verse {
        val range = settings.playbackSettings.verseRange
        if(range != null && (verse.ordinal > range.end.ordinal || verse.ordinal < range.start.ordinal)) {
            return nextIfZero(range.start)
        }
        return nextIfZero(verse)
    }

    private fun getNextVerse(verse: Verse): Verse {
        return limitToRange(bibleTraverser.getNextVerse(book, verse))
    }

    override fun rewind(amount: SpeakSettings.RewindAmount?) {
        rewind(amount, false)
    }

    fun rewind(amount: SpeakSettings.RewindAmount?, autoRewind: Boolean) {
        val lastTitle = this.lastVerseWithTitle
        reset()
        val rewindAmount = amount?: SpeakSettings.RewindAmount.SMART
        val minimumVerse = Verse(startVerse.versification, startVerse.book, 1, 1)

        when(rewindAmount) {
         SpeakSettings.RewindAmount.SMART -> {
             currentVerse = if(lastTitle != null && lastTitle != startVerse) {
                 lastTitle
             } else {
                 if (startVerse.verse <= 1) {
                     bibleTraverser.getPrevChapter(book, startVerse)
                 } else {
                     Verse(startVerse.versification, startVerse.book, startVerse.chapter, 1)
                 }
             }
         }
         SpeakSettings.RewindAmount.ONE_VERSE -> {
             currentVerse = bibleTraverser.getPrevVerse(book, startVerse)
         }
         SpeakSettings.RewindAmount.TEN_VERSES -> {
            currentVerse = startVerse
            for(i in 1..10) {
                currentVerse = bibleTraverser.getPrevVerse(book, currentVerse)
            }
         }
         SpeakSettings.RewindAmount.NONE -> {}
        }

        if(autoRewind && currentVerse.ordinal < minimumVerse.ordinal) {
            currentVerse = minimumVerse
        }

        if(lastTitle == null || currentVerse.ordinal < lastTitle.ordinal) {
            clearNotificationAndWidgetTitles()
        }

        startVerse = currentVerse
        endVerse = currentVerse

        ABEventBus.getDefault().post(SpeakProgressEvent(book, verseRange, null))
    }

    private fun clearNotificationAndWidgetTitles() {
        // Clear title and text from widget and notification.
        ABEventBus.getDefault().post(SpeakProgressEvent(book, startVerse,
                TextCommand("", type=TextCommand.TextType.TITLE)))
        ABEventBus.getDefault().post(SpeakProgressEvent(book, startVerse,
                TextCommand("", type=TextCommand.TextType.NORMAL)))
    }

    override fun forward(amount: SpeakSettings.RewindAmount?) {
        reset()
        val rewindAmount = amount?: SpeakSettings.RewindAmount.SMART
        when(rewindAmount) {
            SpeakSettings.RewindAmount.SMART -> {
                currentVerse = bibleTraverser.getNextChapter(book, startVerse)

            }
            SpeakSettings.RewindAmount.ONE_VERSE ->
                currentVerse = bibleTraverser.getNextVerse(book, startVerse)
            SpeakSettings.RewindAmount.TEN_VERSES -> {
                currentVerse = startVerse
                for (i in 1..10) {
                    currentVerse = bibleTraverser.getNextVerse(book, currentVerse)
                }
            }
            SpeakSettings.RewindAmount.NONE -> throw RuntimeException("Invalid settings")
        }
        startVerse = currentVerse
        endVerse = currentVerse
        clearNotificationAndWidgetTitles()
        ABEventBus.getDefault().post(SpeakProgressEvent(book, verseRange, null))
    }

    override fun finishedUtterance(utteranceId: String) {}

    override fun startUtterance(utteranceId: String) {
        val state = utteranceState[utteranceId]
        currentUtteranceId = utteranceId
        if(state != null) {
            Log.i(TAG, "startUtterance $utteranceId $state")
            if(state.command is TextCommand && state.command.type == TextCommand.TextType.TITLE) {
                lastVerseWithTitle = state.startVerse
            }
            ABEventBus.getDefault().post(SpeakProgressEvent(state.book, VerseRange(state.book.versification, state.startVerse, state.endVerse), state.command!!))
        }
    }

    override fun reset() {
        val state = utteranceState[currentUtteranceId]
        Log.i(TAG, "Resetting. state: $currentUtteranceId $state")
        if(state != null) {
            startVerse = state.startVerse
            currentVerse = state.currentVerse
            endVerse = state.endVerse
            book = state.book
        }
        lastVerseWithTitle = null
        endVerse = startVerse
        readList.clear()
        currentCommands.clear()
        utteranceState.clear()
        currentUtteranceId = ""
        verseRenderLruCache.evictAll()
    }

    override fun getCurrentlyPlayingVerse(): Verse? {
        return currentVerse
    }

    override fun getCurrentlyPlayingBook(): SwordBook {
        return book
    }

    override fun persistState() {
        CommonUtils.settings.apply {
            setString(PERSIST_BOOK, book.abbreviation)
            setString(PERSIST_VERSE, startVerse.osisID)
        }
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = CommonUtils.settings
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
        CommonUtils.settings.apply {
            removeString(PERSIST_BOOK)
            removeString(PERSIST_VERSE)
        }
    }

    override fun getTotalChars(): Long {
        return 0
    }

    override fun getSpokenChars(): Long {
        return 0
    }

    override fun isMoreTextToSpeak(): Boolean {
        return true
    }
}
