package net.bible.service.device.speak

import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.SwordContentFacade
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import net.bible.test.testOrderedLauncher
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
class CommentarySpeakGapsTest : AbstractSpeakTests() {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private var commentary: SwordBook? = null
    private val v11n = Versifications.instance().getVersification("KJV")!!
    private fun verse(number: Int) = Verse(v11n, BibleBook.OBAD, 1, number)

    private fun sparseCommentary(): SwordBook {
        val root = temporaryFolder.newFolder("commentary")
        val data = File(root, "modules/sparse").apply { mkdirs() }
        val first = "<p>First entry.</p>".toByteArray()
        val next = "<p>Next entry.</p>".toByteArray()
        val index = ByteBuffer.allocate((v11n.getTestamentOrdinal(verse(4).ordinal) + 1) * 6)
            .order(ByteOrder.LITTLE_ENDIAN)
        index.position(v11n.getTestamentOrdinal(verse(1).ordinal) * 6)
        index.putInt(0).putShort(first.size.toShort())
        // Verses 2 and 3 deliberately have zero-length index records.
        index.position(v11n.getTestamentOrdinal(verse(4).ordinal) * 6)
        index.putInt(first.size).putShort(next.size.toShort())
        File(data, "ot").writeBytes(first + next)
        File(data, "ot.vss").writeBytes(index.array())
        File(data, "nt").writeBytes(byteArrayOf())
        File(data, "nt.vss").writeBytes(byteArrayOf())
        val conf = File(root, "mods.d/SpeakGaps.conf").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("""
                [SpeakGaps]
                DataPath=./modules/sparse/
                ModDrv=RawCom
                SourceType=OSIS
                Encoding=UTF-8
                Lang=en
                Description=Synthetic sparse commentary for speech
            """.trimIndent())
        }
        val metadata = SwordBookMetaData(conf.absolutePath.toPath(), KUri(root.toURI().toString()))
        return (BookType.fromString("RawCom")!!.createBook(metadata) as SwordBook).also {
            commentary = it
            Books.installed().addBook(it)
            SwordContentFacade.clearCaches()
        }
    }

    @After
    fun removeCommentary() {
        commentary?.let {
            SwordContentFacade.evictBook(it.initials)
            Books.installed().removeBook(it)
            it.deactivate(null)
        }
    }

    private fun generalProvider(start: Int, announceReferences: Boolean): GeneralSpeakTextProvider {
        val doc = sparseCommentary()
        return GeneralSpeakTextProvider(
            bookmarkControl, doc, FakeSpeakPlatform(FakeSpeakEngine(emptyMap())), testAppSettings(),
            AdvancedSpeakSettings(testAppSettings()), testOrderedLauncher(), GlobalContext.get().get(),
            testSwordContentFacade(), testCoreStrings(), bibleTraverser,
        ).apply {
            settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = announceReferences))
            setupReading(BookAndKey(verse(start), doc, OrdinalRange(0)))
        }
    }

    private fun textsThroughNextEntry(provider: GeneralSpeakTextProvider): List<String> = runBlocking {
        val texts = mutableListOf<String>()
        repeat(20) { index ->
            val id = "commentary-$index"
            val command = provider.getNextSpeakCommand(id)
            provider.startUtterance(id)
            if (command is TextCommand) {
                texts.add(command.text)
                if (command.text.contains("Next entry.")) return@runBlocking texts
            }
        }
        throw AssertionError("Next non-empty commentary entry was not spoken: $texts")
    }

    @Test
    fun `empty commentary verses are not announced between entries`() {
        val provider = generalProvider(start = 1, announceReferences = true)
        val texts = textsThroughNextEntry(provider)
        assertEquals("First entry.", texts.first())
        assertTrue(texts.last().contains("Next entry."))
        assertTrue("The next non-empty verse is still announced: $texts", texts.any { it.contains(verse(4).getName()) })
        for (empty in 2..3) {
            assertFalse("Empty verse $empty must not be spoken: $texts", texts.any { it.contains(verse(empty).getName()) })
        }
        assertEquals("Obad.1.4", provider.getCurrentlyPlayingKey().key.getOsisRef())
    }

    @Test
    fun `trailing empty verses end commentary speech without wrapping`() = runBlocking {
        val provider = generalProvider(start = 4, announceReferences = true)
        val texts = mutableListOf<String>()
        repeat(100) { index ->
            if (!provider.isMoreTextToSpeak()) {
                assertEquals(listOf("Next entry."), texts)
                return@runBlocking
            }
            val command = provider.getNextSpeakCommand("end-$index")
            if (command is TextCommand) texts.add(command.text)
        }
        throw AssertionError("Commentary speech did not end: $texts")
    }

    @Test
    fun `starting in a commentary gap jumps to the next entry`() {
        val provider = generalProvider(start = 2, announceReferences = false)
        assertEquals(listOf("Next entry."), textsThroughNextEntry(provider))
        assertEquals("Obad.1.4", provider.getCurrentlyPlayingKey().key.getOsisRef())
    }
}
