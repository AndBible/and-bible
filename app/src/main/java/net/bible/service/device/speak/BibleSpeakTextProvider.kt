/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
import net.bible.android.control.speak.SpeakSettings
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
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import java.util.Locale
import kotlin.collections.HashMap

class BibleSpeakTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser,
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
        const val FLAG_SHOW_ALL = FLAG_SHOW_BOOK or FLAG_SHOW_PERCENT
        private const val TAG = "Speak"
    }

    override val numItemsToTts = 100
    private var book = initialBook
    private var startVerse = initialVerse
    private var endVerse = initialVerse
    private var bookmarkDto : BookmarkDto? = null
    private var _currentVerse = initialVerse
    private var currentVerse: Verse
        get() = _currentVerse
        set(newValue) {
            // Skip verse 0, as we merge verse 0 to verse 1 in getSpeakCommands
            if(newValue.verse == 0) {
                _currentVerse = getNextVerse(newValue)
            }
            else {
                _currentVerse = newValue
            }
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

    private var readList = SpeakCommandArray()
    internal var settings = SpeakSettings.load()

    private val currentState: State
        get() {
            return utteranceState.get(currentUtteranceId) ?: State(book, startVerse, endVerse, currentVerse)
        }


    override fun updateSettings(speakSettingsChangedEvent: SpeakSettingsChangedEvent) {
        this.settings = speakSettingsChangedEvent.speakSettings
        Log.d(TAG, "SpeakSettings updated: $speakSettingsChangedEvent")
        val bookmarkDto = bookmarkDto
        if(speakSettingsChangedEvent.updateBookmark && bookmarkDto != null) {
            // If playback is paused or we are speaking, we need to update bookmark that is upon startVerse
            // (of which we will continue playback if unpaused)

            val oldPlaybackSettings = bookmarkDto.playbackSettings
            val newPlaybackSettings = speakSettingsChangedEvent.speakSettings.playbackSettings
            // Let's retain bookId and bookmarkWasCreated

            if (oldPlaybackSettings != null) {
                newPlaybackSettings.apply {
                    bookId = oldPlaybackSettings.bookId
                    bookmarkWasCreated = oldPlaybackSettings.bookmarkWasCreated
                }
            }
            bookmarkDto.playbackSettings = newPlaybackSettings
            this.bookmarkDto = bookmarkControl.addOrUpdateBookmark(bookmarkDto)
        }
    }

    fun setupBook(book: SwordBook) {
        this.book = book
        localizedResources =  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            BibleApplication.application.getLocalizedResources(book.language.code)
        } else {
            BibleApplication.application.resources
        }

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

    private fun getCurrentBooks(): ArrayList<SwordBook> {
        if(mockedBooks != null) {
            return mockedBooks as ArrayList<SwordBook>
        }
        val books = ArrayList<SwordBook>()
        val firstBook = windowRepository.activeWindow.pageManager.currentPage.currentDocument

        if(firstBook.bookCategory == BookCategory.BIBLE) {
            books.add(firstBook as SwordBook)
        }

        for (w in windowRepository.visibleWindows) {
            val book = w.pageManager.currentPage.currentDocument
            if (book !== firstBook && book.bookCategory == BookCategory.BIBLE) {
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
                cmds.add(TextCommand("$bookName ${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
                cmds.add(SilenceCommand())
            }
        }
        if(settings.multiTranslation) {
            for(b in getCurrentBooks()) {
                cmds.add(ChangeLanguageCommand(Locale(b.language.code)))
                cmds.addAll(getSpeakCommandsForVerse(verse, b))
            }
            cmds.add(ChangeLanguageCommand(Locale(book.language.code)))
        } else {
            cmds.addAll(getSpeakCommandsForVerse(verse))
        }
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
            Log.d(TAG, "Marked current utteranceID $utteranceId")
        }
        utteranceState.set(utteranceId, State(book, startVerse, endVerse, currentVerse, cmd))
        return cmd
    }

    private fun getMoreSpeakCommands(): SpeakCommandArray {
        val cmds = SpeakCommandArray()

        var verse = currentVerse
        startVerse = currentVerse

        // If there's something left from splitted verse, then we'll speak that first.
        if(readList.isNotEmpty()) {
            cmds.addAll(readList)
            readList.clear()
            verse = getNextVerse(verse)
        }

        verse = skipEmptyVerses(verse)

        cmds.addAll(getCommandsForVerse(endVerse, verse))

        if(!settings.multiTranslation) {
            // If verse does not end in period, add the part before period to the current reading
            val rest = SpeakCommandArray()

            while (!cmds.endsSentence) {
                val nextVerse = getNextVerse(verse)
                val nextCommands = getCommandsForVerse(verse, nextVerse)

                cmds.addUntilSentenceBreak(nextCommands, rest)
                verse = nextVerse
            }

            if (rest.isNotEmpty()) {
                readList.addAll(rest)
                currentVerse = verse
            } else {
                currentVerse = getNextVerse(verse)
            }
        }
        else {
            currentVerse = getNextVerse(verse)
        }
        endVerse = verse

        return cmds;
    }

    override fun getStatusText(showFlag: Int): String {
        val percent = bibleTraverser.getPercentOfBook(currentState.startVerse)
        var result = getVerseRange().name
        if(showFlag and FLAG_SHOW_PERCENT != 0) {
            result += " - $percent%"
        }
        if(showFlag and FLAG_SHOW_BOOK != 0) {
            result += " - ${currentState.book.abbreviation}"
        }
        return result
    }

    override fun getText(utteranceId: String): String {
        return currentState.command.toString()
    }

    fun getVerseRange(): VerseRange {
        return VerseRange(currentState.book.versification, currentState.startVerse, currentState.endVerse)
    }

    private fun getSpeakCommandsForVerse(verse: Verse, book: SwordBook? = null): SpeakCommandArray {
        val book_ = book ?: this.book
        var cmds = verseRenderLruCache.get(Pair(book_, verse))
        if(cmds == null) {
            cmds = swordContentFacade.getSpeakCommands(settings, book_, verse)
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

    override fun savePosition(fractionCompleted: Float) {}

    private var isSpeaking: Boolean = false

    override fun stop() {
        reset()
        if(isSpeaking) {
            updateBookmark()
        }
        isSpeaking = false
        bookmarkDto = null
    }

    override fun prepareForStartSpeaking() {
        readBookmark()
        isSpeaking = true
    }

    private fun readBookmark() {
        if(settings.autoBookmark) {
            val verse = currentVerse

            val bookmarkDto = bookmarkControl.getBookmarkByKey(verse)?: return
            val labelList = bookmarkControl.getBookmarkLabels(bookmarkDto)
            val speakLabel = bookmarkControl.getOrCreateSpeakLabel()
            val ttsLabel = labelList.find { it.id == speakLabel.id }

            if(ttsLabel != null) {
                val playbackSettings = bookmarkDto.playbackSettings?.copy()
                if(playbackSettings != null && settings.restoreSettingsFromBookmarks) {
                    playbackSettings.bookmarkWasCreated = null
                    playbackSettings.bookId = null
                    settings.playbackSettings = playbackSettings
                    settings.save()
                    Log.d("SpeakBookmark", "Loaded bookmark from $bookmarkDto ${settings.playbackSettings.speed}")
                }
                this.bookmarkDto = bookmarkDto
            }
        }
    }

    private fun removeBookmark() {
        var bookmarkDto = this.bookmarkDto
        if(bookmarkDto == null) return

        val labelList = bookmarkControl.getBookmarkLabels(bookmarkDto)
        val speakLabel = bookmarkControl.getOrCreateSpeakLabel()
        val ttsLabel = labelList.find { it.id == speakLabel.id }

        if(ttsLabel != null) {
            if(labelList.size > 1 || bookmarkDto.playbackSettings?.bookmarkWasCreated == false) {
                labelList.remove(ttsLabel)
                bookmarkDto.playbackSettings = null
                bookmarkDto = bookmarkControl.addOrUpdateBookmark(bookmarkDto)
                bookmarkControl.setBookmarkLabels(bookmarkDto, labelList)
                Log.d("SpeakBookmark", "Removed speak label from bookmark $bookmarkDto")
            }
            else {
                bookmarkControl.deleteBookmark(bookmarkDto)
                Log.d("SpeakBookmark", "Removed bookmark from $bookmarkDto")
            }
            this.bookmarkDto = null
        }
    }

    private fun saveBookmark(){
        val labelList = ArrayList<LabelDto>()
        if(settings.autoBookmark) {
            var bookmarkDto = bookmarkControl.getBookmarkByKey(startVerse)
            val playbackSettings = settings.playbackSettings.copy()
            playbackSettings.bookId = book.initials

            if(bookmarkDto == null) {
                playbackSettings.bookmarkWasCreated = true
                bookmarkDto = BookmarkDto()
                bookmarkDto.verseRange = VerseRange(startVerse.versification, startVerse)
                bookmarkDto.playbackSettings = playbackSettings
                bookmarkDto = bookmarkControl.addOrUpdateBookmark(bookmarkDto)
            }
            else {
                playbackSettings.bookmarkWasCreated = bookmarkDto.playbackSettings?.bookmarkWasCreated ?: false
                labelList.addAll(bookmarkControl.getBookmarkLabels(bookmarkDto))
                bookmarkDto.playbackSettings = playbackSettings
                bookmarkDto = bookmarkControl.addOrUpdateBookmark(bookmarkDto)
            }

            labelList.add(bookmarkControl.getOrCreateSpeakLabel())

            bookmarkControl.setBookmarkLabels(bookmarkDto, labelList)
            Log.d("SpeakBookmark", "Saved bookmark into $bookmarkDto, ${settings.playbackSettings.speed}")
            this.bookmarkDto = bookmarkDto
        }
    }

    private fun getNextVerse(verse: Verse): Verse = bibleTraverser.getNextVerse(book, verse)

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
             if(lastTitle != null && !lastTitle.equals(startVerse)) {
                 currentVerse = lastTitle
             }
             else {
                 if (startVerse.verse <= 1) {
                     currentVerse = bibleTraverser.getPrevChapter(book, startVerse)
                 } else {
                     currentVerse = Verse(startVerse.versification, startVerse.book, startVerse.chapter, 1)
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

        ABEventBus.getDefault().post(SpeakProgressEvent(book, startVerse, null))
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
        clearNotificationAndWidgetTitles();
        ABEventBus.getDefault().post(SpeakProgressEvent(book, startVerse, null))
    }

    override fun finishedUtterance(utteranceId: String) {}

    override fun startUtterance(utteranceId: String) {
        val state = utteranceState.get(utteranceId)
        currentUtteranceId = utteranceId
        if(state != null) {
            Log.d(TAG, "startUtterance $utteranceId $state")
            if(state.command is TextCommand && state.command.type == TextCommand.TextType.TITLE) {
                lastVerseWithTitle = state.startVerse
            }
            ABEventBus.getDefault().post(SpeakProgressEvent(state.book, state.startVerse, state.command!!))
        }
    }

    override fun reset() {
        val state = utteranceState.get(currentUtteranceId)
        Log.d(TAG, "Resetting. state: $currentUtteranceId $state")
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
        with (CommonUtils.getSharedPreferences().edit() ) {
            putString(PERSIST_BOOK, book.abbreviation)
            putString(PERSIST_VERSE, startVerse.osisID)
            apply()
        }
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = CommonUtils.getSharedPreferences()
        if(sharedPreferences.contains(PERSIST_BOOK)) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            val book = Books.installed().getBook(bookStr)
            if(book is SwordBook) {
                this.book = book
            }
        }
        if(sharedPreferences.contains(PERSIST_VERSE)) {
            val verseStr = sharedPreferences.getString(PERSIST_VERSE, "")
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
        with (CommonUtils.getSharedPreferences().edit() ) {
            remove(PERSIST_BOOK)
            remove(PERSIST_VERSE)
            apply()
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
