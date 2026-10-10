package net.bible.service.device.speak

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.speak.*
import net.bible.sharedcore.platform.UserNotifier
import net.bible.test.testCoreStrings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

class FakeSpeakEngine(var languages: Map<Locale, SpeakLanguageResult>) : SpeakEngine {
    val calls = mutableListOf<String>()
    val requestedLocales = mutableListOf<Locale>()
    override fun speakText(text: String, utteranceId: String) { calls += "speak:$text" }
    override fun playEarcon(earcon: String, volume: Float, utteranceId: String) { calls += "earcon:$earcon" }
    override fun playSilence(durationMs: Long, utteranceId: String) { calls += "silence:$durationMs" }
    override fun setLanguage(locale: Locale): SpeakLanguageResult {
        requestedLocales += locale
        return languages[locale] ?: SpeakLanguageResult.NOT_SUPPORTED
    }
    override fun setSpeechRate(rate: Float) { calls += "rate:$rate" }
    override fun flush() { calls += "flush" }
    override fun stop() { calls += "stop" }
    override fun shutdown() { calls += "shutdown" }
    override val maxSpeechInputLength = 4000
}

class FakeSpeakPlatform(private val engine: FakeSpeakEngine, private val autoInit: Boolean = false) : SpeakPlatform {
    override var preferredEnginePackage: String? = null
    override val maxSpeechInputLength get() = engine.maxSpeechInputLength
    var creations = 0
    var listener: SpeakEngineListener? = null
    override fun createEngine(listener: SpeakEngineListener): SpeakEngine {
        this.listener = listener
        creations++
        if (autoInit) listener.onInit(true)
        return engine
    }
    var focusGranted = true
    var focusChanges: ((Boolean) -> Unit)? = null
    var focusAbandons = 0
    override fun requestAudioFocus(onFocusChange: (Boolean) -> Unit): Boolean {
        focusChanges = onFocusChange
        return focusGranted
    }
    override fun abandonAudioFocus() { focusAbandons++ }
    override fun startPhoneCallMonitoring(onCallStateChanged: (Boolean) -> Unit) {}
    override fun onPrepareForSpeaking() {}
    override fun attachMediaControls(control: SpeakControl) {}
    override fun detachMediaControls() {}
    override fun bringReadingToFront() {}
    override fun phrasesFor(languageCode: String) = SpeakPhrases("1.", "2.", "3.", "4.", "5.", "book", "chapter")
}

@Config(qualifiers = "fi", application = TestBibleApplication::class, sdk = [TEST_SDK])
@RunWith(RobolectricTestRunner::class)
class TextToSpeechServiceManagerEngineTest : SpeakIntegrationTestBase() {
    private class RecordingNotifier : UserNotifier {
        val errors = mutableListOf<String>()
        val causes = mutableListOf<Throwable?>()
        override fun showInfo(message: String, long: Boolean) {}
        override fun showError(message: String, cause: Throwable?) {
            errors += message
            causes += cause
        }
    }

    private lateinit var engine: FakeSpeakEngine
    private lateinit var platform: FakeSpeakPlatform
    private lateinit var notifier: RecordingNotifier

    override fun configureSpeakPlatform() {
        engine = FakeSpeakEngine(emptyMap())
        platform = FakeSpeakPlatform(engine)
        notifier = RecordingNotifier()
        loadKoinModules(module {
            single<SpeakPlatform> { platform }
            single<UserNotifier> { notifier }
        })
    }

    private fun start(languages: Map<Locale, SpeakLanguageResult>, success: Boolean = true): Pair<FakeSpeakEngine, RecordingNotifier> {
        engine.languages = languages
        configureFreshControl()
        speakControl.speakBible(book, (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0))
        platform.listener!!.onInit(success)
        return engine to notifier
    }

    private fun configureFreshControl() {
        loadKoinModules(module {
            single { TextToSpeechServiceManager(get(), get(), get(), speakControl, platform, notifier, get(), get()) }
        })
        // App/settings initialization can resolve the original control's lazy manager before
        // test overrides. A fresh control guarantees that playback uses the fake manager.
        speakControl = SpeakControl(lazy { GlobalContext.get().get<TextToSpeechServiceManager>() }, windowControl, platform)
    }

    @Test fun allLocalesUnsupportedShowsMessageAndStops() {
        val (engine, notifier) = start(emptyMap())
        assertTrue(notifier.errors.contains(testCoreStrings().ttsLangNotAvailable))
        assertEquals(listOf<Throwable?>(null), notifier.causes)
        assertTrue(speakControl.isStopped)
        assertTrue(engine.calls.contains("stop"))
        assertFalse(engine.calls.any { it.startsWith("speak:") })
    }

    @Test fun secondLocaleAcceptedIsUsed() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("fi", "SE"))
            val second = Locale("fi", "FI")
            val (engine, notifier) = start(mapOf(second to SpeakLanguageResult.AVAILABLE))
            assertEquals(listOf(Locale("fi", "SE"), second), engine.requestedLocales)
            assertTrue(engine.calls.any { it.startsWith("speak:") })
            assertTrue(GlobalContext.get().get<TextToSpeechServiceManager>().isLanguageAvailable(second.language))
            assertTrue(notifier.errors.isEmpty())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun changedEnginePreferenceRecreatesEngine() {
        engine.languages = mapOf(Locale("fi", "FI") to SpeakLanguageResult.AVAILABLE,
            Locale("fi") to SpeakLanguageResult.AVAILABLE)
        configureFreshControl()
        val verse = (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        platform.listener!!.onInit(true)
        platform.preferredEnginePackage = "changed.engine"
        speakControl.speakBible(book, verse)
        assertEquals(2, platform.creations)
        assertTrue(engine.calls.contains("shutdown"))
        platform.listener!!.onInit(true)
        assertTrue(speakControl.isSpeaking)
    }

    private fun supportedFinnish() = mapOf(Locale("fi", "FI") to SpeakLanguageResult.AVAILABLE,
        Locale("fi") to SpeakLanguageResult.AVAILABLE)

    @Test fun deniedAudioFocusStopsWithoutQueuingSpeech() {
        platform.focusGranted = false
        start(supportedFinnish())
        assertTrue(speakControl.isStopped)
        assertTrue(engine.calls.contains("stop"))
        assertTrue(engine.calls.contains("shutdown"))
        assertFalse(engine.calls.any { it.startsWith("speak:") })
        assertEquals(1, platform.focusAbandons)
    }

    @Test fun lostAudioFocusPausesAndRetainsFocusRequest() {
        start(supportedFinnish())
        assertTrue(speakControl.isSpeaking)
        platform.focusChanges!!(false)
        assertTrue(speakControl.isPaused)
        assertFalse(speakControl.isSpeaking)
        assertTrue(engine.calls.contains("shutdown"))
        assertEquals(0, platform.focusAbandons)
    }

    @Test fun regainedAudioFocusResumesPausedSpeech() {
        start(supportedFinnish())
        platform.focusChanges!!(false)
        assertTrue(speakControl.isPaused)
        val queuedBefore = engine.calls.count { it.startsWith("speak:") }
        platform.focusChanges!!(true)
        assertEquals(2, platform.creations)
        platform.listener!!.onInit(true)
        assertTrue(speakControl.isSpeaking)
        assertFalse(speakControl.isPaused)
        assertTrue(engine.calls.count { it.startsWith("speak:") } > queuedBefore)
    }

    @Test fun initFailureStops() {
        val (engine, notifier) = start(emptyMap(), success = false)
        assertTrue(notifier.errors.contains(testCoreStrings().errorOccurred))
        assertEquals(listOf<Throwable?>(null), notifier.causes)
        assertTrue(speakControl.isStopped)
        assertTrue(engine.calls.contains("shutdown"))
    }
}
