package net.bible.service.device.speak

import android.content.res.Resources
import android.speech.tts.TextToSpeech
import de.greenrobot.event.EventBus
import kotlinx.serialization.SerializationException
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakProggressEvent
import net.bible.service.sword.SwordContentFacade
import net.bible.android.activity.R
import org.crosswire.jsword.book.Book
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
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleNames
import java.util.*


class SpeakBibleTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser,
                             private val bookmarkControl: BookmarkControl,
                             initialBook: Book,
                             initialVerse: Verse): AbstractSpeakTextProvider() {

    companion object {
        private val PERSIST_BOOK = "SpeakBibleBook"
        private val PERSIST_VERSE = "SpeakBibleVerse"
        private val PERSIST_SETTINGS = "SpeakBibleSettings"
    }

    private var book: Book
    private var startVerse: Verse
    private var endVerse: Verse
    private var currentVerse: Verse

    init {
        book = initialBook
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

    private var _localizedResources: Resources? = null
    private var _language: String = "en"

    private fun getLocalizedResources(): Resources
    {
        if(_localizedResources == null || _language != book.language.code) {
            _language = book.language.code
            _localizedResources = BibleApplication.getApplication().getLocalizedResources(_language)
        }
        return _localizedResources!!
    }


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

    fun setupReading(book: Book, verse: Verse) {
        this.book = book
        currentVerse = verse
        startVerse = verse
        endVerse = verse
        reset()
    }

    private fun skipEmptyVerses(verse: Verse): Verse {
        var text = getRawTextForVerse(verse)
        var result = verse
        while(text.isEmpty()) {
            result = getNextVerse(result)
            text = getRawTextForVerse(result)
        }
        return result
    }

    private fun getTextForVerse(prevVerse: Verse, verse: Verse): String {
        var text = getRawTextForVerse(verse)
        val res = getLocalizedResources()
        val bookName = BibleNames.instance().getPreferredNameInLocale(verse.book, Locale(_language))

        if(prevVerse.book != verse.book) {
            text = res.getString(R.string.speak_book_changed) + " " + bookName + " " +
                    res.getString(R.string.speak_chapter_changed) + " " + verse.chapter + ". " + text
        }
        else if(settings.chapterChanges && prevVerse.chapter != verse.chapter) {
            text = bookName + " " + res.getString(R.string.speak_chapter_changed) + " " + verse.chapter + ". " + text
        }


        return text.trim()
    }

    public override fun getNextTextToSpeak(): String {
        var text = ""
        val maxLength = TextToSpeech.getMaxSpeechInputLength()

        var verse = currentVerse
        startVerse = currentVerse

        // If there's something left from splitted verse, then we'll speak that first.
        if(readList.isNotEmpty()) {
            text += readList.removeAt(0)
            verse = getNextVerse(verse)
        }

        verse = skipEmptyVerses(verse)

        text += getTextForVerse(endVerse, verse)

        if(settings.continueSentences) {
            // If verse does not end in period, add the part before period to the current reading
            val regex = Regex("(.*)([.?!]+[`´”“\"']*\\W*)")
            var rest = ""

            while(!text.matches(regex)) {
                val nextVerse = getNextVerse(verse)
                val nextText = getTextForVerse(verse, nextVerse)
                val newText: String

                val parts = nextText.split('.', '?', '!')

                if(parts.size > 1) {
                    newText = text + " " + parts[0] + "."
                    rest = parts.slice(1 until parts.count()).joinToString { it }
                }
                else {
                    newText = text + " " + nextText
                    rest = ""
                }

                if(newText.length > maxLength) {
                    break
                }
                verse = nextVerse
                text = newText

            }
            if(rest.length > 0) {
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
        return text.trim();
    }

    fun getStatusText(): String {
        return startVerse.name + if(startVerse != endVerse) " - " +  endVerse.name else ""
    }

    fun getVerseRange(): VerseRange {
        val v11n = startVerse.versification
        return VerseRange(v11n, startVerse, endVerse)
    }

    private fun getRawTextForVerse(verse: Verse): String {
        return swordContentFacade.getTextToSpeak(book, verse)
    }

    internal override fun pause(fractionCompleted: Float) {
        currentVerse = startVerse
        saveBookmark()
        reset()
    }

    internal override fun stop() {
        saveBookmark()
        reset();
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

    internal override fun rewind() {
        currentVerse = getPrevVerse(startVerse)
        startVerse = currentVerse
        reset()
    }

    internal override fun forward() {
        currentVerse = getNextVerse(startVerse)
        startVerse = currentVerse
        reset()
    }

    internal override fun finishedUtterance(utteranceId: String?) {
    }

    override fun reset() {
        endVerse = startVerse
        readList.clear()
    }

    internal override fun persistState() {
        CommonUtils.getSharedPreferences().edit()
                .putString(PERSIST_BOOK, book.abbreviation)
                .putString(PERSIST_VERSE, startVerse.osisID)
                .apply()
    }

    internal override fun restoreState(): Boolean {
        val sharedPreferences = CommonUtils.getSharedPreferences()
        if(sharedPreferences.contains(PERSIST_BOOK)) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            book = Books.installed().getBook(bookStr)
        }
        if(sharedPreferences.contains(PERSIST_VERSE)) {
            val verseStr = sharedPreferences.getString(PERSIST_VERSE, "")
            val verse = book.getKey(verseStr) as RangedPassage
            startVerse = verse.getVerseAt(0)
            endVerse = startVerse
            currentVerse = startVerse
            return true
        }
        return false
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