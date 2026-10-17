package net.bible.service.device.speak

import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.launch
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.speak.*
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import net.bible.sharedcore.platform.speak.SpeakLanguageResult
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
    val utteranceIds = mutableListOf<String>()
    override fun speakText(text: String, utteranceId: String) { calls += "speak:$text"; utteranceIds += utteranceId }
    override fun playEarcon(earcon: String, volume: Float, utteranceId: String) { calls += "earcon:$earcon" }
    override fun playSilence(durationMs: Long, utteranceId: String) { calls += "silence:$durationMs" }
    override fun setLanguage(languageTag: String): SpeakLanguageResult {
        val locale = Locale.forLanguageTag(languageTag)
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
    val problems = mutableListOf<SpeakSetupProblem>()
    override fun reportProblem(problem: SpeakSetupProblem) { problems += problem }
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
        kotlinx.coroutines.runBlocking { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(net.bible.android.control.speak.SPEAK_KEY) }
        return engine to notifier
    }

    private fun configureFreshControl(mainDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined) {
        loadKoinModules(module {
            single { TextToSpeechServiceManager(get(), get(), get(), speakControl, platform, notifier, get(), get(), get(), get(), { net.bible.service.db.DatabaseContainer.instance.myDocumentDb.myDocumentDao() }, net.bible.test.testSwordContentFacade(), mainDispatcher) }
        })
        // App/settings initialization can resolve the original control's lazy manager before
        // test overrides. A fresh control guarantees that playback uses the fake manager.
        speakControl = GlobalContext.get().run {
            SpeakControl(lazy { get<TextToSpeechServiceManager>() }, windowControl, platform, notifier, get(), get(), get(), get(), get(), get())
        }
    }

    @Test
    fun queuedReadingPlanPassageDoesNotReplaceCurrentUtterance() {
        engine.languages = supportedFinnish()
        configureFreshControl()
        val launcher = GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>()
        speakControl.speakKeyListLegacy(book, listOf(book.getKey("Gen.1.1")), true)
        platform.listener!!.onInit(true)
        kotlinx.coroutines.runBlocking { launcher.join(SPEAK_KEY) }
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val provider = org.robolectric.util.ReflectionHelpers.getField<LegacySpeakTextProvider>(manager, "legacySpeakTextProvider")
        platform.listener!!.onUtteranceStart("AND-BIBLE-0")
        val currentText = provider.getText("AND-BIBLE-0")
        val queuedBefore = engine.calls.count { it.startsWith("speak:") }
        speakControl.speakKeyListLegacy(book, listOf(book.getKey("Gen.2.1")), true)
        kotlinx.coroutines.runBlocking { launcher.join(SPEAK_KEY) }
        assertEquals("Appending a passage must not queue a new chunk during the current utterance", queuedBefore,
            engine.calls.count { it.startsWith("speak:") })
        assertEquals("Appending a passage must retain the current utterance text", currentText, provider.getText("AND-BIBLE-0"))
        platform.listener!!.onUtteranceDone("AND-BIBLE-0")
        joinSpeech()
        assertTrue("Completion must advance the queued passage", engine.calls.count { it.startsWith("speak:") } > queuedBefore)
        speakControl.stop()
    }

    @Test
    fun queuedReadingPlanPassageResumesAfterEnginePreferenceChanges() {
        engine.languages = supportedFinnish()
        configureFreshControl()
        val launcher = GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>()
        speakControl.speakKeyListLegacy(book, listOf(book.getKey("Gen.1.1")), true)
        platform.listener!!.onInit(true)
        kotlinx.coroutines.runBlocking { launcher.join(SPEAK_KEY) }
        platform.listener!!.onUtteranceStart("AND-BIBLE-0")
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val provider = org.robolectric.util.ReflectionHelpers.getField<LegacySpeakTextProvider>(manager, "legacySpeakTextProvider")
        val currentText = provider.getText("AND-BIBLE-0")
        val queuedBefore = engine.calls.count { it.startsWith("speak:") }
        platform.preferredEnginePackage = "replacement.engine"
        try {
            speakControl.speakKeyListLegacy(book, listOf(book.getKey("Gen.2.1")), true)
            assertEquals(2, platform.creations)
            assertEquals("Replacement must await real initialization", queuedBefore, engine.calls.count { it.startsWith("speak:") })
            assertEquals("Pending replacement must retain the current text", currentText, provider.getText("AND-BIBLE-0"))
            platform.listener!!.onInit(true)
            kotlinx.coroutines.runBlocking { launcher.join(SPEAK_KEY) }
            assertTrue("Replacement engine must receive the next queued chunk", engine.calls.count { it.startsWith("speak:") } > queuedBefore)
            assertTrue(speakControl.isSpeaking)
            assertNotEquals("A replacement must advance text after the old engine stops", currentText, provider.getText("AND-BIBLE-1"))
        } finally {
            speakControl.stop(force = true)
        }
    }

    @Test
    fun stopBeforeLoopEngineInitRestoresOriginalSettings() {
        verifyPendingLoopCleanup { assertFalse("Stop must restore non-loop playback settings", net.bible.android.database.bookmarks.SpeakSettings.load().playbackSettings.isMemorizationLoop) }
    }

    @Test
    fun stopBeforeLoopEngineInitRemovesSleepTimer() {
        verifyPendingLoopCleanup { assertFalse("Stop must cancel the sleep timer", speakControl.sleepTimerActive()) }
    }

    private fun verifyPendingLoopCleanup(assertCleanup: () -> Unit) {
        engine.languages = supportedFinnish()
        configureFreshControl()
        val original = net.bible.android.database.bookmarks.SpeakSettings().apply { sleepTimer = 60 }
        original.save()
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        try {
            speakControl.speakMemorizationLoop(book, verse, verse)
            assertTrue(speakControl.isMemorizationLoop)
            assertTrue(speakControl.sleepTimerActive())
            val callback = platform.listener!!
            speakControl.stop()
            assertCleanup()
            assertFalse(speakControl.isMemorizationLoop)
            callback.onInit(true)
            kotlinx.coroutines.runBlocking { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(SPEAK_KEY) }
            assertFalse(engine.calls.any { it.startsWith("speak:") })
        } finally {
            speakControl.stop(force = true)
            net.bible.android.database.bookmarks.SpeakSettings().save()
        }
    }

    private fun joinSpeech() = kotlinx.coroutines.runBlocking {
        kotlinx.coroutines.withTimeout(4000) {
            GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(SPEAK_KEY)
        }
    }

    private inner class HeldEpub : AutoCloseable {
        private val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        val entered = java.util.concurrent.CountDownLatch(1)
        val resume = kotlinx.coroutines.CompletableDeferred<Unit>()
        val hold = java.util.concurrent.atomic.AtomicBoolean(true)
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val state: net.bible.service.sword.epub.EpubBackendState
        val book: org.crosswire.jsword.book.sword.SwordGenBook
        val key: net.bible.service.sword.BookAndKey
        private val real: net.bible.android.database.EpubDao
        init {
            fixture.setUp()
            var acquiredState: net.bible.service.sword.epub.EpubBackendState? = null
            var registered: org.crosswire.jsword.book.sword.SwordGenBook? = null
            try {
            val dir = fixture.buildEpub("held-speech-ordinal")
            java.io.File(dir, "OEBPS/ch1.xhtml").writeText(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>One</title></head><body>" +
                    (1..50).joinToString("") { "<p>Sentence $it is readable.</p>" } + "</body></html>")
            state = net.bible.service.sword.epub.EpubBackendState(dir.toOkioPath(), testCoreStrings(),
                net.bible.android.platform.AndroidAppEnvironment(app))
            acquiredState = state
            val metadata = state.getBookMetaData()
            book = org.crosswire.jsword.book.sword.SwordGenBook(metadata, net.bible.service.sword.epub.EpubBackend(state, metadata))
            org.crosswire.jsword.book.Books.installed().addBook(book)
            registered = book
            net.bible.service.sword.SwordContentFacade.clearCaches()
            val first = state.keys.first()
            val range = kotlinx.coroutines.runBlocking { state.getOrdinalRange(first) }
            key = net.bible.service.sword.BookAndKey(first, book, net.bible.android.control.page.OrdinalRange(range.first))
            // Warm only this fragment's text so the held DAO call is the noncontract ordinal read,
            // not JSword's synchronous readRawContent edge.
            net.bible.service.sword.SwordContentFacade.getTextWithinOrdinalsAsString(book, first, range, testCoreStrings())
            real = org.robolectric.util.ReflectionHelpers.getField(state, "dao")
            org.robolectric.util.ReflectionHelpers.setField(state, "dao", object : net.bible.android.database.EpubDao by real {
                override suspend fun getFragment(id: Long): net.bible.android.database.EpubFragment? {
                    reads.incrementAndGet()
                    if (hold.get()) {
                        entered.countDown()
                        kotlinx.coroutines.withTimeout(3000) { resume.await() }
                    }
                    return real.getFragment(id)
                }
            })
            } catch (failure: Throwable) {
                try {
                    registered?.let { org.crosswire.jsword.book.Books.installed().removeBook(it) }
                    net.bible.service.sword.SwordContentFacade.clearCaches()
                } finally {
                    try { acquiredState?.close() } finally { fixture.tearDown() }
                }
                throw failure
            }
        }
        override fun close() {
            resume.complete(Unit)
            try { joinSpeech() } finally {
                try {
                    org.robolectric.util.ReflectionHelpers.setField(state, "dao", real)
                    speakControl.stop(force = true)
                    joinSpeech()
                } finally {
                    try {
                        org.crosswire.jsword.book.Books.installed().removeBook(book)
                        net.bible.service.sword.SwordContentFacade.clearCaches()
                        state.close()
                    } finally { fixture.tearDown() }
                }
            }
        }
    }

    private fun prepareGeneric(): TextToSpeechServiceManager {
        engine.languages = mapOf(Locale.UK to SpeakLanguageResult.AVAILABLE,
            Locale.ENGLISH to SpeakLanguageResult.AVAILABLE)
        configureFreshControl()
        return GlobalContext.get().get()
    }

    @Test
    fun providerReplacementRemainsLiveAndSuppressesHeldOldCursor() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            val replacementDone = java.util.concurrent.CountDownLatch(1)
            var replacement: Thread? = null
            try {
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                val provider = org.robolectric.util.ReflectionHelpers.getField<GeneralSpeakTextProvider>(manager, "generalSpeakTextProvider")
                replacement = Thread {
                    manager.speakBible(book, (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0))
                    replacementDone.countDown()
                }.apply { start() }
                assertTrue("Provider replacement must not wait for the old ordinal lookup", replacementDone.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                epub.resume.complete(Unit)
                replacement.join(3000)
                joinSpeech()
                assertEquals("An obsolete read must not advance the replaced provider", epub.key.ordinal!!.start,
                    provider.getCurrentlyPlayingKey().ordinal!!.start)
                assertFalse(engine.calls.any { it.contains("Sentence") })
            } finally {
                epub.resume.complete(Unit)
                replacement?.join(3000)
            }
        }
    }

    @Test
    fun engineReplacementRejectsHeldChunkAndOldCallbacks() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            var replacement: Thread? = null
            val replaced = java.util.concurrent.CountDownLatch(1)
            try {
                manager.speakGeneric(epub.key)
                val oldListener = platform.listener!!
                oldListener.onInit(true)
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                platform.preferredEnginePackage = "replacement.engine"
                replacement = Thread { manager.speakGeneric(epub.key); replaced.countDown() }.apply { start() }
                assertTrue("Engine replacement must not wait for a held ordinal read", replaced.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                epub.resume.complete(Unit)
                replacement.join(3000)
                joinSpeech()
                assertFalse("Old chunk must not queue before replacement readiness", engine.calls.any { it.startsWith("speak:") })
                oldListener.onUtteranceStart("AND-BIBLE-0")
                oldListener.onUtteranceDone("AND-BIBLE-0")
                joinSpeech()
                assertFalse(engine.calls.any { it.startsWith("speak:") })
                platform.listener!!.onInit(true)
                joinSpeech()
                assertTrue(engine.calls.any { it.contains("Sentence") })
            } finally {
                epub.resume.complete(Unit)
                replacement?.join(3000)
            }
        }
    }

    @Test
    fun rapidMovementAndChunkSubmissionRemainFifo() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            var rewind: Thread? = null
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                platform.listener!!.onUtteranceStart(engine.utteranceIds.first())
                joinSpeech()
                manager.pause(true)
                val initial = (manager.currentlyPlayingKey as net.bible.service.sword.BookAndKey).ordinal!!.start
                epub.hold.set(true)
                val submitted = java.util.concurrent.CountDownLatch(1)
                rewind = Thread { manager.rewind(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE); submitted.countDown() }.apply { start() }
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                assertTrue("Movement must enqueue, not block its caller on lazy ordinals", submitted.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                epub.resume.complete(Unit)
                rewind.join(3000)
                joinSpeech()
                // Rewind wraps to previous chapter, then forward returns and advances once.
                assertEquals(initial + 1, (manager.currentlyPlayingKey as net.bible.service.sword.BookAndKey).ordinal!!.start)
                manager.continueAfterPause()
                joinSpeech()
                val count = engine.utteranceIds.size
                val last = engine.utteranceIds.last()
                platform.listener!!.onUtteranceDone(last)
                platform.listener!!.onUtteranceDone(last)
                joinSpeech()
                assertEquals("Duplicate completion cannot advance two chunks", 20, engine.utteranceIds.size - count)
                assertEquals(engine.utteranceIds.size, engine.utteranceIds.distinct().size)
            } finally {
                epub.resume.complete(Unit)
                rewind?.join(3000)
            }
        }
    }

    @Test fun pauseOwnsLifecycleDuringHeldForward() = pauseDuringHeldMovement(backwards = false)
    @Test fun pauseOwnsLifecycleDuringHeldRewind() = pauseDuringHeldMovement(backwards = true)

    private fun pauseDuringHeldMovement(backwards: Boolean) {
        val manager = prepareGeneric()
        val advanced = GlobalContext.get().get<AdvancedSpeakSettings>()
        val oldAutoBookmark = advanced.autoBookmark
        val settings = GlobalContext.get().get<net.bible.sharedcore.platform.AppSettings>()
        val hostJob = kotlinx.coroutines.Job()
        HeldEpub().use { epub ->
            val states = java.util.Collections.synchronizedList(mutableListOf<SpeakPlaybackState>())
            val progress = java.util.Collections.synchronizedList(mutableListOf<SpeakChange.Progress>())
            val sub = SpeakChanges.changes.subscribe {
                if (it is SpeakChange.State) states += it.speakState
                if (it is SpeakChange.Progress) progress += it
            }
            try {
                advanced.autoBookmark = true
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                val oldListener = platform.listener!!
                val first = engine.utteranceIds.first()
                oldListener.onUtteranceStart(first)
                joinSpeech()
                assertTrue(manager.isSpeaking)
                assertFalse(manager.isPaused)
                val utterances = engine.utteranceIds.size
                states.clear()
                progress.clear()
                epub.hold.set(true)
                if (backwards) manager.rewind(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                else manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                assertTrue("Real movement must reach the held EPUB ordinal read", epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                assertTrue(manager.isPaused)
                assertFalse(manager.isSpeaking)
                kotlinx.coroutines.CoroutineScope(hostJob + kotlinx.coroutines.Dispatchers.Unconfined).launch {
                    speakControl.pause(willContinueAfterThis = false, toast = false)
                }
                hostJob.cancel()
                val differences = mutableListOf<String>()
                fun checkPause(condition: Boolean, message: String) { if (!condition) differences += message }
                val provider = org.robolectric.util.ReflectionHelpers.getField<GeneralSpeakTextProvider>(manager, "generalSpeakTextProvider")
                checkPause(!provider.isSpeaking, "provider.pause must end the active provider lifecycle")
                checkPause(settings.getString("SpeakGenBook") == epub.book.abbreviation, "generic pause book must be persisted before read release")
                checkPause(settings.getString("SpeakGenKey") == epub.key.serialized, "generic pause position must be persisted before read release")
                checkPause(settings.getString("SpeakLocale") != null, "manager pause locale must be persisted")
                checkPause(states.toList() == listOf(SpeakPlaybackState.PAUSED), "Pause must publish a new PAUSED event, not retain a prior projection: $states")
                checkPause(engine.calls.contains("shutdown"), "long Pause must shut down the engine before read release")
                epub.resume.complete(Unit)
                joinSpeech()
                val saved = kotlinx.coroutines.runBlocking { bookmarkControl.getGenericBookmarksWithLabel(bookmarkControl.speakLabel()) }
                    .filter { it.book?.initials == epub.book.initials }
                checkPause(saved.size == 1, "application-owned pause bookmark must survive host cancellation: ${saved.size}")
                checkPause(saved.singleOrNull()?.ordinalStart == epub.key.ordinal!!.start, "pause bookmark must retain active utterance position")
                checkPause(manager.isPaused && !manager.isSpeaking, "released movement must remain intentionally paused")
                checkPause((manager.currentlyPlayingKey as net.bible.service.sword.BookAndKey).ordinal!!.start == epub.key.ordinal!!.start,
                    "obsolete movement must not publish its target cursor")
                oldListener.onUtteranceStart(first)
                oldListener.onUtteranceDone(engine.utteranceIds.last())
                joinSpeech()
                checkPause(engine.utteranceIds.size == utterances, "obsolete movement/callbacks must not resume audio")
                checkPause(progress.isEmpty(), "obsolete movement/callbacks must not publish progress")
                println("HELD_PAUSE backwards=$backwards persisted=${settings.getString("SpeakGenKey")} states=$states bookmarks=${saved.size} engine=${engine.calls.takeLast(3)} differences=$differences")
                assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
            } finally {
                epub.resume.complete(Unit)
                hostJob.cancel()
                sub.cancel()
                advanced.autoBookmark = oldAutoBookmark
            }
        }
    }

    @Test fun resumeSupersedesHeldPlayingMovement() = supersedeHeldMovement("resume")
    @Test fun stopSupersedesHeldPlayingMovement() = supersedeHeldMovement("stop")
    @Test fun replacementSupersedesHeldPlayingMovement() = supersedeHeldMovement("replacement")

    private fun supersedeHeldMovement(action: String) {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            val progress = java.util.Collections.synchronizedList(mutableListOf<SpeakChange.Progress>())
            val sub = SpeakChanges.changes.subscribe { if (it is SpeakChange.Progress) progress += it }
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                platform.listener!!.onUtteranceStart(engine.utteranceIds.first())
                joinSpeech()
                val provider = org.robolectric.util.ReflectionHelpers.getField<GeneralSpeakTextProvider>(manager, "generalSpeakTextProvider")
                progress.clear()
                epub.hold.set(true)
                manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                when (action) {
                    "resume" -> manager.continueAfterPause()
                    "stop" -> manager.shutdown()
                    "replacement" -> manager.speakGeneric(epub.key)
                }
                val cursor = provider.getCurrentlyPlayingKey().ordinal!!.start
                epub.hold.set(false)
                epub.resume.complete(Unit)
                joinSpeech()
                assertTrue("$action must suppress the obsolete movement's progress", progress.isEmpty())
                if (action == "stop") {
                    assertFalse(manager.isSpeaking || manager.isPaused)
                    assertTrue(engine.calls.contains("shutdown"))
                    assertEquals(cursor, provider.getCurrentlyPlayingKey().ordinal!!.start)
                } else {
                    assertTrue("$action must retain newer playback", manager.isSpeaking)
                    assertFalse(manager.isPaused)
                }
            } finally { epub.resume.complete(Unit); sub.cancel() }
        }
    }

    private class QueuedMain : kotlinx.coroutines.CoroutineDispatcher() {
        private val tasks = java.util.concurrent.LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { tasks.put(block) }
        fun take(): Runnable = checkNotNull(tasks.poll(3, java.util.concurrent.TimeUnit.SECONDS)) { "Expected Main handoff runnable" }
        fun drainSpeech() {
            val joined = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(SPEAK_KEY)
            }
            try {
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4)
                while (!joined.isCompleted && System.nanoTime() < deadline) {
                    tasks.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS)?.run()
                }
                check(joined.isCompleted) { "Speech FIFO did not drain with queued Main" }
            } finally { joined.cancel() }
        }
    }

    @Test fun publicPauseOwnsForwardTailHandoff() = publicMovementTail(backwards = false, stop = false)
    @Test fun publicPauseOwnsRewindTailHandoff() = publicMovementTail(backwards = true, stop = false)
    @Test fun publicStopOwnsMovementTailHandoff() = publicMovementTail(backwards = false, stop = true)

    private fun publicMovementTail(backwards: Boolean, stop: Boolean) {
        val main = QueuedMain()
        engine.languages = mapOf(Locale.UK to SpeakLanguageResult.AVAILABLE, Locale.ENGLISH to SpeakLanguageResult.AVAILABLE)
        configureFreshControl(main)
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val advanced = GlobalContext.get().get<AdvancedSpeakSettings>()
        val oldAutoBookmark = advanced.autoBookmark
        val settings = GlobalContext.get().get<net.bible.sharedcore.platform.AppSettings>()
        HeldEpub().use { epub ->
            val states = java.util.Collections.synchronizedList(mutableListOf<SpeakPlaybackState>())
            val sub = SpeakChanges.changes.subscribe { if (it is SpeakChange.State) states += it.speakState }
            var heldStart: Runnable? = null
            try {
                advanced.autoBookmark = true
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                main.drainSpeech()
                platform.listener!!.onUtteranceStart(engine.utteranceIds.first())
                main.drainSpeech()
                assertTrue(speakControl.isSpeaking)
                val utterances = engine.utteranceIds.size
                states.clear()
                epub.hold.set(true)
                if (backwards) speakControl.rewind(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                else speakControl.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                main.take().run() // move entry Main; real ordinal read is next on the Default worker.
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                assertTrue(speakControl.isPaused)
                epub.hold.set(false)
                epub.resume.complete(Unit)
                main.take().run() // refreshStatus publication Main.
                main.take().run() // final movement flags Main, before playback ownership handoff.
                heldStart = main.take() // startSpeaking Main is queued but MUST NOT run before public command.
                val target = manager.currentlyPlayingKey as net.bible.service.sword.BookAndKey
                val stoppedAtTail = speakControl.isStopped
                val provider = org.robolectric.util.ReflectionHelpers.getField<GeneralSpeakTextProvider>(manager, "generalSpeakTextProvider")
                val differences = mutableListOf<String>()
                fun verify(condition: Boolean, message: String) { if (!condition) differences += message }
                verify(!stoppedAtTail, "Public isStopped must not classify active movement handoff as stopped")
                if (stop) speakControl.stop() else speakControl.pause(willContinueAfterThis = false, toast = false)
                verify(!provider.isSpeaking, "Public command must end provider playback lifecycle")
                verify(engine.calls.contains("shutdown"), "Public command must shut down engine before startMain")
                verify(states.toList() == listOf(if (stop) SpeakPlaybackState.SILENT else SpeakPlaybackState.PAUSED),
                    "Public command must emit its own lifecycle event: $states")
                if (!stop) {
                    verify(settings.getString("SpeakGenBook") == epub.book.abbreviation, "Pause must persist generic book")
                    verify(settings.getString("SpeakGenKey") == target.serialized, "Pause must persist completed movement target")
                    verify(settings.getString("SpeakLocale") != null, "Pause must persist locale")
                }
                heldStart.run()
                heldStart = null
                main.drainSpeech()
                val saved = kotlinx.coroutines.runBlocking { bookmarkControl.getGenericBookmarksWithLabel(bookmarkControl.speakLabel()) }
                    .filter { it.book?.initials == epub.book.initials }
                verify(saved.size == 1 && saved.singleOrNull()?.ordinalStart == target.ordinal!!.start,
                    "Public command must apply app-owned bookmark for completed target")
                verify(engine.utteranceIds.size == utterances, "Invalidated startMain must not resume or submit utterances")
                verify(if (stop) speakControl.isStopped else speakControl.isPaused && !speakControl.isSpeaking,
                    "Public command state must survive draining obsolete startMain")
                println("PUBLIC_TAIL backwards=$backwards stop=$stop stoppedAtTail=$stoppedAtTail states=$states bookmarks=${saved.size} differences=$differences")
                assertEquals(differences.joinToString("\n"), emptyList<String>(), differences)
            } finally {
                epub.resume.complete(Unit)
                heldStart?.run()
                main.drainSpeech()
                speakControl.stop(force = true)
                main.drainSpeech()
                sub.cancel()
                advanced.autoBookmark = oldAutoBookmark
            }
        }
    }

    @Test fun repeatedPauseDuringIntentionallyPausedMovementDoesNotRepeatLifecycle() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            val states = java.util.Collections.synchronizedList(mutableListOf<SpeakPlaybackState>())
            val sub = SpeakChanges.changes.subscribe { if (it is SpeakChange.State) states += it.speakState }
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                manager.pause(true)
                joinSpeech()
                val before = manager.currentlyPlayingKey
                val stops = engine.calls.count { it == "stop" }
                states.clear()
                epub.hold.set(true)
                manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                manager.pause(false)
                assertTrue(states.isEmpty())
                assertFalse(engine.calls.contains("shutdown"))
                assertEquals(stops, engine.calls.count { it == "stop" })
                epub.resume.complete(Unit)
                joinSpeech()
                assertEquals(before, manager.currentlyPlayingKey)
                assertTrue(manager.isPaused)
            } finally { epub.resume.complete(Unit); sub.cancel() }
        }
    }

    @Test
    fun stoppedBibleRenderCannotPublishCursorOrUtteranceState() {
        engine.languages = supportedFinnish()
        configureFreshControl()
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val provider = org.robolectric.util.ReflectionHelpers.getField<BibleSpeakTextProvider>(manager, "bibleSpeakTextProvider")
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val verse = (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        val cache = org.robolectric.util.ReflectionHelpers.getField<androidx.collection.LruCache<Pair<org.crosswire.jsword.book.sword.SwordBook, org.crosswire.jsword.passage.Verse>, SpeakCommandArray>>(provider, "verseRenderLruCache")
        org.robolectric.util.ReflectionHelpers.setField(provider, "verseRenderLruCache", object : androidx.collection.LruCache<Pair<org.crosswire.jsword.book.sword.SwordBook, org.crosswire.jsword.passage.Verse>, SpeakCommandArray>(100) {
            override fun create(key: Pair<org.crosswire.jsword.book.sword.SwordBook, org.crosswire.jsword.passage.Verse>): SpeakCommandArray {
                entered.countDown()
                check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                return net.bible.test.testSwordContentFacade().getBibleSpeakCommands(net.bible.android.database.bookmarks.SpeakSettings.load(), key.first, key.second)
            }
        })
        try {
            manager.speakBible(book, verse)
            platform.listener!!.onInit(true)
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            manager.shutdown()
            release.countDown()
            joinSpeech()
            val current = org.robolectric.util.ReflectionHelpers.getField<org.crosswire.jsword.passage.Verse>(provider, "_currentVerse")
            assertEquals("Stopped Bible render cannot advance private cursor", verse.ordinal, current.ordinal)
            val states = org.robolectric.util.ReflectionHelpers.getField<Map<String, Any>>(provider, "utteranceState")
            assertTrue("Stopped Bible render cannot resurrect utterance state", states.isEmpty())
            assertFalse(engine.calls.any { it.startsWith("speak:") })
        } finally {
            release.countDown()
            try { joinSpeech() } finally {
                org.robolectric.util.ReflectionHelpers.setField(provider, "verseRenderLruCache", cache)
                speakControl.stop(force = true)
                joinSpeech()
            }
        }
    }

    @Test
    fun legacyCompletionSubmittedBeforeAppendAdvancesExactlyOnce() {
        engine.languages = supportedFinnish()
        configureFreshControl()
        speakControl.speakKeyListLegacy(book, listOf(book.getKey("Gen.1.1")), true)
        platform.listener!!.onInit(true)
        joinSpeech()
        val before = engine.utteranceIds.size
        val last = engine.utteranceIds.last()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val launcher = GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>()
        launcher.launch(SPEAK_KEY) { entered.countDown(); release.await() }
        try {
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            platform.listener!!.onUtteranceDone(last)
            speakControl.speakKeyListLegacy(book, listOf(book.getKey("Gen.2.1")), true)
            release.complete(Unit)
            joinSpeech()
            assertEquals("Retained queue completion must advance exactly one chunk", before + 1, engine.utteranceIds.size)
        } finally {
            release.complete(Unit)
            joinSpeech()
            speakControl.stop(force = true)
            joinSpeech()
        }
    }

    private fun failLiveOperation(operation: String) {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            val states = java.util.Collections.synchronizedList(mutableListOf<SpeakPlaybackState>())
            val sub = SpeakChanges.changes.subscribe { if (it is SpeakChange.State) states += it.speakState }
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                if (operation != "command") {
                    platform.listener!!.onInit(true)
                    joinSpeech()
                }
                val actual = org.robolectric.util.ReflectionHelpers.getField<SpeakTextProvider>(manager, "mSpeakTextProvider")
                val failing = object : SpeakTextProvider by actual {
                    override suspend fun forward(amount: net.bible.android.database.bookmarks.SpeakSettings.RewindAmount?, isValid: () -> Boolean) {
                        if (operation == "movement") throw IllegalStateException("live movement failure")
                        actual.forward(amount, isValid)
                    }
                    override suspend fun getNextSpeakCommand(utteranceId: String, isCurrent: Boolean, isValid: () -> Boolean): SpeakCommand {
                        if (operation == "command") throw IllegalStateException("live command failure")
                        return actual.getNextSpeakCommand(utteranceId, isCurrent, isValid)
                    }
                    override suspend fun getStatusTexts(): Map<Int, String> {
                        if (operation == "status") throw IllegalStateException("live status failure")
                        return actual.getStatusTexts()
                    }
                }
                org.robolectric.util.ReflectionHelpers.setField(manager, "mSpeakTextProvider", failing)
                states.clear()
                if (operation == "command") platform.listener!!.onInit(true)
                else manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.ONE_VERSE)
                joinSpeech()
                assertFalse("A live $operation failure must terminate playback coherently", manager.isSpeaking || manager.isPaused)
                assertEquals(SpeakPlaybackState.SILENT, states.lastOrNull())
                assertTrue(engine.calls.contains("shutdown"))
            } finally {
                sub.cancel()
                speakControl.stop(force = true)
                joinSpeech()
            }
        }
    }

    @Test fun liveMovementFailurePublishesCoherentTerminalState() = failLiveOperation("movement")
    @Test fun liveCommandFailurePublishesCoherentTerminalState() = failLiveOperation("command")
    @Test fun liveStatusFailurePublishesCoherentTerminalState() = failLiveOperation("status")

    @Test
    fun restoredPausedBibleHasStatusWithoutEngineStartup() {
        start(supportedFinnish())
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        manager.pause(true)
        joinSpeech()
        val expectedVerse = (manager.currentlyPlayingKey as org.crosswire.jsword.passage.Verse).getName()
        try {
            val creations = platform.creations
            configureFreshControl()
            val restored = GlobalContext.get().get<TextToSpeechServiceManager>()
            joinSpeech()
            assertTrue(restored.isPaused)
            assertFalse(restored.isSpeaking)
            assertEquals(creations, platform.creations)
            assertTrue("Paused reconstruction must resolve Bible status", restored.getStatusText(BibleSpeakTextProvider.FLAG_SHOW_ALL).isNotBlank())
            assertTrue(restored.getStatusText(BibleSpeakTextProvider.FLAG_SHOW_ALL).startsWith(expectedVerse))
            assertTrue(restored.getStatusText(BibleSpeakTextProvider.FLAG_SHOW_ALL).contains(book.abbreviation))
        } finally { speakControl.stop(force = true); manager.shutdown(); joinSpeech() }
    }

    @Test
    fun restoredPausedGenericHasLazyStatusWithoutEngineStartup() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            epub.hold.set(false)
            manager.speakGeneric(epub.key)
            platform.listener!!.onInit(true)
            joinSpeech()
            manager.pause(true)
            joinSpeech()
            val creations = platform.creations
            configureFreshControl()
            val restored = GlobalContext.get().get<TextToSpeechServiceManager>()
            joinSpeech()
            assertTrue(restored.isPaused)
            assertFalse(restored.isSpeaking)
            assertEquals(creations, platform.creations)
            val status = restored.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL)
            assertTrue("Paused reconstruction must resolve generic percentage", status.contains("%"))
            assertTrue(status.contains(epub.book.abbreviation))
        }
    }

    @Test
    fun currentEngineStartCallbacksCannotMoveBackOrRestartTiming() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            val progress = java.util.Collections.synchronizedList(mutableListOf<SpeakChange.Progress>())
            val sub = SpeakChanges.changes.subscribe { if (it is SpeakChange.Progress) progress += it }
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                val first = engine.utteranceIds[0]
                val second = engine.utteranceIds[1]
                platform.listener!!.onUtteranceStart(first)
                platform.listener!!.onUtteranceStart(second)
                joinSpeech()
                val count = progress.size
                val status = manager.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL)
                val provider = org.robolectric.util.ReflectionHelpers.getField<GeneralSpeakTextProvider>(manager, "generalSpeakTextProvider")
                val timing = org.robolectric.util.ReflectionHelpers.getField<SpeakTiming>(manager, "mSpeakTiming")
                val startedAt = org.robolectric.util.ReflectionHelpers.getField<Long>(timing, "lastSpeakStartTime")
                platform.listener!!.onUtteranceStart(second)
                platform.listener!!.onUtteranceStart(first)
                joinSpeech()
                assertEquals("Duplicate/delayed start cannot republish old progress", count, progress.size)
                assertEquals(status, manager.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL))
                assertEquals(second, org.robolectric.util.ReflectionHelpers.getField<String>(provider, "currentUtteranceId"))
                assertEquals(second, org.robolectric.util.ReflectionHelpers.getField<String>(timing, "lastUtteranceId"))
                assertEquals(startedAt, org.robolectric.util.ReflectionHelpers.getField<Long>(timing, "lastSpeakStartTime"))
            } finally { sub.cancel() }
        }
    }

    @Test
    fun engineInvalidationDuringHeldMovementSuppressesCursorAndProgress() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            val progress = java.util.Collections.synchronizedList(mutableListOf<SpeakChange.Progress>())
            val sub = SpeakChanges.changes.subscribe { if (it is SpeakChange.Progress) progress += it }
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                manager.pause(true)
                val before = (manager.currentlyPlayingKey as net.bible.service.sword.BookAndKey).ordinal!!.start
                progress.clear()
                epub.hold.set(true)
                manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.SMART)
                assertTrue(epub.entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                manager.invalidatePendingStart()
                epub.resume.complete(Unit)
                joinSpeech()
                assertEquals("Invalid engine request cannot publish a calculated cursor", before,
                    (manager.currentlyPlayingKey as net.bible.service.sword.BookAndKey).ordinal!!.start)
                assertTrue("Invalid movement cannot emit stale progress", progress.isEmpty())
            } finally {
                epub.resume.complete(Unit)
                sub.cancel()
            }
        }
    }

    @Test
    fun liveStatusDoesNotBlockTransportAndDoesNotScanTheBook() {
        val manager = prepareGeneric()
        HeldEpub().use { epub ->
            var reader: Thread? = null
            try {
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                platform.listener!!.onUtteranceStart(engine.utteranceIds.first())
                joinSpeech()
                val before = epub.reads.get()
                epub.hold.set(true)
                val returned = java.util.concurrent.CountDownLatch(1)
                var status = ""
                reader = Thread { status = manager.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL); returned.countDown() }.apply { start() }
                assertTrue("Transport status must read a live projection without lazy I/O", returned.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
                assertTrue("Status must retain the live chapter and percentage", status.contains("%"))
                assertTrue(status.contains(epub.book.abbreviation))
                assertEquals("Rendering status must not preload or reread ordinal data", before, epub.reads.get())
                epub.hold.set(false)
                epub.resume.complete(Unit)
                manager.pause(true)
                manager.forward(net.bible.android.database.bookmarks.SpeakSettings.RewindAmount.SMART)
                joinSpeech()
                assertNotEquals("Movement must refresh percentage", status, manager.getStatusText(GeneralSpeakTextProvider.FLAG_SHOW_ALL))
            } finally {
                epub.resume.complete(Unit)
                reader?.join(3000)
            }
        }
    }

    @Test
    fun applicationOwnedBookmarkWriteSurvivesHostCancellation() {
        val manager = prepareGeneric()
        val advanced = GlobalContext.get().get<AdvancedSpeakSettings>()
        val old = advanced.autoBookmark
        val hostJob = kotlinx.coroutines.Job()
        HeldEpub().use { epub ->
            try {
                advanced.autoBookmark = true
                epub.hold.set(false)
                manager.speakGeneric(epub.key)
                platform.listener!!.onInit(true)
                joinSpeech()
                platform.listener!!.onUtteranceStart(engine.utteranceIds.first())
                joinSpeech()
                val writeEntered = java.util.concurrent.CountDownLatch(1)
                val allowWrite = kotlinx.coroutines.CompletableDeferred<Unit>()
                val launcher = GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>()
                launcher.launch(SPEAK_KEY) {
                    writeEntered.countDown()
                    allowWrite.await()
                }
                assertTrue(writeEntered.await(3, java.util.concurrent.TimeUnit.SECONDS))
                try {
                    kotlinx.coroutines.CoroutineScope(hostJob + kotlinx.coroutines.Dispatchers.Unconfined).launch {
                        manager.shutdown()
                    }
                    hostJob.cancel()
                    assertFalse(manager.isSpeaking)
                } finally {
                    allowWrite.complete(Unit)
                }
                joinSpeech()
                val saved = kotlinx.coroutines.runBlocking { bookmarkControl.getGenericBookmarksWithLabel(bookmarkControl.speakLabel()) }
                assertEquals(1, saved.count { it.book?.initials == epub.book.initials })
                assertFalse(manager.isSpeaking)
                val count = engine.utteranceIds.size
                platform.listener!!.onUtteranceDone(engine.utteranceIds.last())
                joinSpeech()
                assertEquals(count, engine.utteranceIds.size)
            } finally {
                hostJob.cancel()
                advanced.autoBookmark = old
            }
        }
    }

    @Test
    fun stopRemainsLiveDuringRealEpubOrdinalLookup() {
        val fixture = net.bible.service.sword.epub.EpubBackendStateTest()
        fixture.setUp()
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val stopped = java.util.concurrent.CountDownLatch(1)
        var stopper: Thread? = null
        var state: net.bible.service.sword.epub.EpubBackendState? = null
        var originalDao: net.bible.android.database.EpubDao? = null
        val launcher = GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>()
        try {
            engine.languages = mapOf(Locale.UK to SpeakLanguageResult.AVAILABLE,
                Locale.ENGLISH to SpeakLanguageResult.AVAILABLE)
            configureFreshControl()
            val epubState = net.bible.service.sword.epub.EpubBackendState(
                fixture.buildEpub("held-speech-ordinal").toOkioPath(), testCoreStrings(),
                net.bible.android.platform.AndroidAppEnvironment(app))
            state = epubState
            val metadata = epubState.getBookMetaData()
            val epub = org.crosswire.jsword.book.sword.SwordGenBook(metadata,
                net.bible.service.sword.epub.EpubBackend(epubState, metadata))
            val key = epubState.keys.first()
            val range = kotlinx.coroutines.runBlocking { epubState.getOrdinalRange(key) }
            val real = org.robolectric.util.ReflectionHelpers.getField<net.bible.android.database.EpubDao>(epubState, "dao")
            originalDao = real
            org.robolectric.util.ReflectionHelpers.setField(epubState, "dao", object : net.bible.android.database.EpubDao by real {
                override suspend fun getFragment(id: Long): net.bible.android.database.EpubFragment? {
                    entered.countDown()
                    check(release.await(3, java.util.concurrent.TimeUnit.SECONDS))
                    return real.getFragment(id)
                }
            })
            val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
            manager.speakGeneric(net.bible.service.sword.BookAndKey(key, epub,
                net.bible.android.control.page.OrdinalRange(range.first)))
            platform.listener!!.onInit(true)
            assertTrue("Speech must reach the real lazy EPUB lookup", entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            stopper = Thread { manager.shutdown(); stopped.countDown() }.apply { start() }
            assertTrue("Stop must not wait for an ordinal read holding the manager monitor",
                stopped.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            try {
                stopper?.join(3000)
                kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(4000) { launcher.join(SPEAK_KEY) } }
            } finally {
                try {
                    if (state != null && originalDao != null) {
                        org.robolectric.util.ReflectionHelpers.setField(state, "dao", originalDao)
                    }
                    speakControl.stop(force = true)
                } finally {
                    try { state?.close() } finally { fixture.tearDown() }
                }
            }
        }
        assertFalse("Released obsolete lookup must not enqueue audio", engine.calls.any { it.startsWith("speak:") })
    }

    @Test fun allLocalesUnsupportedShowsMessageAndStops() {
        val (engine, notifier) = start(emptyMap())
        assertEquals(listOf(SpeakSetupProblem.LANGUAGE_NOT_AVAILABLE), platform.problems)
        assertTrue("a device setup problem is not a generic error", notifier.errors.isEmpty())
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
            assertTrue(platform.problems.isEmpty())
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
        kotlinx.coroutines.runBlocking { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(net.bible.android.control.speak.SPEAK_KEY) }
        platform.preferredEnginePackage = "changed.engine"
        speakControl.speakBible(book, verse)
        assertEquals(2, platform.creations)
        assertTrue(engine.calls.contains("shutdown"))
        platform.listener!!.onInit(true)
        kotlinx.coroutines.runBlocking { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(net.bible.android.control.speak.SPEAK_KEY) }
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
        assertEquals(listOf(SpeakSetupProblem.AUDIO_FOCUS_DENIED), platform.problems)
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
        kotlinx.coroutines.runBlocking { GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(net.bible.android.control.speak.SPEAK_KEY) }
        assertTrue(speakControl.isSpeaking)
        assertFalse(speakControl.isPaused)
        assertTrue(engine.calls.count { it.startsWith("speak:") } > queuedBefore)
    }

    @Test fun initFailureStops() {
        val (engine, notifier) = start(emptyMap(), success = false)
        assertEquals(listOf(SpeakSetupProblem.ENGINE_NOT_AVAILABLE), platform.problems)
        assertTrue("a device setup problem is not a generic error", notifier.errors.isEmpty())
        assertTrue(speakControl.isStopped)
        assertTrue(engine.calls.contains("shutdown"))
    }

    @Test
    fun latestRequestWaitsForActualEngineSuccess() {
        verifyPendingEngine(true)
    }

    @Test
    fun latestRequestReceivesActualEngineFailure() {
        verifyPendingEngine(false)
    }

    private fun verifyPendingEngine(success: Boolean) {
        engine.languages = supportedFinnish()
        configureFreshControl()
        val verse = (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        speakControl.speakBible(book, verse)
        val launcher = GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>()
        kotlinx.coroutines.runBlocking { launcher.join(net.bible.android.control.speak.SPEAK_KEY) }
        assertFalse("No synthetic success before the actual callback", speakControl.isSpeaking)
        platform.listener!!.onInit(success)
        kotlinx.coroutines.runBlocking { launcher.join(net.bible.android.control.speak.SPEAK_KEY) }
        assertEquals(success, speakControl.isSpeaking)
    }

    @Test
    fun publicStopBeforeInitRejectsLateSuccess() { verifyStoppedPendingEngine(true) }

    @Test
    fun publicStopBeforeInitRejectsLateFailure() { verifyStoppedPendingEngine(false) }

    private fun verifyStoppedPendingEngine(success: Boolean) {
        engine.languages = supportedFinnish()
        configureFreshControl()
        val verse = (book.getKey("Rom.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        val callback = platform.listener!!
        speakControl.stop()
        val errorsBefore = notifier.errors.toList()
        val problemsBefore = platform.problems.toList()
        callback.onInit(success)
        kotlinx.coroutines.runBlocking {
            GlobalContext.get().get<net.bible.sharedcore.platform.OrderedLauncher>().join(net.bible.android.control.speak.SPEAK_KEY)
        }
        assertTrue("Canceled request must remain stopped", speakControl.isStopped)
        assertFalse("Canceled callback must not queue text", engine.calls.any { it.startsWith("speak:") })
        assertEquals("Canceled callback must not report a stale error", errorsBefore, notifier.errors)
        assertEquals("Canceled callback must not report a stale problem", problemsBefore, platform.problems)
    }
}
