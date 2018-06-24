package net.bible.service.device.speak

import net.bible.android.TestBibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.PlaybackSettings
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.common.CommonUtils
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import net.bible.service.sword.SwordContentFacade
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.RangedPassage
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.hamcrest.Matchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.After
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

var idCount = 0;

@Config(qualifiers="fi", constants = BuildConfig::class, application = TestBibleApplication::class)
open class AbstractSpeakTests {
    lateinit var provider: BibleSpeakTextProvider
    internal var text: String = ""
    lateinit var book: SwordBook

    @Before
    open fun setup() {
        book = Books.installed().getBook("FinRK") as SwordBook
    }

	@After
	fun tearDown(){
		DatabaseResetter.resetDatabase();
	}
    protected fun getVerse(verseStr: String): Verse {
        val verse = book.getKey(verseStr) as RangedPassage
        return verse.getVerseAt(0)
    }

    protected fun range(): String? {
        return provider.getVerseRange().osisRef
    }

    protected fun nextText(): String {
        var cmd: SpeakCommand
        do {
            val utteranceId = "id-${idCount++}"
            cmd = provider.getNextSpeakCommand(utteranceId)
            provider.startUtterance(utteranceId)
        } while (!(cmd is TextCommand))

        return cmd.text
    }

    companion object {
        val swordContentFacade = SwordContentFacade(BookmarkFormatSupport(), MyNoteFormatSupport())
        val documentBibleBooksFactory = DocumentBibleBooksFactory();
        val bibleTraverser = BibleTraverser(documentBibleBooksFactory);
        val bookmarkControl = BookmarkControl(swordContentFacade, mock(WindowControl::class.java),
                mock(AndroidResourceProvider::class.java));
    }
}

@RunWith(RobolectricTestRunner::class)
open class OsisToBibleSpeakTests: AbstractSpeakTests() {
    private lateinit var s: SpeakSettings
    @Before
    override fun setup() {
        super.setup()
        s = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true))
    }

    @Test
    fun testSentenceBreak() {
        val cmds = SpeakCommandArray()
        cmds.add(TextCommand("test 1 test 2"))
        cmds.add(TextCommand("test 3. test 4"))
        cmds.add(TextCommand("test 5"))
        val cmds2 = SpeakCommandArray()
        val rest = SpeakCommandArray()
        cmds2.addUntilSentenceBreak(cmds, rest)
        assertThat((cmds2[0] as TextCommand).text, equalTo("test 1 test 2 test 3."))
        assertThat((rest[0] as TextCommand).text, equalTo("test 4 test 5"))
        assertThat(rest.size, equalTo(1))
        assertThat(cmds2.size, equalTo(1))
        assertThat(cmds.size, equalTo(1))
    }

    @Test
    fun testAdd() {
        val cmds = SpeakCommandArray()
        cmds.add(TextCommand("test 1"))
        cmds.add(0, TextCommand("test 2"))
        assertThat((cmds[0] as TextCommand).text, equalTo("test 2 test 1"))
    }

    @Test
    fun testAddAll() {
        val cmds = SpeakCommandArray()
        val cmds2 = arrayListOf(TextCommand("test 1"), TextCommand("test 2"), TextCommand("test 3"))
        cmds.addAll(cmds2)
        assertThat((cmds[0] as TextCommand).text, equalTo("test 1 test 2 test 3"))
    }

    @Test
    fun testSentenceBreak2() {
        val cmds = ArrayList<SpeakCommand>()
        cmds.add(TextCommand("test 1 test 2"))
        cmds.add(TextCommand("test 3. test 4"))
        cmds.add(TextCommand("test 5"))
        val cmds2 = SpeakCommandArray()
        val rest = ArrayList<SpeakCommand>()
        cmds2.addUntilSentenceBreak(cmds, rest)
        assertThat((cmds2[0] as TextCommand).text, equalTo("test 1 test 2 test 3."))
        assertThat((rest[0] as TextCommand).text, equalTo("test 4"))
        assertThat((rest[1] as TextCommand).text, equalTo("test 5"))
        assertThat(rest.size, equalTo(2))
        assertThat(cmds2.size, equalTo(1))
        assertThat(cmds.size, equalTo(3))
    }
    @Test
    fun testTitleFinRK() {
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.1")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.2")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
        assertThat(cmds.size, equalTo( 4))
    }

    @Test
    fun testTitleEsv() {
        book = Books.installed().getBook("ESV2011") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.1")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.2")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
        assertThat(cmds.size, equalTo( 4))
    }

    @Test
    fun testTitleSTLK() { // TOOD: this is not yet released bible!
        book = Books.installed().getBook("STLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.1")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.2")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
        assertThat(cmds.size, equalTo( 4))
    }

    @Test
    fun testParagraphChangeRK() {
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.23")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.24")))
        assertThat("Command is of correct type", cmds[0] is TextCommand)
        assertThat("Command is of correct type", cmds[1] is ParagraphChangeCommand)
        assertThat("Command is of correct type", cmds[2] is TextCommand)
        assertThat(cmds.size, equalTo( 3))
    }

    @Test
    fun testParagraphChangeESV() {
        book = Books.installed().getBook("ESV2011") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.clear();
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.23")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.24")))
        assertThat("Command is of correct type", cmds[0] is TextCommand)
        assertThat("Command is of correct type", cmds[1] is ParagraphChangeCommand)
        assertThat("Command is of correct type", cmds[2] is TextCommand)
        assertThat(cmds.size, equalTo( 3))
    }

    @Test
    fun testParagraphChangeSTLK() { // TOOD: this is not yet released bible!
        book = Books.installed().getBook("STLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.25")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.26")))
        assertThat("Command is of correct type", cmds[0] is TextCommand)
        assertThat("Command is of correct type", cmds[1] is ParagraphChangeCommand)
        assertThat("Command is of correct type", cmds[2] is TextCommand)
        assertThat(cmds.size, equalTo( 3))
    }

    @Test
    fun testQuotationMarkAnomalySTLK() { // TOOD: this is not yet released bible!
        book = Books.installed().getBook("STLK2017") as SwordBook
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl, book, getVerse("Ps.14.1"))
        provider.setupReading(book, getVerse("Exod.31.8"))
        val cmd = provider.getNextSpeakCommand("id-1") as TextCommand
        assertThat(cmd.text, startsWith("pöydän varusteineen"))
        assertThat(cmd.text, endsWith("heitä tekemään.\""))
        assertThat("", provider.getNextSpeakCommand("id-1") is ParagraphChangeCommand)
        assertThat("", provider.getNextSpeakCommand("id-1") is PreTitleCommand)
        assertThat((provider.getNextSpeakCommand("id-1") as TextCommand).text, equalTo("Sapatti"))
        assertThat("", provider.getNextSpeakCommand("id-1") is SilenceCommand)
        val cmd2 = provider.getNextSpeakCommand("id-1") as TextCommand
        assertThat(cmd2.text, startsWith("Herra puhui"))
        assertThat(cmd2.text, endsWith("pyhitän teidät."))
    }

    @Test
    fun testDivinenameInTitle() { // TOOD: this is not yet released bible!
        val s = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true,  speakTitles = true),replaceDivineName = true)
        book = Books.installed().getBook("STLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Exod.19.1")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat((cmds[1] as TextCommand).text, equalTo("Saapuminen Siinaille. Jahve ilmestyy"))
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
    }

    @Test
    fun testDivinenameInText() { // TOOD: this is not yet released bible!
        val s = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true,  speakTitles = true),replaceDivineName = true)
        book = Books.installed().getBook("STLK2017") as SwordBook

        val cmds = swordContentFacade.getSpeakCommands(s, book, getVerse("Exod.19.3"))
        assertThat((cmds[0] as TextCommand).text, containsString("ja Jahve huusi"))
    }

}

@RunWith(RobolectricTestRunner::class)
class TestPersistence: AbstractSpeakTests () {
    @Before
    override fun setup() {
        super.setup()
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true,  speakTitles = false))
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
        text = nextText()
        assertThat(range(), equalTo("Rom.5.1"))
        assertThat(text, startsWith("Koska siis"))
        assertThat(text, endsWith("Kristuksen kautta."))
    }
}
@RunWith(RobolectricTestRunner::class)
class AutoBookmarkTests: AbstractSpeakTests () {
    @Before
    override fun setup() {
        super.setup()
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        var label = LabelDto();
        label.name = "tts";
		label = bookmarkControl.saveOrUpdateLabel(label)

        val settings = SpeakSettings(autoBookmarkLabelId = label.id)
        settings.save()
    }

    @Test
    fun autoBookmarkDisabled() {
        provider.settings = SpeakSettings(autoBookmarkLabelId = null)
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = nextText()
        provider.pause();
        assertThat(bookmarkControl.allBookmarks.size, equalTo(0))
    }

    @Test
    fun autoBookmarkOnPauseAddLabel() {
        var dto = BookmarkDto()
        val verse = getVerse("Ps.14.1")
        dto.verseRange = VerseRange(verse.versification, verse)
        dto = bookmarkControl.addOrUpdateBookmark(dto)
        var labelDto= LabelDto()
        labelDto.name = "Another"
        labelDto = bookmarkControl.saveOrUpdateLabel(labelDto)
        bookmarkControl.setBookmarkLabels(dto, listOf(labelDto))

        provider.setupReading(book, verse)
        text = nextText()
        provider.pause();
        labelDto.id = provider.settings.autoBookmarkLabelId
        dto = bookmarkControl.getBookmarkByKey(verse)
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.pause()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.prepareForContinue()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
    }

    @Test
    fun autoBookmarkOnPauseAddLabelAndSettings() {
        val settings = SpeakSettings(restoreSettingsFromBookmarks = true, autoBookmarkLabelId = SpeakSettings.load().autoBookmarkLabelId)
        settings.save()
        var dto = BookmarkDto()
        val verse = getVerse("Ps.14.1")
        dto.verseRange = VerseRange(verse.versification, verse)
        dto = bookmarkControl.addOrUpdateBookmark(dto)
        var labelDto= LabelDto()
        labelDto.name = "Another"
        labelDto = bookmarkControl.saveOrUpdateLabel(labelDto)
        bookmarkControl.setBookmarkLabels(dto, listOf(labelDto))

        provider.setupReading(book, verse)
        text = nextText()
        provider.pause();
        labelDto.id = provider.settings.autoBookmarkLabelId
        dto = bookmarkControl.getBookmarkByKey(verse)
        assertThat(dto.playbackSettings, notNullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.pause()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.prepareForContinue()
        dto = bookmarkControl.getBookmarkByKey(verse)
        assertThat(dto.playbackSettings, nullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
    }

    @Test
    fun autoBookmarkOnPauseCreateNewSaveSettings() {
        val settings = SpeakSettings(restoreSettingsFromBookmarks = true, autoBookmarkLabelId = SpeakSettings.load().autoBookmarkLabelId)
        settings.save()
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = nextText()
        provider.pause();
        val labelDto = LabelDto()
        labelDto.id = provider.settings.autoBookmarkLabelId
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        assertThat(bookmark.playbackSettings, notNullValue())
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.1"))

        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        // test that it does not add another bookmark if there's already one with same key
        provider.pause();
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.prepareForContinue()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(0))
    }

    @Test
    fun autoBookmarkOnPauseCreateNew() {
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = nextText()
        provider.pause();
        val labelDto = LabelDto()
        labelDto.id = provider.settings.autoBookmarkLabelId
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        //assertThat(bookmark.playbackSettings, notNullValue())
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.1"))

        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        // test that it does not add another bookmark if there's already one with same key
        provider.pause();
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.prepareForContinue()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(0))
    }

    @Test
    fun autoBookmarkOnStop() {
        provider.setupReading(book, getVerse("Ps.14.2"))
        text = nextText()
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
class SpeakWithContinueSentences : AbstractSpeakTests() {
    @Before
    override fun setup() {
        super.setup()
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = false))
    }

    private fun checkRomansBeginning() {
        assertThat(text, equalTo("Kirja vaihtui. Roomalaiskirje Luku 1."))
        text = nextText();
        assertThat(text, startsWith("Paavali, "))
        assertThat(text, endsWith("meidän Herrastamme."))
        assertThat(range(), equalTo("Rom.1.1-Rom.1.3"))
    }

    @Test
    fun textProgression() {
        provider.setupReading(book, getVerse("Ps.14.1"))

        text = nextText()
        assertThat(range(), equalTo("Ps.14.1"))
        assertThat(text, startsWith("Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))

        text = nextText()
        assertThat(range(), equalTo("Ps.14.2"))
        assertThat(text, startsWith("Herra katsoo"))
        assertThat(text, endsWith("etsii Jumalaa."))

        provider.setupReading(book, getVerse("Ps.13.6"))
        text = nextText()
        assertThat(range(), equalTo("Ps.13.6"))
        assertThat(text, startsWith("Mutta minä"))
        assertThat(text, endsWith("minulle hyvin."))

        text = nextText()
        assertThat(range(), equalTo("Ps.14.1"))
        assertThat(text, equalTo("Psalmit Luku 14.")) // there's title after this
        text = nextText()
        assertThat(range(), equalTo("Ps.14.1"))
        assertThat(text, startsWith("Musiikinjohtajalle"))
        assertThat(text, endsWith("tekee hyvää."))
    }

    @Test
    @Config(qualifiers="en")
    fun testBookWithoutOldTestament() {
        val book = Books.installed().getBook("ISV") as SwordBook

        provider.setupReading(book, getVerse("Rev.22.21"))
        assertThat(range(), equalTo("Rev.22.21"))
        text = nextText()
        assertThat(range(), equalTo("Rev.22.21"))
        assertThat(text, startsWith("May the grace of"))
        text = nextText()
        assertThat(range(), equalTo("Gen-Matt.1.1"))
        assertThat(text, equalTo("Book changed. Matthew Chapter 1."))
        text = nextText()
        assertThat(text, startsWith("The Gospel According"))
    }

    
    @Test
    fun chapterChangeMessage() {
        provider.setupReading(book, getVerse("Rom.1.1"))
        text = nextText()
        assertThat(range(), equalTo("Rom.1.1-Rom.1.3"))
        assertThat(text, startsWith("Paavali, "))
        assertThat(text, endsWith("meidän Herrastamme."))

        text = nextText()
        assertThat(range(), equalTo("Rom.1.3-Rom.1.4"))
        assertThat(text, startsWith("Lihan puolesta"))
        assertThat(text, endsWith("Jumalan Pojaksi voimassa."))

        provider.setupReading(book, getVerse("Acts.28.31"))
        text = nextText()
        assertThat(range(), equalTo("Acts.28.31"))
        text = nextText()
        checkRomansBeginning()
        provider.setupReading(book, getVerse("Acts.28.30"))
        text = nextText()
        assertThat(range(), equalTo("Acts.28.30"))
        text = nextText()
        assertThat(range(), equalTo("Acts.28.31"))
        text = nextText()
        checkRomansBeginning()
    }

    @Test
    fun verseEndingWithSpecialCharacter() {
        provider.setupReading(book, getVerse("Acts.28.29"))
        text = nextText()
        assertThat(range(), equalTo("Acts.28.29"))
        assertThat(text, containsString("]"))
        text = nextText()
        assertThat(range(), equalTo("Acts.28.30"))
        text = nextText()
        assertThat(range(), equalTo("Acts.28.31"))
        text = nextText()
        checkRomansBeginning()
    }

    @Test
    fun chapterChangeAfterJoinedSentences() {
        provider.setupReading(book, getVerse("Rom.5.20"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        text = nextText()
        assertThat(range(), equalTo("Rom.6.1"))
        assertThat(text, equalTo("Roomalaiskirje Luku 6."))
        text = nextText()
        assertThat(text, startsWith("Mitä me"))
        assertThat(text, endsWith("tulisi suureksi?"))

    }

    @Test
    fun pauseRewindForwardOneVerse() {
        provider.setupReading(book, getVerse("Rom.5.20"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))

        provider.pause()
        assertThat(range(), equalTo("Rom.5.20"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))

        provider.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
        assertThat(range(), equalTo("Rom.5.19"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.19"))
        assertThat(text, startsWith("Niin kuin"))
        assertThat(text, endsWith("vanhurskaiksi."))
        provider.pause()
        assertThat(range(), equalTo("Rom.5.19"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.19"))
        assertThat(text, startsWith("Niin kuin"))
        assertThat(text, endsWith("vanhurskaiksi."))

        provider.forward(SpeakSettings.RewindAmount.ONE_VERSE)
        assertThat(range(), equalTo("Rom.5.20"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))
    }

    @Test
    fun pauseRewindForwardNormal() {
        provider.setupReading(book, getVerse("Rom.5.20"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))

        provider.pause()
        assertThat(range(), equalTo("Rom.5.20"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.20-Rom.5.21"))
        assertThat(text, startsWith("Laki kuitenkin"))
        assertThat(text, endsWith("meidän Herramme, kautta."))

        provider.rewind(null)
        assertThat(range(), equalTo("Rom.5.1"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.1"))

        provider.forward(null)
        assertThat(range(), equalTo("Rom.6.1"))
        text = nextText()
        assertThat(range(), equalTo("Rom.6.1"))
    }

    @Test
    fun rewind() {
        provider.settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true))
        provider.setupReading(book, getVerse("Rom.5.11"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.11"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.12"))
        assertThat(text, equalTo("Aadam ja Kristus")) // title
        text = nextText()
        assertThat(range(), equalTo("Rom.5.12")) // verse text
        text = nextText()
        assertThat(range(), equalTo("Rom.5.13"))

        provider.rewind(null)
        assertThat(range(), equalTo("Rom.5.12"))
        provider.rewind(null)
        assertThat(range(), equalTo("Rom.5.1"))
        provider.rewind(null)
        assertThat(range(), equalTo("Rom.4.1"))

        provider.forward(null)
        assertThat(range(), equalTo("Rom.5.1"))
        provider.forward(null)
        assertThat(range(), equalTo("Rom.6.1"))
    }

    @Test
    fun rewind2() {
        provider.settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true))
        provider.setupReading(book, getVerse("Rom.5.11"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.11"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.12"))
        assertThat(text, equalTo("Aadam ja Kristus")) // title
        text = nextText()
        assertThat(range(), equalTo("Rom.5.12")) // verse text
        text = nextText()
        assertThat(range(), equalTo("Rom.5.13"))

        provider.rewind(null)
        text = nextText()
        assertThat(range(), equalTo("Rom.5.12"))
        provider.rewind(null)
        text = nextText()
        assertThat(range(), equalTo("Rom.5.1"))
        provider.rewind(null)
        text = nextText()
        assertThat(range(), equalTo("Rom.4.1"))

        provider.forward(null)
        text = nextText()
        assertThat(range(), equalTo("Rom.5.1"))
        provider.forward(null)
        text = nextText()
        assertThat(range(), equalTo("Rom.6.1"))
    }

    @Test
    fun autorewind() {
        var settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true), autoRewindAmount = SpeakSettings.RewindAmount.ONE_VERSE)
        settings.save()
        provider.setupReading(book, getVerse("Rom.5.11"))
        provider.autoRewind()
        assertThat(range(), equalTo("Rom.5.10"))

        settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true), autoRewindAmount = SpeakSettings.RewindAmount.TEN_VERSES)
        settings.save()
        provider.setupReading(book, getVerse("Rom.5.12"))
        provider.autoRewind()
        assertThat(range(), equalTo("Rom.5.2"))

        settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true), autoRewindAmount = SpeakSettings.RewindAmount.SMART)
        settings.save()
        provider.setupReading(book, getVerse("Rom.5.12"))
        provider.autoRewind()
        assertThat(range(), equalTo("Rom.5.1"))
    }

    @Test
    fun forward() {
        provider.settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true))

        provider.setupReading(book, getVerse("Rom.5.11"))
        text = nextText()
        assertThat(range(), equalTo("Rom.5.11"))
        provider.forward(null)
        assertThat(range(), equalTo("Rom.6.1"))
        provider.forward(null)
        assertThat(range(), equalTo("Rom.7.1"))
    }
}