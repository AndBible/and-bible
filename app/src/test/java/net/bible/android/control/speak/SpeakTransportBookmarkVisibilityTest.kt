package net.bible.android.control.speak

import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
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

    private var transport: SpeakTransportServiceImpl? = null

    @After
    fun closeTransport() {
        transport?.close()
        transportScope.cancel()
        SpeakTransportServiceImpl.instance = null
        GlobalContext.get().get<AdvancedSpeakSettings>().reset()
    }

    /**
     * Waits for the transport's launched work. Must not block the Main thread in `runBlocking`: the work can hop to
     * `Dispatchers.Main` (speakFromBookmark), which is the Robolectric looper this thread owns. So pump the looper
     * between polls instead of joining.
     */
    private fun awaitTransport() {
        val deadline = System.nanoTime() + 5_000_000_000L
        val looper = shadowOf(Looper.getMainLooper())
        while (true) {
            looper.idle()
            if (transportScope.coroutineContext.job.children.none { it.isActive }) return
            check(System.nanoTime() < deadline) { "transport work did not finish within 5 s" }
            Thread.sleep(5)
        }
    }

    @Test
    fun bookmarkButtonUpdatesAfterChangingTextFollowsSpeechWithoutRestart() {
        val koin = GlobalContext.get()
        val advanced = koin.get<AdvancedSpeakSettings>()
        advanced.autoBookmark = true
        advanced.synchronize = true
        val transport = SpeakTransportServiceImpl(transportScope, fakePlatform, advanced, speakControl, bookmarkControl)
        this.transport = transport
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

    /** A widget tap can be the first speech action of the process: the id must resolve without [speakBookmarks] having run. */
    @Test
    fun speakFromBookmarkResolvesAnIdTheCacheNeverSaw() {
        val koin = GlobalContext.get()
        val advanced = koin.get<AdvancedSpeakSettings>()
        advanced.autoBookmark = true
        val first = SpeakTransportServiceImpl(transportScope, fakePlatform, advanced, speakControl, bookmarkControl)
        val launcher = koin.get<OrderedLauncher>()
        awaitTransport()
        val verse = (book.getKey("Gen.1.1") as RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        runBlocking { launcher.join(SPEAK_KEY) }
        first.pause()
        runBlocking { launcher.join(SPEAK_KEY) }
        awaitTransport()
        val bookmark = runBlocking { bookmarkControl.speakBookmarkForVerse(verse) }
        assertNotNull(bookmark)
        speakControl.stop(true)
        runBlocking { launcher.join(SPEAK_KEY) }
        first.close()

        val fresh = SpeakTransportServiceImpl(transportScope, fakePlatform, advanced, speakControl, bookmarkControl)
        this.transport = fresh
        awaitTransport()
        assertFalse(speakControl.isSpeaking)
        fresh.speakFromBookmark(bookmark!!.id.toString())
        awaitTransport()
        runBlocking { launcher.join(SPEAK_KEY) }
        assertTrue("speakFromBookmark must read the bookmark from the database on a cache miss", speakControl.isSpeaking)
    }
}
