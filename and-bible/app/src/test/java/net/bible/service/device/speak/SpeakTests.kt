package net.bible.service.device.speak

import net.bible.android.TestBibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import net.bible.service.sword.SwordContentFacade
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.Versification
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.hamcrest.Matchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.After
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

@Config(qualifiers="fi", constants = BuildConfig::class, application = TestBibleApplication::class)
open class AbstractSpeakTests {
    lateinit var provider: BibleSpeakTextProvider
    internal var text: String = ""

    internal fun getVerse(verseStr: String): Verse {
        val verse = book.getKey(verseStr) as RangedPassage
        return verse.getVerseAt(0)
    }

    internal fun range(): String? {
        return provider.getVerseRange().osisRef
    }

    companion object {
        val swordContentFacade = SwordContentFacade(BookmarkFormatSupport(), MyNoteFormatSupport())
        val documentBibleBooksFactory = DocumentBibleBooksFactory();
        val bibleTraverser = BibleTraverser(documentBibleBooksFactory);
        val bookmarkControl = BookmarkControl(swordContentFacade, mock(WindowControl::class.java),
                mock(AndroidResourceProvider::class.java));

        val book = Books.installed().getBook("FinRK") as SwordBook // as AbstractPassageBook
        val v11n: Versification = book.versification
    }
}

@RunWith(RobolectricTestRunner::class)
class TestPersistence: AbstractSpeakTests () {
    @Before
    fun setup() {
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(false, true, false)
    }

    @Test
    fun storePersistence() {
        provider.setupReading(book, getVerse("Ps.14.1"))
        val sharedPreferences = CommonUtils.getSharedPreferences()
        provider.persistState()
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Ps.14.1"))
        assertThat(sharedPreferences.getString("SpeakBibleBook", ""), equalTo("FinRK"))
    }

    @Test
    fun readPersistence() {
        val sharedPreferences = CommonUtils.getSharedPreferences()
        sharedPreferences.edit().putString("SpeakBibleBook", "FinRK").apply()
        sharedPreferences.edit().putString("SpeakBibleVerse", "Ps.14.1").apply()
        provider.setupReading(book, getVerse("Ps.14.1"))
        sharedPreferences.edit().putString("SpeakBibleBook", "FinRK").apply()
        sharedPreferences.edit().putString("SpeakBibleVerse", "Rom.5.1").apply()
        provider.restoreState()
        assertThat(range(), equalTo("Rom.5.1"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.1"))
        assertThat(text, startsWith("Koska siis"))
        assertThat(text, endsWith("Kristuksen kautta."))
    }
}
@RunWith(RobolectricTestRunner::class)
class AutoBookmarkTests: AbstractSpeakTests () {
    @Before
    fun setup() {
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        var label = LabelDto();
		label.setName("tts");
		label = bookmarkControl.saveOrUpdateLabel(label)

        provider.settings = SpeakSettings(false, true, false, label.id)
    }
	@After
	fun tearDown(){
		val bookmarks = bookmarkControl.getAllBookmarks()
		for (dto in bookmarks) {
			bookmarkControl.deleteBookmark(dto)
		}

		val labels = bookmarkControl.getAllLabels()
		for (dto in labels) {
			bookmarkControl.deleteLabel(dto);
		}

		DatabaseResetter.resetDatabase();
	}

    @Test
    fun autoBookmarkDisabled() {
        provider.settings = SpeakSettings(false, true, false, null)
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = provider.getNextTextToSpeak()
        provider.pause(0.5f);
        assertThat(bookmarkControl.allBookmarks.size, equalTo(0))
    }

    @Test
    fun autoBookmarkOnPause() {
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = provider.getNextTextToSpeak()
        provider.pause(0.5f);
        val labelDto = LabelDto()
        labelDto.id = provider.settings.autoBookmarkLabelId
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.1"))

        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        // test that it does not add another bookmark if there's already one with same key
        provider.pause(0.5f);
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.prepareForContinue()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(0))
    }

    @Test
    fun autoBookmarkOnStop() {
        provider.setupReading(book, getVerse("Ps.14.2"))
        text = provider.getNextTextToSpeak()
        provider.stop();
        val labelDto = LabelDto()
        labelDto.id = provider.settings.autoBookmarkLabelId
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.2"))
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.setupReading(book, getVerse("Ps.14.2"))
        provider.prepareForContinue()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(0))
    }
}

@RunWith(RobolectricTestRunner::class)
class SpeakWithoutContinueSentences: AbstractSpeakTests (){
    @Before
    fun setup() {
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(false, true, false)
    }


    @Test
    fun textProgression() {
        provider.setupReading(book, getVerse("Ps.14.1"))

        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Ps.14.1"))
        assertThat(text, startsWith("Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))

        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Ps.14.2"))
        assertThat(text, startsWith("Herra katsoo"))
        assertThat(text, endsWith("etsii Jumalaa."))

        provider.setupReading(book, getVerse("Ps.13.6"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Ps.13.6"))
        assertThat(text, startsWith("Mutta minä"))
        assertThat(text, endsWith("minulle hyvin."))

        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Ps.14.1"))
        assertThat(text, startsWith("Psalmit Luku 14. Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))
    }

    private fun checkRomansBeginning() {
        assertThat(text, startsWith("Kirja vaihtui. Roomalaiskirje Luku 1. Paavali, "))
        assertThat(text, endsWith("evankeliumia,"))
    }

    @Test
    fun chapterChangeMessage() {
        // Test that genesis follows revelations
        provider.setupReading(book, getVerse("Rev.22.21"))
        assertThat(range(), equalTo("Rev.22.21"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rev.22.21"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Gen.1.1"))
        // test that 1. is replaced with "Ensimmäinen" (first)
        assertThat(text, startsWith("Kirja vaihtui. Ensimmäinen Mooseksen kirja Luku 1. Alussa"))

        provider.setupReading(book, getVerse("Rom.1.1"))
        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("Paavali, "))
        assertThat(text, endsWith("evankeliumia,"))

        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("jonka Jumala"))
        assertThat(text, endsWith("Kirjoituksissa,"))

        provider.setupReading(book, getVerse("Acts.28.31"))
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        provider.setupReading(book, getVerse("Acts.28.30"))
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        provider.setupReading(book, getVerse("Acts.28.29"))
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        for(i in 1..32) {
            text = provider.getNextTextToSpeak()
        }
        assertThat(text, startsWith("Roomalaiskirje Luku 2"))
        for(i in 1..29) {
            text = provider.getNextTextToSpeak()
        }
        assertThat(text, startsWith("Roomalaiskirje Luku 3"))
    }

    @Test
    fun testBookWithoutOldTestament() {
        val book = Books.installed().getBook("ISV") as SwordBook

        provider.setupReading(book, getVerse("Rev.22.21"))
        assertThat(range(), equalTo("Rev.22.21"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rev.22.21"))
        assertThat(text, startsWith("May the grace of"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Gen-Matt.1.1"))
        assertThat(text, containsString("The Gospel According"))
    }

    @Test
    fun pauseRewindForward() {
        provider.setupReading(book, getVerse("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("ylenpalttiseksi,"))

        provider.pause(0.5F)
        assertThat(range(), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("ylenpalttiseksi,"))

        provider.rewind()
        assertThat(range(), equalTo("Rom.5.19"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.19"))
        assertThat(text, startsWith("Niin kuin"))
        assertThat(text, endsWith("vanhurskaiksi."))
        provider.pause(0.5F)
        assertThat(range(), equalTo("Rom.5.19"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.19"))
        assertThat(text, startsWith("Niin kuin"))
        assertThat(text, endsWith("vanhurskaiksi."))

        provider.forward()
        assertThat(range(), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("ylenpalttiseksi,"))
    }
}

@RunWith(RobolectricTestRunner::class)
class SpeakWithContinueSentences : AbstractSpeakTests() {
    @Before
    fun setup() {
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(false, true, true)
    }

    private fun checkRomansBeginning() {
        assertThat(text, startsWith("Kirja vaihtui. Roomalaiskirje Luku 1. Paavali, "))
        assertThat(text, endsWith("meidän Herrastamme."))
        assertThat(range(), equalTo("Rom.1.1-Rom.1.3"))
    }
    
    @Test
    fun chapterChangeMessage() {
        provider.setupReading(book, getVerse("Rom.1.1"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.1.1-Rom.1.3"))
        assertThat(text, startsWith("Paavali, "))
        assertThat(text, endsWith("meidän Herrastamme."))

        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.1.3-Rom.1.4"))
        assertThat(text, startsWith("Lihan puolesta"))
        assertThat(text, endsWith("Jumalan Pojaksi voimassa."))

        provider.setupReading(book, getVerse("Acts.28.31"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Acts.28.31"))
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        provider.setupReading(book, getVerse("Acts.28.30"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Acts.28.30"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Acts.28.31"))
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
    }

    @Test
    fun verseEndingWithSpecialCharacter() {
        provider.setupReading(book, getVerse("Acts.28.29"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Acts.28.29"))
        assertThat(text, containsString("]"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Acts.28.30"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Acts.28.31"))
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
    }

    @Test
    fun chapterChangeAfterJoinedSentences() {
        provider.setupReading(book, getVerse("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.6.1"))
        assertThat(text, startsWith("Roomalaiskirje Luku 6. Mitä me"))
        assertThat(text, endsWith("tulisi suureksi?"))

    }

    @Test
    fun pauseRewindForward() {
        provider.setupReading(book, getVerse("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))

        provider.pause(0.5F)
        assertThat(range(), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))

        provider.rewind()
        assertThat(range(), equalTo("Rom.5.19"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.19"))
        assertThat(text, startsWith("Niin kuin"))
        assertThat(text, endsWith("vanhurskaiksi."))
        provider.pause(0.5F)
        assertThat(range(), equalTo("Rom.5.19"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.19"))
        assertThat(text, startsWith("Niin kuin"))
        assertThat(text, endsWith("vanhurskaiksi."))

        provider.forward()
        assertThat(range(), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))
    }
}