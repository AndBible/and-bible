package net.bible.android.control.speak

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.service.device.speak.SpeakIntegrationTestBase
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import org.crosswire.jsword.passage.RangedPassage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SpeakTransportBookmarkVisibilityTest : SpeakIntegrationTestBase() {
    private val transportScope = AppCoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun closeTransport() {
        transportScope.cancel()
        SpeakTransportServiceImpl.instance = null
    }

    private fun awaitTransport() {
        shadowOf(Looper.getMainLooper()).idle()
        runBlocking {
            withTimeout(5_000) { transportScope.coroutineContext.job.children.toList().joinAll() }
        }
    }

    @Test
    fun bookmarkButtonUpdatesAfterChangingTextFollowsSpeechWithoutRestart() {
        val koin = GlobalContext.get()
        val advanced = koin.get<AdvancedSpeakSettings>()
        advanced.autoBookmark = true
        advanced.synchronize = true
        val transport = SpeakTransportServiceImpl(transportScope, fakePlatform, advanced, speakControl, bookmarkControl)
        val launcher = koin.get<OrderedLauncher>()
        awaitTransport()
        assertFalse(transport.state.value.bookmarkButtonVisible)

        val verse = (book.getKey("Gen.1.1") as RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        runBlocking { launcher.join(SPEAK_KEY) }
        speakSettingsService.setSynchronize(false)
        assertFalse(speakSettingsService.advanced.value.synchronize)
        awaitTransport()
        assertFalse("Changing text following alone must not invent a speech bookmark", transport.state.value.bookmarkButtonVisible)
        // Pausing persists the first automatic speech bookmark after the setting was changed.
        transport.pause()
        runBlocking { launcher.join(SPEAK_KEY) }
        awaitTransport()

        val bookmark = runBlocking { bookmarkControl.speakBookmarkForVerse(verse) }
        assertNotNull("The speech bookmark must have been persisted", bookmark)
        assertTrue("The existing transport must show the newly saved bookmark", transport.state.value.bookmarkButtonVisible)

        runBlocking { bookmarkControl.deleteBookmark(bookmark!!) }
        awaitTransport()
        assertFalse("Removing the last speech bookmark must hide the button", transport.state.value.bookmarkButtonVisible)
    }
}
