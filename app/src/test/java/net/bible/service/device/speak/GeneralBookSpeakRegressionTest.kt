package net.bible.service.device.speak

import java.io.File
import kotlinx.coroutines.runBlocking
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.android.control.versification.BibleTraverser
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.common.next
import net.bible.service.common.prev
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testSwordContentFacade
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.GenBookBackend
import org.crosswire.jsword.book.sword.SwordBookDriver
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.crosswire.jsword.book.sword.SwordGenBook
import org.crosswire.jsword.passage.RangedPassage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner

/** Real RawGenBook keys/backends catch the casts that EPUB and MyDocument fixtures cannot. */
@RunWith(RobolectricTestRunner::class)
class GeneralBookSpeakRegressionTest : SpeakIntegrationTestBase() {
    private lateinit var genBook: SwordGenBook
    private val traverser = BibleTraverser(DocumentBibleBooksFactory())
    private val settings get() = testAppSettings()

    @Before
    fun registerGenBook() {
        val repo = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "jsword/src/commonTest/resources/k4-modules").isDirectory }
        val root = File(repo, "jsword/src/commonTest/resources/k4-modules/space dir/ä")
        val metadata = SwordBookMetaDataJvm.create(File(root, "mods.d/K4GenBook.conf"), root.toURI())
        metadata.setDriver(SwordBookDriver.instance())
        genBook = metadata.bookType!!.createBook(metadata) as SwordGenBook
        Books.installed().addBook(genBook)
    }

    @After
    fun unregisterGenBook() {
        if (!speakControl.isStopped) speakControl.stop()
        joinSpeech()
        if (Books.installed().getBook(genBook.initials) != null) Books.installed().removeBook(genBook)
        SwordContentFacade.clearCaches()
    }

    private fun key(ref: String) = genBook.getKey(ref)
    private fun start(ref: String) = runBlocking {
        val key = key(ref)
        BookAndKey(key, genBook, OrdinalRange(SwordContentFacade.ordinalRangeFor(genBook, key, testCoreStrings()).first))
    }

    private fun provider() = GeneralSpeakTextProvider(
        bookmarkControl, book, fakePlatform, settings, GlobalContext.get().get(), GlobalContext.get().get(),
        GlobalContext.get().get(), swordContentFacade = testSwordContentFacade(),
        strings = testCoreStrings(), traverser = traverser,
    )

    private fun joinSpeech() = runBlocking { GlobalContext.get().get<OrderedLauncher>().join(SPEAK_KEY) }

    @Test
    fun readingSpeakWindowWhileStoppedDoesNotLockTheNextPlayback() {
        val bibleWindow = windowControl.activeWindow
        bibleWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("Rom.1.1"))
        assertSame(bibleWindow.pageManager, speakControl.speakPageManager)
        val generalWindow = windowControl.addNewWindow(genBook, key("Part 1/Chapter 1"))
        windowControl.activeWindow = generalWindow

        speakControl.speakAny()
        joinSpeech()

        assertTrue(speakControl.isSpeaking)
        assertSame(generalWindow.pageManager, speakControl.speakPageManager)
        assertSame(genBook, GlobalContext.get().get<TextToSpeechServiceManager>().currentlyPlayingBook)
        windowControl.activeWindow = bibleWindow
        assertSame("Playback must keep following its original window", generalWindow.pageManager, speakControl.speakPageManager)
    }

    @Test
    fun biblePlaybackUsesTheBookAndVerseFromTheSameWindow() {
        val bibleWindow = windowControl.activeWindow
        bibleWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("Rom.1.1"))
        assertSame(bibleWindow.pageManager, speakControl.speakPageManager)
        val otherWindow = windowControl.addNewWindow(genBook, key("Part 2"))
        windowControl.activeWindow = bibleWindow
        speakControl.speakBible(book, (book.getKey("Rom.1.1") as RangedPassage).getVerseAt(0))
        joinSpeech()
        windowControl.activeWindow = otherWindow

        speakControl.speakAny()
        joinSpeech()

        assertTrue(speakControl.isSpeaking)
        assertSame(book, GlobalContext.get().get<TextToSpeechServiceManager>().currentlyPlayingBook)
    }

    @Test
    fun restartingPausedSpeechKeepsTheSelectedSourceWindow() {
        val bibleWindow = windowControl.activeWindow
        bibleWindow.pageManager.setCurrentDocumentAndKey(book, book.getKey("Rom.1.1"))
        speakControl.speakBible(book, (book.getKey("Rom.1.1") as RangedPassage).getVerseAt(0))
        joinSpeech()
        speakControl.pause()
        joinSpeech()
        assertTrue(speakControl.isPaused)
        val otherWindow = windowControl.addNewWindow(genBook, key("Part 2"))
        windowControl.activeWindow = otherWindow

        speakControl.speakAny()
        joinSpeech()

        assertSame(bibleWindow.pageManager, speakControl.speakPageManager)
        assertSame(book, GlobalContext.get().get<TextToSpeechServiceManager>().currentlyPlayingBook)
        assertSame(genBook, otherWindow.pageManager.currentPage.currentDocument)
    }

    @Test
    fun previousSectionUsesTheGenBookBackend() = runBlocking {
        assertTrue(genBook.backend is GenBookBackend)
        assertEquals("Part 1/Chapter 1", start("Part 1/Chapter 2").prev(testCoreStrings(), traverser).key.getOsisRef())
    }

    @Test
    fun rewindFromTheStartOfASectionReadsThePreviousSection() = runBlocking {
        val provider = provider()
        provider.setupReading(start("Part 1/Chapter 2"))
        provider.rewind(SpeakSettings.RewindAmount.ONE_VERSE) { true }
        assertEquals("Part 1/Chapter 1", provider.getCurrentlyPlayingKey().key.getOsisRef())
    }

    @Test
    fun genBookNavigationWrapsAtBothEnds() = runBlocking {
        assertEquals("Part 1/Chapter 2", start("Part 1/Chapter 1").next(testCoreStrings(), traverser).key.getOsisRef())
        assertEquals("Part 2", start("Part 1/Chapter 2").next(testCoreStrings(), traverser).key.getOsisRef())
        assertEquals("Part 2", start("").prev(testCoreStrings(), traverser).key.getOsisRef())
        assertEquals("", start("Part 2").next(testCoreStrings(), traverser).key.getOsisRef())
    }

    @Test
    fun restoringWithoutTheLegacyBookSettingUsesTheSerializedDocument() = runBlocking {
        settings.removeString("SpeakGenBook")
        settings.setString("SpeakGenKey", start("Part 1/Chapter 1").serialized)
        val provider = provider()

        assertTrue(provider.restoreState())
        assertSame(genBook, provider.getCurrentlyPlayingBook())
        assertEquals("Part 1/Chapter 1", provider.getCurrentlyPlayingKey().key.getOsisRef())
        assertTrue(provider.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL).contains(genBook.abbreviation))
    }

    @Test
    fun restoringWithAStaleLegacyBookSettingDoesNotMixABibleAndGenBookKey() = runBlocking {
        settings.setString("SpeakGenBook", book.initials)
        settings.setString("SpeakGenKey", start("Part 2").serialized)
        val provider = provider()

        assertTrue(provider.restoreState())
        // This used to reach ZVerseBackend with a TreeKey and throw "Expected key to be a Verse".
        assertTrue(provider.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL).contains(genBook.abbreviation))
        assertSame(genBook, provider.getCurrentlyPlayingBook())
    }

    @Test
    fun restoringADeletedDocumentIsRejected() {
        settings.setString("SpeakGenBook", genBook.abbreviation)
        settings.setString("SpeakGenKey", start("Part 2").serialized)
        Books.installed().removeBook(genBook)
        assertFalse(provider().restoreState())
    }
}
