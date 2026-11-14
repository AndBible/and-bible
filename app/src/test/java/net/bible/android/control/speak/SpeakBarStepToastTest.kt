package net.bible.android.control.speak

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.device.speak.SpeakIntegrationTestBase
import net.bible.sharedcore.platform.UserNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #4091: the speak bar's Next/Previous (one-verse steps) must not raise the media-command toasts
 * "Forward"/"Rewind"; those name the separate fast-forward/rewind buttons only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fi", application = TestBibleApplication::class, sdk = [TEST_SDK])
class SpeakBarStepToastTest : SpeakIntegrationTestBase() {
    private val infos = mutableListOf<String>()

    override fun configureSpeakPlatform() {
        loadKoinModules(module {
            single<UserNotifier> {
                object : UserNotifier {
                    override fun showInfo(message: String, long: Boolean) { infos += message }
                    override fun showError(message: String, cause: Throwable?) {}
                }
            }
        })
    }

    private fun startSpeaking() {
        speakControl.speakBible(book, (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0))
        kotlinx.coroutines.runBlocking { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(SPEAK_KEY) }
        assertTrue(speakControl.isSpeaking)
        infos.clear()
    }

    @Test fun barNextAndPreviousShowNoMediaCommandToast() {
        startSpeaking()
        val transport = GlobalContext.get().get<SpeakTransportServiceImpl>()
        transport.nextVerse()
        transport.prevVerse()
        assertEquals(emptyList<String>(), infos)
    }

    @Test fun barFastForwardAndRewindStillShowTheirToasts() {
        val strings = GlobalContext.get().get<net.bible.sharedcore.platform.CoreStrings>()
        startSpeaking()
        val transport = GlobalContext.get().get<SpeakTransportServiceImpl>()
        transport.forward()
        transport.rewind()
        assertEquals(listOf(strings.forward, strings.rewind), infos)
    }
}
