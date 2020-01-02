package net.bible.service.device.speak

import kotlinx.android.synthetic.main.speak_bible.*
import kotlinx.android.synthetic.main.speak_settings.*
import net.bible.android.BibleApplication
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.PlaybackSettings
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.speak.BibleSpeakActivity
import net.bible.android.view.activity.speak.SpeakSettingsActivity
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
import org.junit.Ignore
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLog


@Config(qualifiers="fi", application = TestBibleApplication::class, sdk=[28])
open class SpeakIntegrationTestBase {
    lateinit var app: TestBibleApplication
    lateinit var bookmarkControl: BookmarkControl
    lateinit var speakControl: SpeakControl
    lateinit var book: SwordBook
    lateinit var windowControl: WindowControl

    lateinit var bibleSpeakActivityController: ActivityController<BibleSpeakActivity>
    lateinit var bibleSpeakSettingsActivityController: ActivityController<SpeakSettingsActivity>

    @Before
    fun setUp() {
        ShadowLog.stream = System.out
        app = BibleApplication.application as TestBibleApplication
        val appComponent = app.applicationComponent
        bookmarkControl = appComponent.bookmarkControl()
        speakControl = appComponent.speakControl()
        windowControl = appComponent.windowControl()
        windowControl.windowRepository.initialize()
        speakControl.setupMockedTts()
        book = Books.installed().getBook("FinRK") as SwordBook
        bibleSpeakActivityController = Robolectric.buildActivity(BibleSpeakActivity::class.java)
        bibleSpeakSettingsActivityController = Robolectric.buildActivity(SpeakSettingsActivity::class.java)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }
}

@RunWith(RobolectricTestRunner::class)
class SpeakActivityTests : SpeakIntegrationTestBase() {
    @Test
    fun testSpeaActivityIsUpdatedWhenSettingsAreChanged() {
        var s = SpeakSettings(synchronize = true)
        s.save()
        val settingsActivity = bibleSpeakSettingsActivityController.create().visible().get()
        assertThat(settingsActivity.synchronize.isChecked, equalTo(true))
        s = SpeakSettings(synchronize = false)
        s.save()
        assertThat(settingsActivity.synchronize.isChecked, equalTo(false))
    }

    @Test
    fun testSpeaActivityUpdatesSettings() {
        var s = SpeakSettings(synchronize = true)
        s.save()
        val settingsActivity = bibleSpeakSettingsActivityController.create().visible().get()
        assertThat(settingsActivity.synchronize.isChecked, equalTo(true))
        settingsActivity.synchronize.performClick()

        assertThat(settingsActivity.synchronize.isChecked, equalTo(false))
        s = SpeakSettings.load()
        assertThat(s.synchronize, equalTo(false))
    }
}

@RunWith(RobolectricTestRunner::class)
class SpeakIntegrationTests : SpeakIntegrationTestBase() {
    lateinit var mainActivityController: ActivityController<MainBibleActivity>


    @Before
    fun setup() {
        mainActivityController = Robolectric.buildActivity(MainBibleActivity::class.java)
        bookmarkControl.orCreateSpeakLabel
        val s = SpeakSettings(autoBookmark = true, restoreSettingsFromBookmarks = true)
        s.save()

        bibleSpeakActivityController.create()
        mainActivityController.create()
    }

    fun getVerse(verseStr: String): Verse {
        val verse = book.getKey(verseStr) as RangedPassage
        return verse.getVerseAt(0)
    }

    @Test
    fun testSleeptimer() {
        speakControl.speakBible(book, getVerse("Rom.1.1"))
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.1")), nullValue())
        assertThat(speakControl.sleepTimerActive(), equalTo(false))
        setSleepTimer(5)
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.1")), notNullValue())
        assertThat(speakControl.sleepTimerActive(), equalTo(true))
        setSleepTimer(0)
        assertThat(speakControl.sleepTimerActive(), equalTo(false))
    }

    fun changeSpeed(speed: Int) {
        val settingsActivity = bibleSpeakActivityController.visible().get()
        settingsActivity.speakSpeed.setProgress(speed)
        settingsActivity.updateSettings()
    }

    fun setSleepTimer(time: Int) {
        val s = SpeakSettings.load()
        s.sleepTimer = time
        s.save()
    }

    @Test
    fun testAutobookmark() {
        speakControl.speakBible(book, getVerse("Rom.1.1"))
        speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE) // to Rom.1.2
        speakControl.pause()
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.1")), nullValue())
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.2")), notNullValue())

        speakControl.continueAfterPause()
        speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE) // to Rom.1.3
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.2")), notNullValue())
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.3")), nullValue())

        // Check that altering playback settigns are saved also to bookmark (bookmark is also moved when saving)
        changeSpeed(201)
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.2")), nullValue())
        var b = bookmarkControl.getBookmarkByKey((getVerse("Rom.1.3")))
        assertThat(b!!.playbackSettings!!.speed, equalTo(201))

        // Test that bookmark is moved properly when paused / stopped
        speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE) // to Rom.1.4
        speakControl.pause()
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.3")), nullValue())
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.4")), notNullValue())

        // Check that altering playback settigns are saved to bookmark when paused
        changeSpeed(202)
        b = bookmarkControl.getBookmarkByKey((getVerse("Rom.1.4")))
        assertThat(b!!.playbackSettings!!.speed, equalTo(202))


        // Check that altering playback settigns are saved to bookmark when paused and we have moved away
        windowControl.windowRepository.firstVisibleWindow.pageManager.setCurrentDocumentAndKey(book, getVerse("Rom.2.1"))

        changeSpeed(206)
        b = bookmarkControl.getBookmarkByKey((getVerse("Rom.1.4")))
        assertThat(b!!.playbackSettings!!.speed, equalTo(206))


        // continue...
        speakControl.continueAfterPause()
        speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE) // to Rom.1.5
        speakControl.stop()
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.4")), nullValue())
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Rom.1.5")), notNullValue())

        // Check that altering playback settigns are saved to bookmark when stopped
        changeSpeed(203)
        b = bookmarkControl.getBookmarkByKey((getVerse("Rom.1.5")))
        assertThat(b!!.playbackSettings!!.speed, equalTo(203))

        // Check that altering playback settigns are not saved to bookmark when stopped and we have moved away
        windowControl.windowRepository.firstVisibleWindow.pageManager.setCurrentDocumentAndKey(book, getVerse("Rom.2.1"))

        changeSpeed(204)
        b = bookmarkControl.getBookmarkByKey((getVerse("Rom.1.5")))
        assertThat(b!!.playbackSettings!!.speed, equalTo(203))
    }
}


@Config(qualifiers = "fi", application = TestBibleApplication::class, sdk = [28])
open class AbstractSpeakTests {
    lateinit var provider: BibleSpeakTextProvider
    internal var text: String = ""
    lateinit var book: SwordBook

    @Before
    open fun setup() {
        ShadowLog.stream = System.out
        book = Books.installed().getBook("FinRK") as SwordBook
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
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
        var idCount = 0;
        val swordContentFacade = SwordContentFacade(BookmarkFormatSupport(), MyNoteFormatSupport())
        val documentBibleBooksFactory = DocumentBibleBooksFactory()
        val windowControl = mock(WindowControl::class.java)
        val windowRepository = mock(WindowRepository::class.java)
        val bibleTraverser = BibleTraverser(documentBibleBooksFactory)
        val bookmarkControl = BookmarkControl(swordContentFacade, windowControl, mock(AndroidResourceProvider::class.java))
    }
}

@RunWith(RobolectricTestRunner::class)
open class OsisToBibleSpeakTests : AbstractSpeakTests() {
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
        assertThat(cmds.size, equalTo(4))
    }

    @Test
    fun testFootnoteFinRK() {
        val cmds = SpeakCommandArray()
        val s = SpeakSettings(playbackSettings = PlaybackSettings(speakTitles = true, speakFootnotes = true))

        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Gen.1.1")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[4] is TextCommand)
        assertThat("Command is of correct type", cmds[5] is SilenceCommand)
        assertThat("Command is of correct type", cmds[6] is TextCommand)
        assertThat("Command is of correct type", cmds[7] is PreFootnoteCommand)
        assertThat("Command is of correct type", cmds[8] is TextCommand)
        assertThat("Command is of correct type", cmds[9] is PostFootnoteCommand)
        assertThat("Command is of correct type", cmds[10] is TextCommand)
        assertThat(cmds.size, equalTo(11))
    }

    @Ignore("Until ESV comes back")
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
        assertThat(cmds.size, equalTo(4))
    }

    @Test
    fun testTitle2STLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Jer.11.1")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat((cmds[1] as TextCommand).type, equalTo(TextCommand.TextType.TITLE))
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
        assertThat(cmds.size, equalTo(4))
    }

    @Test
    fun testTitleSTLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.1")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.2")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
        assertThat(cmds.size, equalTo(4))
    }

    @Test
    fun testParagraphChangeRK() {
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.23")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.24")))
        assertThat("Command is of correct type", cmds[0] is TextCommand)
        assertThat("Command is of correct type", cmds[1] is ParagraphChangeCommand)
        assertThat("Command is of correct type", cmds[2] is TextCommand)
        assertThat(cmds.size, equalTo(3))
    }

    @Ignore("Until ESV comes back")
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
        assertThat(cmds.size, equalTo(3))
    }

    @Test
    fun testParagraphChangeSTLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.25")))
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Rom.1.26")))
        assertThat("Command is of correct type", cmds[0] is TextCommand)
        assertThat("Command is of correct type", cmds[1] is ParagraphChangeCommand)
        assertThat("Command is of correct type", cmds[2] is TextCommand)
        assertThat(cmds.size, equalTo(3))
    }

    @Test
    fun testQuotationMarkAnomalySTLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl, windowRepository, book, getVerse("Ps.14.1"))
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
    fun testDivinenameInTitle() {
        val s = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true), replaceDivineName = true)
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        val cmds = SpeakCommandArray()
        cmds.addAll(swordContentFacade.getSpeakCommands(s, book, getVerse("Exod.19.1")))
        assertThat("Command is of correct type", cmds[0] is PreTitleCommand)
        assertThat("Command is of correct type", cmds[1] is TextCommand)
        assertThat((cmds[1] as TextCommand).text, equalTo("Saapuminen Siinaille. Jahve ilmestyy"))
        assertThat("Command is of correct type", cmds[2] is SilenceCommand)
        assertThat("Command is of correct type", cmds[3] is TextCommand)
    }

    @Test
    fun testDivinenameInText() {
        val s = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = true), replaceDivineName = true)
        book = Books.installed().getBook("FinSTLK2017") as SwordBook

        val cmds = swordContentFacade.getSpeakCommands(s, book, getVerse("Exod.19.3"))
        assertThat((cmds[0] as TextCommand).text, containsString("ja Jahve huusi"))
    }

}

@RunWith(RobolectricTestRunner::class)
class TestPersistence : AbstractSpeakTests() {
    @Before
    override fun setup() {
        super.setup()
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                windowRepository, book, getVerse("Ps.14.1"))
        provider.settings = SpeakSettings(synchronize = false, playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = false))
    }

    @Test
    fun storePersistence() {
        provider.setupReading(book, getVerse("Ps.14.1"))
        val sharedPreferences = CommonUtils.sharedPreferences
        provider.persistState()
        assertThat(sharedPreferences.getString("SpeakBibleVerse", ""), equalTo("Ps.14.1"))
        assertThat(sharedPreferences.getString("SpeakBibleBook", ""), equalTo("FinRK"))
    }

    @Test
    fun readPersistence() {
        val sharedPreferences = CommonUtils.sharedPreferences
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
class AutoBookmarkTests : AbstractSpeakTests() {
    @Before
    override fun setup() {
        super.setup()
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                windowRepository, book, getVerse("Ps.14.1"))
        bookmarkControl.orCreateSpeakLabel
        provider.settings = SpeakSettings(autoBookmark = true)
    }

    @After
    fun resetDatabase() {
        System.out.println("Database reset (1)!");
        //DatabaseResetter.resetDatabase()
    }

    @Test
    fun autoBookmarkDisabled() {
        provider.settings = SpeakSettings(autoBookmark = false)
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
        var labelDto = LabelDto()
        labelDto.name = "Another"
        labelDto = bookmarkControl.saveOrUpdateLabel(labelDto)
        bookmarkControl.setBookmarkLabels(dto, listOf(labelDto))

        provider.setupReading(book, verse)
        text = nextText()
        provider.pause();
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.pause()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.prepareForStartSpeaking()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        provider.stop(false)
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
    }

    @Test
    fun autoBookmarkWhenThereIsDefaultBookmark1() {
        var dto = BookmarkDto()
        val verse = getVerse("Ps.14.1")
        dto.verseRange = VerseRange(verse.versification, verse)
        dto = bookmarkControl.addOrUpdateBookmark(dto)

        assertThat(bookmarkControl.getBookmarkByKey(verse)!!, notNullValue())
        provider.setupReading(book, verse)
        text = nextText()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(0))
        provider.pause();
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.pause()
        provider.prepareForStartSpeaking()
        provider.pause()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.pause() // does not remove bookmark as it was already there
        provider.prepareForStartSpeaking()


        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.prepareForStartSpeaking()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        provider.stop(false) // does not remove bookmark as it was already there
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(0))
        assertThat(bookmarkControl.getBookmarkByKey(verse)!!, notNullValue())
    }


    @Test
    fun autoBookmarkWhenThereIsDefaultBookmark2() {
        val verse = getVerse("Ps.14.1")
        var dto = BookmarkDto()
        dto.verseRange = VerseRange(verse.versification, verse)
        dto = bookmarkControl.addOrUpdateBookmark(dto)

        assertThat(bookmarkControl.getBookmarkByKey(verse), notNullValue())

        provider.setupReading(book, verse)
        text = nextText()
        assertThat(bookmarkControl.getBookmarkByKey(verse), notNullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(0))
        provider.pause();
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(dto, notNullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.pause()
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        assertThat(range(), equalTo("Ps.14.1"))
        provider.prepareForStartSpeaking()

        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.pause()
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        assertThat(range(), equalTo("Ps.14.1"))
        provider.prepareForStartSpeaking()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))


        text = nextText()
        text = nextText()
        text = nextText()
        assertThat(range(), equalTo("Ps.14.2"))


        provider.pause()
        assertThat(range(), equalTo("Ps.14.2"))
        assertThat(bookmarkControl.getBookmarkByKey(verse), notNullValue())
        assertThat(bookmarkControl.getBookmarkLabels( bookmarkControl.getBookmarkByKey(verse)).size, equalTo(0))
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Ps.14.2")), notNullValue())
    }


    @Test
    fun autoBookmarkWhenThereIsAnotherSpeakBookmark() {
        var dto = BookmarkDto()
        val speakLabel = bookmarkControl.orCreateSpeakLabel

        dto.verseRange = VerseRange(book.versification, getVerse("Ps.14.2"))
        dto.playbackSettings = PlaybackSettings(bookmarkWasCreated = true)
        dto = bookmarkControl.addOrUpdateBookmark(dto)
        bookmarkControl.setBookmarkLabels(dto, mutableListOf(speakLabel))

        var verse = getVerse("Ps.14.1")

        assertThat(bookmarkControl.getBookmarkByKey(verse), nullValue())

        provider.setupReading(book, verse)
        text = nextText()
        provider.pause();

        assertThat(bookmarkControl.getBookmarkByKey(verse)!!, notNullValue())
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))

        verse = getVerse("Ps.14.2")
        provider.prepareForStartSpeaking()
        text = nextText()
        text = nextText()
        text = nextText()
        assertThat(range(), equalTo("Ps.14.2"))

        // now we save speak bookmark above speak bookmark
        provider.pause()
        assertThat(bookmarkControl.getBookmarkByKey(verse)!!, notNullValue())
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(dto.playbackSettings!!.bookmarkWasCreated, equalTo(true))
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))

        provider.prepareForStartSpeaking()
        text = nextText()
        text = nextText()
        assertThat(range(), equalTo("Ps.14.3"))

        provider.pause()

        verse = getVerse("Ps.14.3")
        assertThat(bookmarkControl.getBookmarkByKey(verse)!!, notNullValue())
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))

        // now there should not be any more original speak bookmark
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Ps.14.2")), nullValue())
    }


    @Test
    fun autoBookmarkWhenThereIsNoBookmark() {
        val verse = getVerse("Ps.14.1")
        assertThat(bookmarkControl.getBookmarkByKey(verse), nullValue())

        provider.setupReading(book, verse)
        text = nextText()
        assertThat(bookmarkControl.getBookmarkByKey(verse), nullValue())
        provider.pause();
        var dto = bookmarkControl.getBookmarkByKey(verse)
        assertThat(dto, notNullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.pause()
        dto = bookmarkControl.getBookmarkByKey(verse)
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        assertThat(range(), equalTo("Ps.14.1"))
        provider.prepareForStartSpeaking()

        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        provider.pause()
        dto = bookmarkControl.getBookmarkByKey(verse)
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
        assertThat(range(), equalTo("Ps.14.1"))
        provider.prepareForStartSpeaking()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))


        text = nextText()
        text = nextText()
        text = nextText()
        assertThat(range(), equalTo("Ps.14.2"))


        provider.pause()
        assertThat(range(), equalTo("Ps.14.2"))
        assertThat(bookmarkControl.getBookmarkByKey(verse), nullValue())
        assertThat(bookmarkControl.getBookmarkByKey(getVerse("Ps.14.2")), notNullValue())
    }

    @Test
    fun autoBookmarkOnPauseAddLabelAndSettings() {
        provider.settings = SpeakSettings(restoreSettingsFromBookmarks = true, autoBookmark = true)
        var dto = BookmarkDto()
        val verse = getVerse("Ps.14.1")
        dto.verseRange = VerseRange(verse.versification, verse)
        dto = bookmarkControl.addOrUpdateBookmark(dto)
        var labelDto = LabelDto()
        labelDto.name = "Another"
        labelDto = bookmarkControl.saveOrUpdateLabel(labelDto)
        bookmarkControl.setBookmarkLabels(dto, listOf(labelDto))

        provider.setupReading(book, verse)
        text = nextText()
        provider.pause();
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(dto.playbackSettings, notNullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.pause()
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(2))
        provider.prepareForStartSpeaking()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        provider.stop(false)
        dto = bookmarkControl.getBookmarkByKey(verse)!!
        assertThat(dto.playbackSettings, nullValue())
        assertThat(bookmarkControl.getBookmarkLabels(dto).size, equalTo(1))
    }

    @Test
    fun autoBookmarkOnPauseCreateNewSaveSettings() {
        provider.settings = SpeakSettings(restoreSettingsFromBookmarks = true, autoBookmark = true)
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = nextText()
        provider.pause();
        val labelDto = bookmarkControl.orCreateSpeakLabel
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        assertThat(bookmark.playbackSettings, notNullValue())
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.1"))

        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        // test that it does not add another bookmark if there's already one with same key
        provider.pause();
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.prepareForStartSpeaking()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        text = nextText()
        provider.stop(false)
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1)) // new bookmark with same label has been created
    }

    @Test
    fun autoBookmarkOnPauseCreateNew() {
        provider.setupReading(book, getVerse("Ps.14.1"))
        text = nextText()
        provider.pause();
        val labelDto = bookmarkControl.orCreateSpeakLabel
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        //assertThat(bookmark.playbackSettings, notNullValue())
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.1"))

        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        // test that it does not add another bookmark if there's already one with same key
        provider.pause();
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.prepareForStartSpeaking()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))

        // Test that if stopping when paused, bookmark is not created.
        provider.pause()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        val bmark = bookmarkControl.getBookmarksWithLabel(labelDto).first()
        bookmarkControl.deleteBookmark(bmark);
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(0))
        provider.stop(false)
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(0))
    }

    @Test
    fun autoBookmarkOnStop() {
        provider.setupReading(book, getVerse("Ps.14.2"))
        provider.prepareForStartSpeaking()
        text = nextText()
        provider.stop(false);
        val labelDto = bookmarkControl.orCreateSpeakLabel
        val bookmark = bookmarkControl.getBookmarksWithLabel(labelDto).get(0)
        assertThat(bookmark.verseRange.start.osisID, equalTo("Ps.14.2"))
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
        provider.setupReading(book, getVerse("Ps.14.2"))
        provider.prepareForStartSpeaking()
        assertThat(bookmarkControl.getBookmarksWithLabel(labelDto).size, equalTo(1))
    }
}


@RunWith(RobolectricTestRunner::class)
class SpeakWithContinueSentences : AbstractSpeakTests() {
    @Before
    override fun setup() {
        super.setup()
        provider = BibleSpeakTextProvider(swordContentFacade, bibleTraverser, bookmarkControl,
                windowRepository, book, getVerse("Ps.14.1"))
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
    fun textProgression2STLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        provider.setupReading(book, getVerse("Ezra.4.8"))

        val text1 = nextText()
        val range1 = range()
        val text2 = nextText()
        val range2 = range()
        assertThat(text1, startsWith("Käskynhaltija"))
        assertThat(text1, endsWith("Silloin ja silloin."))
        assertThat(range1, equalTo("Ezra.4.8-Ezra.4.9"))
        assertThat(text2, startsWith("\"Käskynhaltija"))
        assertThat(range2, equalTo("Ezra.4.9-Ezra.4.10"))
    }

    @Test
    fun textProgression3STLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.setupReading(book, getVerse("Ezek.34.27"))

        val text1 = nextText()
        val range1 = range()
        assertThat(text1, startsWith("Kedon puut kantavat"))
        assertThat(text1, endsWith("orjuuttajiensa käsistä."))
        assertThat(text1, containsString("minä olen Jahve, kun särjen"))
        assertThat(range1, equalTo("Ezek.34.27"))
    }

    @Test
    fun textProgression4STLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.setupReading(book, getVerse("Ezek.35.1"))
        nextText() // title
        val text1 = nextText()
        assertThat(text1, startsWith("Minulle tuli tämä Jahven sana, ja se kuului: \"Ihmislapsi, käännä"))
        assertThat(text1, endsWith("autioksi ja hävitetyksi."))
    }

    @Test
    fun textProgression5STLK() {
        book = Books.installed().getBook("FinSTLK2017") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.setupReading(book, getVerse("Ezek.35.4"))
        val text1 = nextText()
        assertThat(text1, endsWith("Tulet tietämään, että minä olen Jahve."))
    }

    @Ignore("Until ESV comes back")
    @Config(qualifiers="en")
    @Test
    fun textProgressionESV() {
        book = Books.installed().getBook("ESV2011") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.setupReading(book, getVerse("Ezek.34.27"))

        val text1 = nextText()
        val range1 = range()
        assertThat(text1, startsWith("And the trees of the field shall yield their fruit, and"))
        assertThat(text1, endsWith("who enslaved them."))
        assertThat(text1, containsString("I am the Yahweh, when I break"))
        assertThat(range1, equalTo("Ezek.34.27"))
    }

    @Ignore("Until ESV comes back")
    @Config(qualifiers="en")
    @Test
    fun textProgression2ESV() {
        book = Books.installed().getBook("ESV2011") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.setupReading(book, getVerse("Ezek.36.2"))

        val text1 = nextText()
        assertThat(text1, startsWith("Thus says the Lord Yahweh: Because the enemy said of you, Aha! and, The ancient heights have become our possession,"))
    }

    @Ignore("Until ESV comes back")
    @Config(qualifiers="en")
    @Test
    fun textProgression3ESV() {
        book = Books.installed().getBook("ESV2011") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.setupReading(book, getVerse("Ezek.36.16"))

        val text1 = nextText()// Title
        assertThat(text1, startsWith("The Yahweh's Concern for His Holy Name"))
    }

    @Ignore("Until ESV comes back")
    @Config(qualifiers="en")
    @Test
    fun textProgressionAndRepeatPassageESV() {
        // related to issue #314
        book = Books.installed().getBook("ESV2011") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true)
        provider.settings.playbackSettings = PlaybackSettings(verseRange = VerseRange(book.versification, getVerse("Rev.1.2"), getVerse("Rev.1.5")))
        provider.setupReading(book, getVerse("Rev.1.5"))

        var text = nextText()

        assertThat(range(), equalTo("Rev.1.5"))
        assertThat(text, startsWith("and from Jesus"))
        assertThat(text, endsWith("earth."))

        text = nextText()

        assertThat(range(), equalTo("Rev.1.5"))
        assertThat(text, startsWith("To him who"))
        assertThat(text, endsWith("his blood"))

        text = nextText()

        assertThat(text, equalTo("Revelation of John Chapter 1."))
        assertThat(range(), equalTo("Rev.1.2"))

        text = nextText()

        assertThat(range(), equalTo("Rev.1.2"))

        assertThat(text, startsWith("who bore"))
        assertThat(text, endsWith("he saw."))


    }

    @Test
    fun textProgressionFinPR() {
        book = Books.installed().getBook("FinPR") as SwordBook
        provider.settings = SpeakSettings(replaceDivineName = true, playbackSettings = PlaybackSettings(speakChapterChanges = false))
        provider.setupReading(book, getVerse("Ezek.36.38"))

        val text1 = nextText()
        assertThat(text1, endsWith("että minä olen Herra.\""))
    }

    @Test
    @Config(qualifiers = "en")
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
