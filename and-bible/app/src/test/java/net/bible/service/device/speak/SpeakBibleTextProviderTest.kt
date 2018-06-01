package net.bible.service.device.speak

import net.bible.android.TestBibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import net.bible.service.sword.SwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import robolectric.MyRobolectricTestRunner
import org.hamcrest.Matchers.*
import org.hamcrest.MatcherAssert.*

@RunWith(MyRobolectricTestRunner::class)
@Config(qualifiers="fi", constants = BuildConfig::class, application = TestBibleApplication::class)
class SpeakBibleTextProviderTest {
    lateinit var provider: SpeakBibleTextProvider
    private var text: String = ""

    @Before
    fun setup() {
        provider = SpeakBibleTextProvider(swordContentFacade, bibleTraverser, testBook, PS_14_1)
        provider.settings = SpeakSettings(false, true, false)
        //val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(BibleApplication.getApplication())
        //sharedPreferences.edit().putString("test", "12345").commit()
    }

    @Test
    fun textProgression() {
        provider.setupReading(testBook, PS_14_1)

        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("Luku 14. Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))

        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("Herra katsoo"))
        assertThat(text, endsWith("etsii Jumalaa."))

        provider.setupReading(testBook, PS_13_6)
        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("Mutta minä"))
        assertThat(text, endsWith("minulle hyvin."))

        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("Luku 14. Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))


    }

    private fun checkRomansBeginning() {
        assertThat(text, startsWith("Kirja vaihtuu, uusi kirja: Roomalaiskirje. Luku 1. Paavali, "))
        assertThat(text, endsWith("evankeliumia,"))
    }

    @Test
    fun chapterChangeMessage() {
        provider.setupReading(testBook, Verse(v11n, BibleBook.ROM, 1, 1))
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        text = provider.getNextTextToSpeak()
        assertThat(text, startsWith("jonka Jumala"))
        assertThat(text, endsWith("Kirjoituksissa,"))

        provider.setupReading(testBook, Verse(v11n, BibleBook.ACTS, 28, 31))
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        provider.setupReading(testBook, Verse(v11n, BibleBook.ACTS, 28, 30))
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        text = provider.getNextTextToSpeak()
        checkRomansBeginning()
        provider.setupReading(testBook, Verse(v11n, BibleBook.ACTS, 28, 29))
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

    companion object {
        val swordContentFacade = SwordContentFacade(BookmarkFormatSupport(), MyNoteFormatSupport())
        val documentBibleBooksFactory = DocumentBibleBooksFactory();
        val bibleTraverser = BibleTraverser(documentBibleBooksFactory);

        val testBook = Books.installed().getBook("FinRK") as AbstractPassageBook

        val v11n = testBook.versification

        val PS_14_7 = Verse(v11n, BibleBook.PS, 14, 7)
        val PS_14_1 = Verse(v11n, BibleBook.PS, 14, 1)
        val PS_13_6 = Verse(v11n, BibleBook.PS, 13, 6) // last verse
        val PS_14 = VerseRange(v11n, PS_14_1, PS_14_7)
    }
}