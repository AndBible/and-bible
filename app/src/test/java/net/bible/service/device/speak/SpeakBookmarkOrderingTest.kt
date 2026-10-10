package net.bible.service.device.speak

import kotlinx.coroutines.runBlocking
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.android.control.speak.save
import net.bible.sharedcore.platform.OrderedLauncher
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

@RunWith(RobolectricTestRunner::class)
class SpeakBookmarkOrderingTest : SpeakIntegrationTestBase() {
    @Test
    fun immediateResumeLoadsTheBookmarkWrittenByPause() {
        GlobalContext.get().get<net.bible.android.control.speak.AdvancedSpeakSettings>().apply {
            autoBookmark = true
            restoreSettingsFromBookmarks = true
        }
        net.bible.android.database.bookmarks.SpeakSettings().save()
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        speakControl.speakBible(book, verse)
        runBlocking { launcher.join(SPEAK_KEY) }
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val field = TextToSpeechServiceManager::class.java.getDeclaredField("bibleSpeakTextProvider").apply { isAccessible = true }
        val provider = field.get(manager) as BibleSpeakTextProvider
        val fifth = org.crosswire.jsword.passage.Verse(verse.versification, verse.book, 1, 5)
        provider.setupReading(book, fifth)
        provider.settings.playbackSettings = provider.settings.playbackSettings.copy(speed = 173)
        speakControl.pause()
        provider.settings.playbackSettings = provider.settings.playbackSettings.copy(speed = 100)
        speakControl.continueAfterPause()
        runBlocking { launcher.join(SPEAK_KEY) }
        assertEquals("Resume must apply the preceding pause snapshot", 173, provider.settings.playbackSettings.speed)
        assertEquals(5, provider.verseRange.start.verse)
        val bookmark = runBlocking { bookmarkControl.speakBookmarkForVerse(fifth) }
        assertNotNull("Resume must observe the preceding pause write", bookmark)
        assertEquals(5, bookmark!!.verseRange.start.verse)
    }
}
