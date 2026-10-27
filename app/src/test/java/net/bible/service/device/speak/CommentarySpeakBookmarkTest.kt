package net.bible.service.device.speak

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.OsisDocument
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.service.common.next
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.testCoreStrings
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner

/** Speech advances to a single verse, but commentary markers belong to the entry's annotateRef range. */
@RunWith(RobolectricTestRunner::class)
class CommentarySpeakBookmarkTest : SpeakIntegrationTestBase() {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private lateinit var commentary: SwordBook
    private val v11n = Versifications.instance().getVersification("KJV")!!

    @Before
    fun registerCommentary() {
        val root = temporaryFolder.newFolder("speak-commentary")
        val data = File(root, "modules/commentary").apply { mkdirs() }
        val entries = listOf(
            "<div annotateRef=\"John.1.1-John.1.2\"><p>First entry. More text.</p></div>",
            "<div annotateRef=\"John.1.3-John.1.4\"><p>Second entry. More text.</p></div>",
        ).map { it.toByteArray() }
        val index = ByteBuffer.allocate((v11n.getTestamentOrdinal(verse(4).ordinal) + 1) * 6)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (number in 1..4) {
            index.position(v11n.getTestamentOrdinal(verse(number).ordinal) * 6)
            val entry = (number - 1) / 2
            index.putInt(if (entry == 0) 0 else entries[0].size).putShort(entries[entry].size.toShort())
        }
        File(data, "nt").writeBytes(entries[0] + entries[1])
        File(data, "nt.vss").writeBytes(index.array())
        File(data, "ot").writeBytes(byteArrayOf())
        File(data, "ot.vss").writeBytes(byteArrayOf())
        val conf = File(root, "mods.d/SpeakCommentary.conf").apply {
            parentFile!!.mkdirs()
            writeText("""
                [SpeakCommentary]
                DataPath=./modules/commentary/
                ModDrv=RawCom
                SourceType=OSIS
                Encoding=UTF-8
                Lang=en
                Description=Synthetic speech commentary
            """.trimIndent())
        }
        commentary = BookType.fromString("RawCom")!!.createBook(
            SwordBookMetaData(conf.absolutePath.toPath(), KUri(root.toURI().toString()))
        ) as SwordBook
        Books.installed().addBook(commentary)
        SwordContentFacade.clearCaches()
        GlobalContext.get().get<AdvancedSpeakSettings>().autoBookmark = true
    }

    @After
    fun unregisterCommentary() {
        speakControl.stop(force = true)
        runBlocking { GlobalContext.get().get<OrderedLauncher>().join(SPEAK_KEY) }
        SwordContentFacade.evictBook(commentary.initials)
        Books.installed().removeBook(commentary)
        commentary.deactivate(null)
    }

    private fun verse(number: Int) = Verse(v11n, BibleBook.JOHN, 1, number)

    @Test
    fun pauseAfterAdvancingToAnotherCommentaryEntryIncludesSpeakMarkerInDocument() = runBlocking {
        val page = windowControl.activeWindow.pageManager.currentCommentary
        page.setCurrentDocumentAndKey(commentary, verse(1))
        val initial = page.getPageContent(verse(1)) as OsisDocument
        assertEquals("John.1.1-John.1.2", initial.osisFragment.annotateRef!!.getOsisRef())
        val initialKey = BookAndKey(initial.osisFragment.annotateRef!!, commentary, OrdinalRange(0))
        val next = initialKey.next(testCoreStrings(), GlobalContext.get().get())
        assertEquals("John.1.3", next.key.getOsisRef())

        speakControl.speakGeneric(next)
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        launcher.join(SPEAK_KEY)
        speakControl.pause()
        launcher.join(SPEAK_KEY)

        val bookmark = bookmarkControl.getGenericBookmarksWithLabel(bookmarkControl.speakLabel(), addData = true).single()
        assertTrue(bookmarkControl.isSpeakBookmark(bookmark))
        val document = page.getPageContent(verse(3)) as OsisDocument
        assertEquals("John.1.3-John.1.4", document.osisFragment.annotateRef!!.getOsisRef())
        assertEquals("Paused speech must be sent to BibleView as an entry marker", listOf(bookmark.id),
            document.genericBookmarks.map { it.id })
        assertEquals("John.1.3-John.1.4", bookmark.key)
        assertTrue(document.contentMap().getValue("genericBookmarks").contains(bookmark.id.toString()))
    }

    @Test
    fun restartingFromSingleVerseFindsTheExistingEntryBookmark() = runBlocking {
        val entry = org.crosswire.jsword.passage.VerseRange(v11n, verse(3), verse(4))
        assertResumeMovesBookmark(entry)
    }

    @Test
    fun restartingFromSingleVerseFindsALegacySingleVerseBookmark() = runBlocking {
        assertResumeMovesBookmark(verse(3))
    }

    private suspend fun assertResumeMovesBookmark(savedKey: org.crosswire.jsword.passage.Key) {
        val saved = bookmarkControl.addOrUpdateGenericBookmark(
            GenericBookmarkWithNotes(savedKey, commentary, null, 0).apply {
                playbackSettings = PlaybackSettings(bookId = commentary.initials, bookmarkWasCreated = true)
            }, labels = setOf(bookmarkControl.speakLabel().id),
        )
        speakControl.speakGeneric(BookAndKey(verse(3), commentary, OrdinalRange(0)))
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        launcher.join(SPEAK_KEY)
        speakControl.pause()
        launcher.join(SPEAK_KEY)

        val bookmarks = bookmarkControl.getGenericBookmarksWithLabel(bookmarkControl.speakLabel())
        assertEquals("Resuming must move the old Speak bookmark, not leave a duplicate", 1, bookmarks.size)
        assertTrue(bookmarks.single().id != saved.id)
        assertEquals("John.1.3-John.1.4", bookmarks.single().key)
    }
}
