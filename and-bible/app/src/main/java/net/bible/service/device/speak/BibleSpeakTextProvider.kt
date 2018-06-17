package net.bible.service.device.speak

import android.content.res.Resources
import android.speech.tts.TextToSpeech
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
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import kotlinx.serialization.json.JSON
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.format.osistohtml.osishandlers.SpeakCommands
import net.bible.service.format.osistohtml.osishandlers.TextCommand
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import java.util.*
import kotlin.collections.HashMap


class BibleSpeakTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser,
                             private val bookmarkControl: BookmarkControl,
                             initialBook: SwordBook,
                             initialVerse: Verse) : SpeakTextProvider {
    companion object {
        private val PERSIST_BOOK = "SpeakBibleBook"
        private val PERSIST_VERSE = "SpeakBibleVerse"
        private val PERSIST_SETTINGS = "SpeakBibleSettings"
    }

    private var book: SwordBook
    private var startVerse: Verse
    private var endVerse: Verse
    private var currentVerse: Verse
    private val bibleBooks = HashMap<String, String>()

    init {
        book = initialBook
        setupBook(initialBook)
        startVerse = initialVerse
        endVerse = initialVerse
        currentVerse = initialVerse
    }

    private var readList: ArrayList<String>
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

    private lateinit var localizedResources: Resources

    init {
        readList = ArrayList()
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
        if(book != this.book) {
            setupBook(book)
        }
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

    private fun getCommandsForVerse(prevVerse: Verse, verse: Verse): SpeakCommands {
        val cmds = getSpeakCommandsForVerse(verse)
        val res = localizedResources
        val bookName = bibleBooks[verse.book.osis]

        if(prevVerse.book != verse.book) {
            cmds.add(0, TextCommand("${res.getString(R.string.speak_book_changed)} $bookName "+
                    "${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))

        }
        else if(settings.chapterChanges && prevVerse.chapter != verse.chapter) {
            cmds.add(0, TextCommand("$bookName ${res.getString(R.string.speak_chapter_changed)} ${verse.chapter}. "))
        }
        return cmds
    }

    override fun getNextTextToSpeak(): SpeakCommands {
        val cmds = SpeakCommands()
        //val maxLength = TextToSpeech.getMaxSpeechInputLength()

        var verse = currentVerse
        startVerse = currentVerse

        // If there's something left from splitted verse, then we'll speak that first.
        if(readList.isNotEmpty()) {
            cmds.add(TextCommand(readList.removeAt(0)))
            verse = getNextVerse(verse)
        }

        verse = skipEmptyVerses(verse)

        cmds.addAll(getCommandsForVerse(endVerse, verse))

        if(settings.continueSentences) {

            // If verse does not end in period, add the part before period to the current reading
            val regex = Regex("(.*)([.?!]+[`´”“\"']*\\W*)")
            var rest = ""
            val lastCommand = cmds.last()
            if(lastCommand is TextCommand) {
                val text = lastCommand.text
                while (!text.matches(regex)) {
                    val nextVerse = getNextVerse(verse)
                    val nextCommands = getCommandsForVerse(verse, nextVerse)
                    val newText: String


                    val parts = nextText.split('.', '?', '!')

                    if (parts.size > 1) {
                        newText = "$cmds ${parts[0]}."
                        rest = parts.slice(1 until parts.count()).joinToString { it }
                    } else {
                        newText = "$cmds $nextText"
                        rest = ""
                    }

                    if (newText.length > maxLength) {
                        break
                    }
                    verse = nextVerse
                    cmds = newText

                }
            }
            if(rest.isNotEmpty()) {
                readList.add(rest)
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

        EventBus.getDefault().post(SpeakProggressEvent(book, startVerse, settings.synchronize))
        return cmds;
    }

    fun getStatusText(): String {
        return "${startVerse.name}${if (startVerse != endVerse) " - " + endVerse.name else ""}"
    }

    fun getVerseRange(): VerseRange {
        val v11n = startVerse.versification
        return VerseRange(v11n, startVerse, endVerse)
    }

    private val lruCache = LruCache<Pair<SwordBook, Verse>, SpeakCommands>(100)

    private fun getSpeakCommandsForVerse(verse: Verse): SpeakCommands {
        var cmds = lruCache.get(Pair(book, verse))
        if(cmds == null) {
            cmds = swordContentFacade.getSpeakCommands(book, verse)
            lruCache.put(Pair(book, verse), cmds)
        }
        return cmds.copy()
    }

    override fun pause(fractionCompleted: Float) {
        currentVerse = startVerse
        saveBookmark()
        reset()
    }

    override fun stop() {
        saveBookmark()
        reset();
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

    private fun getPrevVerse(verse: Verse): Verse = bibleTraverser.getPrevVerse(book as AbstractPassageBook, verse)
    private fun getNextVerse(verse: Verse): Verse = bibleTraverser.getNextVerse(book as AbstractPassageBook, verse)

    override fun rewind() {
        currentVerse = getPrevVerse(startVerse)
        startVerse = currentVerse
        reset()
    }

    override fun forward() {
        currentVerse = getNextVerse(startVerse)
        startVerse = currentVerse
        reset()
    }

    override fun finishedUtterance(utteranceId: String) {
    }

    override fun reset() {
        endVerse = startVerse
        readList.clear()
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
        // We do not want to do this.
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