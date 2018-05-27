package net.bible.service.device.speak

import android.content.res.Configuration
import android.content.res.Resources
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
import org.crosswire.jsword.versification.BibleNames
import java.util.*

private val PERSIST_BOOK = "SpeakBibleBook"
private val PERSIST_VERSE = "SpeakBibleVerse"
private val PERSIST_SETTINGS = "SpeakBibleSettings"

class SpeakBibleTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser,
                             initialBook: Book,
                             initialVerse: Verse): AbstractSpeakTextProvider() {


    private var book: Book
    private var currentVerse: Verse
    private var startVerse: Verse
    private var numVersesToRead = 1

    init {
        book = initialBook
        currentVerse = initialVerse
        startVerse = initialVerse
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
            val app = BibleApplication.getApplication()
            val oldConf = app.resources.configuration
            val newConf = Configuration(oldConf)
            _language = book.language.code
            newConf.setLocale(Locale(_language))
            _localizedResources = app.createConfigurationContext(newConf).resources
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
        startVerse = verse
        reset()
    }

    private fun skipEmptyVerses(): String {
        var text = getTextForCurrentItem()
        while(text.isEmpty()) {
            currentVerse = getNextVerse()
            text = getTextForCurrentItem()
        }
        return text
    }

    private fun getNextVerseText(): String {
        var text = skipEmptyVerses()

        val bookChanged = currentVerse.book != startVerse.book
        val app = getLocalizedResources()
        if(settings.chapterChanges && (bookChanged || currentVerse.chapter != startVerse.chapter)) {
            text =  app.getString(R.string.speak_chapter_changed) + " " + currentVerse.chapter + ". " + text
        }

        if(bookChanged) {
            val bookName = BibleNames.instance().getPreferredName(currentVerse.book)
            text = app.getString(R.string.speak_book_changed) + " " + bookName + ". " + text
        }
        return text
    }

    override fun getNextTextToSpeak(): String {
        // If there's something left from splitted verse, then we'll speak that first.
        var text = ""
        startVerse = currentVerse
        if(readList.isNotEmpty()) {
            text += readList.removeAt(0)
            currentVerse = getNextVerse()
        }

        for(i in 0 until numVersesToRead) {
            text += getNextVerseText()
            currentVerse = getNextVerse()
        }
        text = joinBreakingSentence(text)

        EventBus.getDefault().post(SpeakProggressEvent(book, startVerse, settings.synchronize))
        return text
    }

    private fun joinBreakingSentence(inText: String): String {
        // If verse does not end in period, add the part before period to the current reading
        var text = inText.trim()

        val regex = Regex("(.*)([.?!]+[\'\"]?+)")
        if(!text.matches(regex)) {
            val nextText = getTextForCurrentItem()
            val parts = nextText.split('.', '?', '!')
            text += " " + parts[0] + "."
            val rest = parts.slice(1 until parts.count()).joinToString { it }
            readList.add(rest)
        }
        return text

    }

    fun getStatusText(): String {
        return startVerse.name + if(startVerse != currentVerse) " - " +  currentVerse.name else ""
    }

    private fun getTextForCurrentItem(): String {
        return swordContentFacade.getTextToSpeak(book, currentVerse)
    }

    override fun pause(fractionCompleted: Float) {
        currentVerse = startVerse
    }

    private fun getNextVerse(): Verse = bibleTraverser.getNextVerse(book as AbstractPassageBook, currentVerse)

    override fun rewind() {
        currentVerse = bibleTraverser.getPrevVerse(book as AbstractPassageBook, currentVerse)
        startVerse = currentVerse
        reset()
    }

    override fun forward() {
        currentVerse = getNextVerse()
        startVerse = currentVerse
        reset()
    }

    override fun finishedUtterance(utteranceId: String?) {
    }

    override fun reset() {
        currentVerse = startVerse
        readList.clear()
    }

    override fun persistState() {
        CommonUtils.getSharedPreferences().edit()
                .putString(PERSIST_BOOK, book.name)
                .putString(PERSIST_VERSE, currentVerse.osisID)
                .apply()
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = CommonUtils.getSharedPreferences()
        if(sharedPreferences.contains(PERSIST_BOOK)) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            val verseStr = sharedPreferences.getString(PERSIST_VERSE, "")
            book = Books.installed().getBook(bookStr)
            val verse = book.getKey(verseStr) as RangedPassage
            currentVerse = verse.getVerseAt(0)
            clearPersistedState()
            return true
        }
        return false
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