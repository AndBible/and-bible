package net.bible.service.device.speak

import de.greenrobot.event.EventBus
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakProggressEvent
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse

private val PERSIST_BOOK = "SpeakBibleBook"
private val PERSIST_VERSE = "SpeakBibleVerse"

class SpeakBibleTextProvider(private val swordContentFacade: SwordContentFacade,
                             private val bibleTraverser: BibleTraverser): AbstractSpeakTextProvider() {

    private var currentItem: Pair<Book, Verse>? = null
    private var continuous: Boolean = false
    private var itemRead: Boolean = false

    fun setupReading(book: Book, verse: Verse, settings: SpeakSettings) {
        continuous = settings.continuous
        currentItem = Pair(book,verse)
        itemRead = false
    }

    override fun getNextTextToSpeak(): String {
        var text = ""
        if(currentItem == null) {
            return text
        }
        if (itemRead) {
            forward()
        }
        text = getTextForCurrentItem()
        while(text.isEmpty()) {
            forward()
            text = getTextForCurrentItem()
        }
        EventBus.getDefault().post(SpeakProggressEvent(currentItem!!.first, currentItem!!.second))
        return text
    }

    private fun getTextForCurrentItem(): String {
        return swordContentFacade.getTextToSpeak(currentItem!!.first, currentItem!!.second)

    }

    override fun pause(fractionCompleted: Float) {
    }

    override fun rewind() {
        currentItem = Pair(currentItem!!.first, bibleTraverser.getPrevVerse(currentItem!!.first as AbstractPassageBook?,
                currentItem!!.second))
        itemRead = false
    }

    override fun forward() {
        currentItem = Pair(currentItem!!.first, bibleTraverser.getNextVerse(currentItem!!.first as AbstractPassageBook?,
                currentItem!!.second))
        itemRead = false
    }

    override fun finishedUtterance(utteranceId: String?) {
        itemRead = true
    }

    override fun reset() {
        currentItem = null
        itemRead = false
    }

    override fun persistState() {
        CommonUtils.getSharedPreferences().edit()
                .putString(PERSIST_BOOK, currentItem!!.first.name)
                .putString(PERSIST_VERSE, currentItem!!.second.osisID)
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
        return currentItem != null
    }
}