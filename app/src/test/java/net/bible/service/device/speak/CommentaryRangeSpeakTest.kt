package net.bible.service.device.speak

import android.os.Looper
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import net.bible.android.control.navigation.DocumentBibleBooksFactory
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.page.window.WindowView
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChange
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.versification.BibleTraverser
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testSwordContentFacade
import okio.Path.Companion.toPath
import org.crosswire.common.util.KUri
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.BookType
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A real linked RawCom entry exercises annotateKey and paragraph ordinals together. */
@RunWith(RobolectricTestRunner::class)
class CommentaryRangeSpeakTest : SpeakIntegrationTestBase() {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var commentary: SwordBook
    private val v11n = Versifications.instance().getVersification("KJV")!!
    private fun verse(number: Int) = Verse(v11n, BibleBook.JOHN, 1, number)

    @Before
    fun registerCommentary() {
        val root = temporaryFolder.newFolder("range-commentary")
        val data = File(root, "modules/range").apply { mkdirs() }
        val raw = "<div annotateRef=\"John.1.1-John.1.3\"><p>First sentence.</p><p>Second sentence.</p></div>".toByteArray()
        val index = ByteBuffer.allocate((v11n.getTestamentOrdinal(verse(3).ordinal) + 1) * 6)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (number in 1..3) {
            index.position(v11n.getTestamentOrdinal(verse(number).ordinal) * 6)
            index.putInt(0).putShort(raw.size.toShort())
        }
        File(data, "nt").writeBytes(raw)
        File(data, "nt.vss").writeBytes(index.array())
        File(data, "ot").writeBytes(byteArrayOf())
        File(data, "ot.vss").writeBytes(byteArrayOf())
        val conf = File(root, "mods.d/RangeCommentary.conf").apply {
            parentFile.mkdirs()
            writeText("""
                [RangeCommentary]
                DataPath=./modules/range/
                ModDrv=RawCom
                SourceType=OSIS
                Encoding=UTF-8
                Lang=en
                Description=Synthetic range commentary
            """.trimIndent())
        }
        val metadata = SwordBookMetaData(conf.absolutePath.toPath(), KUri(root.toURI().toString()))
        commentary = BookType.fromString("RawCom")!!.createBook(metadata) as SwordBook
        Books.installed().addBook(commentary)
        originalSynchronize = GlobalContext.get().get<AdvancedSpeakSettings>().synchronize
        GlobalContext.get().get<AdvancedSpeakSettings>().synchronize = false
    }

    private var originalSynchronize: Boolean? = null

    @After
    fun unregisterCommentary() {
        originalSynchronize?.let { GlobalContext.get().get<AdvancedSpeakSettings>().synchronize = it }
        if (!::commentary.isInitialized) return
        windowControl.windowRepository.windowList.forEach { it.destroy(destroyView = false) }
        windowControl.retireWindowRepositoryForTest()
        Books.installed().removeBook(commentary)
        commentary.deactivate(null)
        SwordContentFacade.evictBook(commentary.initials)
    }

    private fun prepareUtterance(): Pair<GeneralSpeakTextProvider, String> = runBlocking {
        val window = windowControl.activeWindow
        window.pageManager.setCurrentDocumentAndKey(commentary, verse(2), false)
        // loadText records the displayed document, as it does in the reading screen.
        window.loadText()
        val page = window.pageManager.currentCommentary
        val document = page.getPageContent(verse(2)) as OsisDocument
        assertEquals("John.1.1-John.1.3", document.osisFragment.annotateRef!!.getOsisRef())
        val provider = GeneralSpeakTextProvider(
            bookmarkControl, commentary, fakePlatform, testAppSettings(), GlobalContext.get().get(),
            GlobalContext.get().get(), GlobalContext.get().get(), testSwordContentFacade(),
            testCoreStrings(), BibleTraverser(DocumentBibleBooksFactory()),
        )
        provider.setupReading(BookAndKey(page.displayKey!!, commentary, OrdinalRange(1, 1)))
        repeat(20) { index ->
            val id = "commentary-$index"
            val command = provider.getNextSpeakCommand(id)
            if (command is TextCommand && command.text.contains("Second sentence.")) {
                return@runBlocking provider to id
            }
        }
        error("The real commentary paragraph was not queued")
    }

    @Test
    fun selectedCommentaryRangeDoesNotMoveTheWindowWhenTextFollowsSpeechIsOff() {
        val (provider, id) = prepareUtterance()
        val page = windowControl.activeWindow.pageManager.currentCommentary
        assertEquals("John.1.2", page.key.getOsisRef())
        assertNull(page.anchorOrdinal)

        provider.startUtterance(id)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Speech must not navigate to the entry's first verse", "John.1.2", page.key.getOsisRef())
        assertNull("Speech must not change the window's paragraph position", page.anchorOrdinal)
    }

    @Test
    fun selectedCommentaryRangeHighlightsTheParagraphUsingTheEntryAnnotateKey() {
        val (provider, id) = prepareUtterance()
        val view = mock<WindowView>()
        windowControl.activeWindow.bibleView = view

        provider.startUtterance(id)

        verify(view).highlightOrdinalRange("RangeCommentary", "John.1.1-John.1.3", 1..1)
    }

    @Test
    fun selectedCommentaryRangeStillFollowsSpeechWhenEnabled() {
        val (provider, id) = prepareUtterance()
        GlobalContext.get().get<AdvancedSpeakSettings>().synchronize = true

        provider.startUtterance(id)
        shadowOf(Looper.getMainLooper()).idle()

        val page = windowControl.activeWindow.pageManager.currentCommentary
        assertEquals("John.1.1", page.key.getOsisRef())
        assertEquals(1, page.anchorOrdinal?.start)
        assertEquals(1, page.anchorOrdinal?.end)
    }

    @Test
    fun selectedCommentaryRangeProgressDoesNotForceFollow() {
        val (provider, id) = prepareUtterance()
        val seen = mutableListOf<SpeakChange.Progress>()
        val subscription = SpeakChanges.changes.subscribe { if (it is SpeakChange.Progress) seen += it }
        try {
            provider.startUtterance(id)
            val progress = seen.single()
            assertEquals("John.1.1-John.1.3", (progress.key as BookAndKey).key.getOsisRef())
            assertFalse("Commentary selections must respect text follows speech", progress.forceFollow)
        } finally {
            subscription.cancel()
        }
    }
}
