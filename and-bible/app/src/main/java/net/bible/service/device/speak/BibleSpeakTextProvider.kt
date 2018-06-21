package net.bible.service.device.speak

import android.content.res.Resources
import android.util.Log
import android.util.LruCache
import de.greenrobot.event.EventBus
import kotlinx.serialization.SerializationException
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakProggressEvent
import net.bible.service.sword.SwordContentFacade
import net.bible.android.activity.R
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import kotlinx.serialization.json.JSON
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import java.util.Locale
import kotlin.collections.HashMap

class BibleSpeakTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser,
                             private val bookmarkControl: BookmarkControl,
                             initialBook: SwordBook,
                             initialVerse: Verse) : SpeakTextProvider {
    private data class State(val book: SwordBook,
                             val startVerse: Verse,
                             val endVerse: Verse,
                             val currentVerse: Verse,
                             val command: SpeakCommand? = null)

    companion object {
        private val PERSIST_BOOK = "SpeakBibleBook"
        private val PERSIST_VERSE = "SpeakBibleVerse"
        private val PERSIST_SETTINGS = "SpeakBibleSettings"
        private val TAG = "Speak"
    }

    override val numItemsToTts = 100
    private var book: SwordBook
    private var startVerse: Verse
    private var endVerse: Verse
    private var currentVerse: Verse
    private val utteranceState = HashMap<String, State>()
    private var currentUtteranceId = ""
    private val currentCommands = SpeakCommandArray()

    private val bibleBooks = HashMap<String, String>()
    private val verseRenderLruCache = LruCache<Pair<SwordBook, Verse>, SpeakCommandArray>(100)
    private lateinit var localizedResources: Resources

    private val currentState: State
        get() {
            return utteranceState.get(currentUtteranceId) ?: State(book, startVerse, endVerse, currentVerse)
        }

    init {
        book = initialBook
        setupBook(initialBook)
        startVerse = initialVerse
        endVerse = initialVerse
        currentVerse = initialVerse
    }

    private var readList = SpeakCommandArray()
    private var _settings: SpeakSettings? = null
    var settings: SpeakSettings
        get() = _settings?: SpeakSettings()
        set(value) {
            _settings = value
            val strSettings = JSON.stringify(settings)
            CommonUtils.getSharedPreferences().edit()
                    .putString(PERSIST_SETTINGS, strSettings)
                    .apply()
        }

    init {
        val sharedPreferences = CommonUtils.getSharedPreferences()
        if(sharedPreferences.contains(PERSIST_SETTINGS)) {
            val settingsStr = sharedPreferences.getString(PERSIST_SETTINGS, "")
            settings = try {
                JSON.parse(settingsStr)
            } catch (ex: SerializationException) {
                SpeakSettings()
            }
        }
    }

    fun setupBook(book: SwordBook) {
        this.book = book
        localizedResources = BibleApplication.getApplication().getLocalizedResources(book.language.code)

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
        setupBook(book)
        currentVerse = verse
        startVerse = verse
        endVerse = verse
        reset()
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
            if(settings.playEarconBook) {
                cmds.add(PreBookChangeCommand(settings))
            }
            if(settings.speakBookChanges) {
                cmds.add(TextCommand("${res.getString(R.string.speak_book_changed)} $bookName ${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
                cmds.add(SilenceCommand())
            }
        }
        else if(prevVerse.chapter != verse.chapter) {
            if(settings.playEarconChapter) {
                cmds.add(PreChapterChangeCommand(settings))
            }
            if(settings.speakChapterChanges) {
                cmds.add(TextCommand("$bookName ${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
                cmds.add(SilenceCommand())
            }
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
            Log.d(TAG, "Marked current utteranceID $utteranceId")
        }
        utteranceState.set(utteranceId, State(book, startVerse, endVerse, currentVerse, cmd))
        return cmd
    }

    private fun getMoreSpeakCommands(): SpeakCommandArray {
        val cmds = SpeakCommandArray()

        var verse = currentVerse

        // Skip verse 0, as we merge verse 0 to verse 1 in getSpeakCommands
        if(currentVerse.verse == 0) {
            verse = getNextVerse(verse)
            if(currentVerse == startVerse) {
                startVerse = verse
            }
            if(endVerse == currentVerse) {
                endVerse = verse
            }
            currentVerse = verse
        }

        startVerse = currentVerse

        // If there's something left from splitted verse, then we'll speak that first.
        if(readList.isNotEmpty()) {
            cmds.addAll(readList)
            readList.clear()
            verse = getNextVerse(verse)
        }

        verse = skipEmptyVerses(verse)

        cmds.addAll(getCommandsForVerse(endVerse, verse))

        if(settings.continueSentences) {
            // If verse does not end in period, add the part before period to the current reading
            val rest = SpeakCommandArray()

            while(!cmds.endsSentence) {
                val nextVerse = getNextVerse(verse)
                val nextCommands = getCommandsForVerse(verse, nextVerse)

                cmds.addUntilSentenceBreak(nextCommands, rest)
                verse = nextVerse
            }

            if(rest.isNotEmpty()) {
                readList.addAll(rest)
                currentVerse = verse
            }
            else {
                currentVerse = getNextVerse(verse)
            }
        }
        else {
            currentVerse = getNextVerse(verse)
        }

        endVerse = verse

        return cmds;
    }

    override fun getStatusText(): String {
        return "${getVerseRange().name} (${currentState.book.abbreviation})"
    }

    override fun getText(utteranceId: String): String {
        return currentState.command.toString()
    }

    fun getVerseRange(): VerseRange {
        return VerseRange(currentState.book.versification, currentState.startVerse, currentState.endVerse)
    }

    private fun getSpeakCommandsForVerse(verse: Verse): SpeakCommandArray {
        var cmds = verseRenderLruCache.get(Pair(book, verse))
        if(cmds == null) {
            cmds = swordContentFacade.getSpeakCommands(settings, book, verse)
            verseRenderLruCache.put(Pair(book, verse), cmds)
        }
        return cmds.copy()
    }

    override fun pause() {
        reset()
        currentVerse = startVerse
        saveBookmark()
    }

    override fun savePosition(fractionCompleted: Float) {}

    override fun stop() {
        reset();
        saveBookmark()
    }

    override fun prepareForContinue() {
        removeBookmark()
    }

    private fun removeBookmark() {
        if(settings.autoBookmarkLabelId != null) {
            val verse = currentVerse
            val labelDto = LabelDto()
            labelDto.id = settings.autoBookmarkLabelId
            val bookmarkList = bookmarkControl.getBookmarksWithLabel(labelDto)
            val bookmarkDto = bookmarkList.find { it.verseRange.start.equals(verse) && it.verseRange.end.equals(verse)}
            if(bookmarkDto != null) {
                bookmarkControl.deleteBookmark(bookmarkDto)
                EventBus.getDefault().post(SynchronizeWindowsEvent(true))
            }
        }
    }

    private fun saveBookmark(){
        if(settings.autoBookmarkLabelId != null) {
            var bookmarkDto = bookmarkControl.getBookmarkByKey(startVerse)
            if(bookmarkDto == null) {
                bookmarkDto = BookmarkDto()
                bookmarkDto.verseRange = VerseRange(startVerse.versification, startVerse)
                bookmarkDto = bookmarkControl.addBookmark(bookmarkDto)
            }
            else {
                bookmarkControl.refreshBookmarkDate(bookmarkDto)
            }
            if(settings.autoBookmarkLabelId != SpeakSettings.INVALID_LABEL_ID) {
                val labelDto = LabelDto()
                labelDto.id = settings.autoBookmarkLabelId
                bookmarkControl.setBookmarkLabels(bookmarkDto, listOf(labelDto))
            }
            EventBus.getDefault().post(SynchronizeWindowsEvent(true))
        }
    }

    private fun getNextVerse(verse: Verse): Verse = bibleTraverser.getNextVerse(book, verse)

    private fun rewind(amount: SpeakSettings.RewindAmount) {
        when(amount) {
         SpeakSettings.RewindAmount.FULL_CHAPTER -> {
             if (startVerse.verse <= 1) {
                 currentVerse = bibleTraverser.getPrevChapter(book, startVerse)
             } else {
                 currentVerse = Verse(startVerse.versification, startVerse.book, startVerse.chapter, 1)
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
        }
        startVerse = currentVerse
        endVerse = currentVerse
    }

    override fun rewind() {
        reset()
        rewind(settings.rewindAmount)
        EventBus.getDefault().post(SpeakProggressEvent(book, startVerse, settings.synchronize, null))
    }

    override fun autoRewind() {
        rewind(settings.autoRewindAmount)
    }

    override fun forward() {
        reset()
        when(settings.rewindAmount) {
            SpeakSettings.RewindAmount.FULL_CHAPTER ->
                currentVerse = bibleTraverser.getNextChapter(book, startVerse)
            SpeakSettings.RewindAmount.ONE_VERSE ->
                currentVerse = bibleTraverser.getNextVerse(book, startVerse)
            SpeakSettings.RewindAmount.TEN_VERSES -> {
                currentVerse = startVerse
                for (i in 1..10) {
                    currentVerse = bibleTraverser.getNextVerse(book, currentVerse)
                }
            }
        }
        startVerse = currentVerse
        endVerse = currentVerse
        EventBus.getDefault().post(SpeakProggressEvent(book, startVerse, settings.synchronize, null))
    }

    override fun finishedUtterance(utteranceId: String) {}

    override fun startUtterance(utteranceId: String) {
        val state = utteranceState.get(utteranceId)
        currentUtteranceId = utteranceId
        if(state != null) {
            Log.d(TAG, "startUtterance $utteranceId $state")
            EventBus.getDefault().post(SpeakProggressEvent(state.book, state.startVerse, settings.synchronize, state.command!!))
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
        endVerse = startVerse
        readList.clear()
        currentCommands.clear()
        utteranceState.clear()
        currentUtteranceId = ""
        verseRenderLruCache.evictAll()
    }

    override fun persistState() {
        CommonUtils.getSharedPreferences().edit()
                .putString(PERSIST_BOOK, book.abbreviation)
                .putString(PERSIST_VERSE, startVerse.osisID)
                .apply()
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
            startVerse = osisIdToVerse(verseStr)
            endVerse = startVerse
            currentVerse = startVerse
            return true
        }
        return false
    }

    private fun osisIdToVerse(osisId: String): Verse {
        val verse = book.getKey(osisId) as RangedPassage
        return verse.getVerseAt(0)
    }

    override fun clearPersistedState() {
        CommonUtils.getSharedPreferences().edit().remove(PERSIST_BOOK).remove(PERSIST_VERSE).apply()
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