package net.bible.service.device.speak

import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.database.bookmarks.PlaybackSettings
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.test.testAppSettings
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BibleSpeakTextProviderPhrasesTest : AbstractSpeakTests() {
    private fun createProvider(initials: String) {
        book = Books.installed().getBook(initials) as SwordBook
        val platform = object : SpeakPlatform by FakeSpeakPlatform(FakeSpeakEngine(emptyMap())) {
            override fun phrasesFor(languageCode: String) =
                SpeakPhrases("FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH", "BOOK-CHANGED", "CHAPTER-CHANGED")
        }
        provider = BibleSpeakTextProvider(
            bibleTraverser, bookmarkControl, book, platform, testAppSettings(),
            AdvancedSpeakSettings(testAppSettings()), net.bible.test.testOrderedLauncher(),
        )
        provider.settings = SpeakSettings(playbackSettings = PlaybackSettings(speakChapterChanges = true, speakTitles = false))
    }

    private fun queuedTexts(): List<String> = (0 until 30).mapNotNull { index ->
        (provider.getNextSpeakCommand("phrases-$index") as? TextCommand)?.text
    }

    @Test
    fun bookBoundaryUsesPlatformPhrases() {
        createProvider("KJV")
        provider.setupReading(book, getVerse("Mal.4.6"))
        val texts = queuedTexts()
        assertTrue(texts.any { it.contains("BOOK-CHANGED") && it.contains("CHAPTER-CHANGED 1.") })
    }

    @Test
    fun numberedBookAnnouncementUsesPlatformOrdinal() {
        // Finnish JSword names use "1."; English names use "1" and were never replaced.
        createProvider("FinRK")
        provider.setupReading(book, getVerse("2Pet.3.18"))
        val texts = queuedTexts()
        assertTrue(texts.any { it.startsWith("BOOK-CHANGED FIRST") && it.contains("CHAPTER-CHANGED 1.") })
    }
}
