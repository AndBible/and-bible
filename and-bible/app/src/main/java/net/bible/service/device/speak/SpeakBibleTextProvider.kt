package net.bible.service.device.speak

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

private val PERSIST_BOOK = "SpeakBibleBook"
private val PERSIST_VERSE = "SpeakBibleVerse"
private val PERSIST_SETTINGS = "SpeakBibleSettings"

class SpeakBibleTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser,
                             initialBook: Book,
                             initialVerse: Verse): AbstractSpeakTextProvider() {


    private var currentItem: Pair<Book, Verse>
    init {
        currentItem = Pair(initialBook, initialVerse)
    }

    private var itemRead: Boolean = false
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

    fun setupReading(book: Book, verse: Verse) {
        currentItem = Pair(book,verse)
        itemRead = false
    }

    override fun getNextTextToSpeak(): String {
        var text: String
        val oldVerse = currentItem.second
        if (itemRead) {
            forward()
        }
        text = getTextForCurrentItem()
        while(text.isEmpty()) {
            forward()
            text = getTextForCurrentItem()
        }
        val currentVerse = currentItem.second
        val bookChanged = currentVerse.book != oldVerse.book
        val app = BibleApplication.getApplication();
        if(settings.chapterChanges && (bookChanged || currentVerse.chapter != oldVerse.chapter)) {
            text =  app.getString(R.string.speak_chapter_changed) + currentVerse.chapter + ". " + text
        }

        if(bookChanged) {
            val bookName = BibleNames.instance().getPreferredName(currentVerse.book)
            text = app.getString(R.string.speak_book_changed) + bookName + ". " + text
        }

        EventBus.getDefault().post(SpeakProggressEvent(currentItem.first, currentItem.second, settings.synchronize))
        return text
    }

    fun getStatusText(): String {
        return currentItem.second.name
    }

    private fun getTextForCurrentItem(): String {
        return swordContentFacade.getTextToSpeak(currentItem.first, currentItem.second)
    }

    override fun pause(fractionCompleted: Float) {
        itemRead = false
    }

    override fun rewind() {
        currentItem = Pair(currentItem.first, bibleTraverser.getPrevVerse(currentItem.first as AbstractPassageBook,
                currentItem.second))
        itemRead = false
    }

    override fun forward() {
        currentItem = Pair(currentItem.first, bibleTraverser.getNextVerse(currentItem.first as AbstractPassageBook,
                currentItem.second))
        itemRead = false
    }

    override fun finishedUtterance(utteranceId: String?) {
        itemRead = true
    }

    override fun reset() {
        itemRead = false
    }

    override fun persistState() {
        CommonUtils.getSharedPreferences().edit()
                .putString(PERSIST_BOOK, currentItem.first.name)
                .putString(PERSIST_VERSE, currentItem.second.osisID)
                .apply()
    }

    override fun restoreState(): Boolean {
        val sharedPreferences = CommonUtils.getSharedPreferences()
        if(sharedPreferences.contains(PERSIST_BOOK)) {
            val bookStr = sharedPreferences.getString(PERSIST_BOOK, "")
            val verseStr = sharedPreferences.getString(PERSIST_VERSE, "")
            val book = Books.installed().getBook(bookStr)
            val verse = book.getKey(verseStr)
            currentItem = Pair(book, (verse as RangedPassage).getVerseAt(0))
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