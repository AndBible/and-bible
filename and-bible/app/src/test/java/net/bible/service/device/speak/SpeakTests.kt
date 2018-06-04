package net.bible.service.device.speak

import net.bible.android.TestBibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import net.bible.service.sword.SwordContentFacade
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
import org.robolectric.RobolectricTestRunner

@Config(qualifiers="fi", constants = BuildConfig::class, application = TestBibleApplication::class)
open class AbstractSpeakTests {
    lateinit var provider: SpeakBibleTextProvider
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

        val book = Books.installed().getBook("FinRK") as SwordBook // as AbstractPassageBook
        val v11n: Versification = book.versification
    }
}

@RunWith(RobolectricTestRunner::class)
class OtherSpeakTests: AbstractSpeakTests () {
    @Before
    fun setup() {
        provider = SpeakBibleTextProvider(swordContentFacade, bibleTraverser, book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(false, true, false)
    }

    @Test
    fun storePersistence() {
        provider.setupReading(book, getVerse("Ps.14.1"))
        val sharedPreferences = CommonUtils.getSharedPreferences()
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Ps.14.1"))
        assertThat(sharedPreferences.getString("SpeakBibleBook", ""), equalTo("FinRK"))
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        provider.pause(0.5f)
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Ps.14.2"))
        provider.setupReading(book, getVerse("Rom.5.20"))
        assertThat(range(), equalTo("Rom.5.20"))
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.20"))
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.5.21"))
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Rom.5.20"))
        text = provider.getNextTextToSpeak()
        assertThat(range(), equalTo("Rom.6.1"))
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Rom.6.1"))
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
class SpeakWithoutContinueSentences: AbstractSpeakTests (){
    @Before
    fun setup() {
        provider = SpeakBibleTextProvider(swordContentFacade, bibleTraverser, book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(false, true, false)
        //val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(BibleApplication.getApplication())
        //sharedPreferences.edit().putString("test", "12345").commit()
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
        assertThat(text, startsWith("Luku 14. Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))
    }

    private fun checkRomansBeginning() {
        assertThat(text, startsWith("Kirja vaihtuu, uusi kirja: Roomalaiskirje. Luku 1. Paavali, "))
        assertThat(text, endsWith("evankeliumia,"))
    }
    @Test
    fun chapterChangeMessage() {
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
        assertThat(text, startsWith("Luku 2"))
        for(i in 1..29) {
            text = provider.getNextTextToSpeak()
        }
        assertThat(text, startsWith("Luku 3"))
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
        provider = SpeakBibleTextProvider(swordContentFacade, bibleTraverser, book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(false, true, true)
    }

    private fun checkRomansBeginning() {
        assertThat(text, startsWith("Kirja vaihtuu, uusi kirja: Roomalaiskirje. Luku 1. Paavali, "))
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
        assertThat(text, startsWith("Luku 6. Mitä me"))
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