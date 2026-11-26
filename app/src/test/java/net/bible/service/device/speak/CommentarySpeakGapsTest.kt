package net.bible.service.device.speak

import kotlinx.coroutines.runBlocking
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakSettingsChange
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

    private fun sparseCommentary(firstEntry: String = "<p>First entry.</p>", sourceType: String = "OSIS"): SwordBook {
        val root = temporaryFolder.newFolder("commentary")
        val data = File(root, "modules/sparse").apply { mkdirs() }
        val first = firstEntry.toByteArray()
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
                SourceType=$sourceType
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
    fun `commentary speech expands references without changing displayed text or ordinals`() {
        val doc = sparseCommentary("<p>See <reference osisRef='Matt.2.6'>2:6</reference> and <reference osisRef='Matt.2.9'>Matthew 2:9</reference>.</p>")
        val key = BookAndKey(verse(1), doc, OrdinalRange(0))
        val original = SwordContentFacade.getTextWithinOrdinalsAsString(doc, key.key, 0..3, testCoreStrings())
        val spoken = SpeakCommandArray(4000).apply {
            for (ordinal in 0..4) addAll(testSwordContentFacade().getGenBookSpeakCommands(
                BookAndKey(verse(1), doc, OrdinalRange(ordinal))
            ))
        }
        assertEquals("See Matthew 2:6 and Matthew 2:9.", spoken.filterIsInstance<TextCommand>().joinToString("") { it.text })
        assertEquals(original, SwordContentFacade.getTextWithinOrdinalsAsString(doc, key.key, 0..3, testCoreStrings()))
        assertEquals(listOf("See ", "2:6", " and ", "Matthew 2:9"), original)
        assertEquals("Matthew 2:6", testSwordContentFacade().getGenBookSpeakCommands(
            BookAndKey(verse(1), doc, OrdinalRange(1))
        ).filterIsInstance<TextCommand>().joinToString("") { it.text }.trim())
    }

    @Test
    fun `Bible provider replaces cached and pending speech after link settings change`() = runBlocking {
        val doc = sparseCommentary("<p>Before <reference osisRef='Matt.2.9'>link</reference>.</p><note type='study'>Footnote.</note><p>Ending.</p>")
        val provider = BibleSpeakTextProvider(
            bibleTraverser, bookmarkControl, doc, FakeSpeakPlatform(FakeSpeakEngine(emptyMap())),
            testAppSettings(), AdvancedSpeakSettings(testAppSettings()), testOrderedLauncher(), testSwordContentFacade(),
        )
        provider.settings = SpeakSettings(PlaybackSettings(speakFootnotes = true, speakTitles = false))
        provider.setupReading(doc, verse(1))
        fun change(links: Boolean) = provider.updateSettings(SpeakSettingsChange(
            provider.settings.copy(playbackSettings = provider.settings.playbackSettings.copy(speakLinks = links)),
        ))
        assertEquals("Before Matthew 2:9.", (provider.getNextSpeakCommand("before", isCurrent = true) as TextCommand).text)
        change(false)
        val after = provider.getNextSpeakCommand("after", isCurrent = true)
        assertTrue("Updated speech must restart the current verse, not retain its prefetched note: $after", after is TextCommand)
        assertEquals("Before.", (after as TextCommand).text)
        assertEquals(verse(1), provider.getCurrentlyPlayingKey())
        change(true)
        assertEquals("Before Matthew 2:9.", (provider.getNextSpeakCommand("on", isCurrent = true) as TextCommand).text)
        provider.pause()
        change(false)
        assertEquals("Before.", (provider.getNextSpeakCommand("resumed", isCurrent = true) as TextCommand).text)
    }

    @Test
    fun `General provider restarts current text when link settings change`() = runBlocking {
        val doc = sparseCommentary("<p>Before. <reference osisRef='Matt.2.9'>link</reference> After.</p>")
        val provider = GeneralSpeakTextProvider(
            bookmarkControl, doc, FakeSpeakPlatform(FakeSpeakEngine(emptyMap())), testAppSettings(),
            AdvancedSpeakSettings(testAppSettings()), testOrderedLauncher(), GlobalContext.get().get(),
            testSwordContentFacade(), testCoreStrings(), bibleTraverser,
        )
        provider.settings = SpeakSettings(PlaybackSettings(speakChapterChanges = false))
        provider.setupReading(BookAndKey(verse(1), doc, OrdinalRange(0)))
        assertEquals("Before.", (provider.getNextSpeakCommand("before", isCurrent = true) as TextCommand).text)
        provider.updateSettings(SpeakSettingsChange(provider.settings.copy(
            playbackSettings = provider.settings.playbackSettings.copy(speakLinks = false),
        )))
        assertEquals("Before.", (provider.getNextSpeakCommand("after", isCurrent = true) as TextCommand).text)
    }

    @Test
    fun `legacy provider uses updated speakLinks when reading is set up again`() {
        val doc = sparseCommentary("<p>Before [<reference osisRef='Matt.2.9'>Matthew 2:9</reference>] after.</p>")
        val provider = LegacySpeakTextProvider(testAppSettings(), testCoreStrings(), net.bible.android.platform.androidSaxTextPolicy())
        val initial = SpeakSettings(PlaybackSettings(speakLinks = true))
        provider.updateSettings(SpeakSettingsChange(initial))

        fun read() = runBlocking {
            provider.reset()
            provider.setupReading(doc, listOf(verse(1)))
            val pieces = mutableListOf<String>()
            repeat(10) { index ->
                if (!provider.isMoreTextToSpeak()) return@runBlocking pieces.joinToString("")
                pieces += (provider.getNextSpeakCommand("legacy-links-$index") as TextCommand).text
            }
            pieces.joinToString("")
        }

        val withLinks = read()
        provider.updateSettings(SpeakSettingsChange(initial.copy(playbackSettings = initial.playbackSettings.copy(speakLinks = false))))
        val withoutLinks = read()

        assertTrue(withLinks, withLinks.contains("Matthew 2 verse 9"))
        assertFalse(withoutLinks, withoutLinks.contains("Matthew 2 verse 9"))
    }

    @Test
    fun `skipping links cleans bracket pairs across ordinals and separates speech cache variants`() {
        val doc = sparseCommentary("<p>Before [see <reference osisRef='Matt.2.9'>v. 9</reference>] after.</p>")
        val facade = testSwordContentFacade()
        fun spoken(links: Boolean): String = SpeakCommandArray(4000).apply {
            for (ordinal in 0..4) addAll(facade.getGenBookSpeakCommands(
                BookAndKey(verse(1), doc, OrdinalRange(ordinal)),
                SpeakSettings(PlaybackSettings(speakLinks = links)),
            ))
        }.filterIsInstance<TextCommand>().joinToString("") { it.text }
        assertEquals("Before [see Matthew 2:9 ] after.", spoken(true))
        assertEquals("Before after.", spoken(false))
        assertEquals("Before [see Matthew 2:9 ] after.", spoken(true))
        assertEquals(listOf("Before [see ", "v. 9", "] after."),
            SwordContentFacade.getTextWithinOrdinalsAsString(doc, verse(1), 0..2, testCoreStrings()))
    }

    @Test
    fun `TSK style ThML passage links speak their target instead of numeric labels`() {
        val doc = sparseCommentary("<scripRef passage=\"Mt 2:9\">9</scripRef>", sourceType = "ThML")
        val commands = testSwordContentFacade().getGenBookSpeakCommands(BookAndKey(verse(1), doc, OrdinalRange(0)))
        assertEquals("Matthew 2:9", commands.filterIsInstance<TextCommand>().joinToString("") { it.text })
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
