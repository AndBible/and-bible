package net.bible.service.device.speak

import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.control.speak.save
import net.bible.android.platform.AndroidAppEnvironment
import net.bible.android.platform.AndroidTextDisplayPrefItems
import net.bible.android.view.activity.page.AndroidPaneMenuStrings
import net.bible.android.view.activity.page.WindowPaneMenuDispatcher
import net.bible.android.view.activity.page.WindowPaneMenuPlatform
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.android.view.activity.page.androidWindowPaneMenuStateBuilder
import net.bible.service.common.CommonUtils
import net.bible.service.common.shortName
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.service.sword.epub.EpubBackend
import net.bible.service.sword.epub.EpubBackendState
import net.bible.service.sword.epub.EpubBackendStateTest
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.speak.SpeakLanguageResult
import net.bible.test.testCoreStrings
import okio.Path.Companion.toOkioPath
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.RangedPassage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.util.Locale

/** The headset action must use audible progress, not the independently scrolled reading window. */
@RunWith(RobolectricTestRunner::class)
class SpeakWindowMenuTest : SpeakIntegrationTestBase() {
    private lateinit var engine: FakeSpeakEngine
    private lateinit var platform: FakeSpeakPlatform
    private var epubFixture: EpubBackendStateTest? = null
    private var epub: SwordGenBook? = null
    private var epubState: EpubBackendState? = null

    override fun configureSpeakPlatform() {
        engine = FakeSpeakEngine(mapOf(Locale.ENGLISH to SpeakLanguageResult.AVAILABLE,
            Locale("fi") to SpeakLanguageResult.AVAILABLE))
        platform = FakeSpeakPlatform(engine, autoInit = true)
        loadKoinModules(module { single<net.bible.android.control.speak.SpeakPlatform> { platform } })
    }

    @Before
    fun disableFollow() {
        GlobalContext.get().get<AdvancedSpeakSettings>().apply {
            synchronize = false
            autoBookmark = false
            restoreSettingsFromBookmarks = false
        }
        SpeakSettings().save()
    }

    @After
    fun stopSpeechAndRemoveEpub() {
        if (!speakControl.isStopped) speakControl.stop(force = true)
        joinSpeech()
        epub?.let {
            SwordContentFacade.evictBook(it.initials)
            Books.installed().removeBook(it)
            it.deactivate(null)
        }
        try {
            epubState?.close()
        } finally {
            epubFixture?.tearDown()
        }
    }

    private fun joinSpeech() = runBlocking { GlobalContext.get().get<OrderedLauncher>().join(SPEAK_KEY) }

    private fun startUtterance(id: String) {
        platform.listener!!.onUtteranceStart(id)
        joinSpeech()
        ShadowLooper.idleMainLooper()
    }

    private fun speakItem() = androidWindowPaneMenuStateBuilder(windowControl, speakControl)
        .build(windowControl.activeWindow).single { it.id == WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK }

    private fun jumpToSpeech() {
        val repository = windowControl.windowRepository
        val dispatcher = WindowPaneMenuDispatcher(
            windowControl, speakControl, { repository }, { null }, AndroidTextDisplayPrefItems(),
            { CommonUtils.lastDisplaySettingsSorted }, { CommonUtils.globalTextDisplaySettings },
            object : WindowPaneMenuPlatform {},
        )
        assertFalse(dispatcher.handle(repository.activeWindow.id.toString(), WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK))
    }

    private fun playBibleAndScrollAway(): org.crosswire.jsword.passage.Key {
        val page = windowControl.activeWindow.pageManager
        val verse = (book.getKey("Gen.1.1") as RangedPassage).getVerseAt(0)
        page.setCurrentDocumentAndKey(book, verse)
        speakControl.speakBible(book, verse)
        joinSpeech()
        assertTrue(speakControl.isSpeaking)
        // Capture the provider's progress boundary independently of SpeakControl's menu projection.
        val progress = mutableListOf<SpeakChange.Progress>()
        val subscription = SpeakChanges.changes.subscribe { if (it is SpeakChange.Progress) progress += it }
        try {
            for (id in engine.utteranceIds.toList().dropLast(1)) {
                startUtterance(id)
                val key = progress.lastOrNull()?.key ?: continue
                if (org.crosswire.jsword.passage.KeyUtil.getVerse(key).getOsisRef() != "Gen.1.1") break
            }
        } finally {
            subscription.cancel()
        }
        val audible = progress.last().key
        assertTrue(org.crosswire.jsword.passage.KeyUtil.getVerse(audible).getOsisRef() != "Gen.1.1")
        assertEquals("Follow is disabled", "Gen.1.1", page.currentBible.singleKey.getOsisRef())
        page.setCurrentDocumentAndKey(book, book.getKey("Exod.2.1"))
        return audible
    }

    private fun playEpubAndScrollAway(): BookAndKey = runBlocking {
        val fixture = EpubBackendStateTest().also { epubFixture = it; it.setUp() }
        val directory = fixture.buildEpub("speak-menu")
        java.io.File(directory, "OEBPS/ch2.xhtml").writeText(
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter Two</title></head><body>" +
                (1..30).joinToString("") { "<p>Paragraph $it has readable speech content.</p>" } + "</body></html>",
        )
        val state = EpubBackendState(directory.toOkioPath(),
            testCoreStrings(), AndroidAppEnvironment(app)).also { epubState = it }
        val metadata = state.getBookMetaData()
        val document = SwordGenBook(metadata, EpubBackend(state, metadata)).also {
            epub = it
            Books.installed().addBook(it)
        }
        val key = state.keys.last()
        val range = state.getOrdinalRange(key)
        val ordinal = range.first + 2
        assertTrue("Speech starts inside the chapter, not at its first paragraph", ordinal <= range.last)
        val target = BookAndKey(key, document, OrdinalRange(ordinal))
        val page = windowControl.activeWindow.pageManager
        page.setCurrentDocumentAndKey(document, target)
        speakControl.speakGeneric(target)
        joinSpeech()
        assertTrue(speakControl.isSpeaking)
        startUtterance(engine.utteranceIds.first())
        page.setCurrentDocumentAndKey(document, BookAndKey(state.keys.first(), document, OrdinalRange(0)))
        target
    }

    @Test
    fun bibleMenuLabelTracksAudibleVerseAfterScrollingAway() {
        val audible = playBibleAndScrollAway()
        assertEquals(AndroidPaneMenuStrings().goToRef(audible.shortName), speakItem().label)
    }

    @Test
    fun bibleMenuJumpsToAudibleVerseAfterScrollingAway() {
        val audible = org.crosswire.jsword.passage.KeyUtil.getVerse(playBibleAndScrollAway()).getOsisRef()
        jumpToSpeech()
        assertEquals(audible, windowControl.activeWindow.pageManager.currentBible.singleKey.getOsisRef())
    }

    @Test
    fun epubMenuLabelTracksAudibleChapterAfterScrollingAway() {
        val target = playEpubAndScrollAway()
        assertEquals(AndroidPaneMenuStrings().goToRef(target.shortName), speakItem().label)
    }

    @Test
    fun epubMenuJumpRetainsTheAudibleParagraphOrdinal() {
        val target = playEpubAndScrollAway()
        jumpToSpeech()
        val page = windowControl.activeWindow.pageManager.currentPage
        assertEquals(target.document, page.currentDocument)
        assertEquals(target.key.getOsisRef(), page.key!!.getOsisRef())
        val ordinal = target.ordinal!!.start
        assertEquals(ordinal, page.anchorOrdinal?.start)
        assertEquals(ordinal, page.anchorOrdinal?.end)
    }
}
