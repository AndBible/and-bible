package net.bible.service.device.speak

import kotlinx.coroutines.runBlocking
import net.bible.android.control.speak.SPEAK_KEY
import net.bible.android.control.speak.load
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

    private fun suspendedResume(loop: Boolean = false, supersede: (TextToSpeechServiceManager) -> Unit) {
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        if (loop) {
            net.bible.android.database.bookmarks.SpeakSettings().apply { sleepTimer = 60 }.save()
            speakControl.speakMemorizationLoop(book, verse, verse)
        } else {
            speakControl.speakBible(book, verse)
        }
        runBlocking { launcher.join(SPEAK_KEY) }
        speakControl.pause()
        runBlocking { launcher.join(SPEAK_KEY) }
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val field = TextToSpeechServiceManager::class.java.getDeclaredField("mSpeakTextProvider").apply { isAccessible = true }
        val real = field.get(manager) as SpeakTextProvider
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        var applied = 0
        val suspended = object : SpeakTextProvider by real {
            override suspend fun loadBookmarkState() { entered.complete(Unit); release.await() }
            override fun prepareForStartSpeaking() { applied++ }
        }
        field.set(manager, suspended)
        try {
            speakControl.continueAfterPause()
            runBlocking { entered.await() }
            supersede(manager)
        } finally {
            release.complete(Unit)
            runBlocking { launcher.join(SPEAK_KEY) }
        }
        assertEquals("Superseded load must never apply/start", 0, applied)
    }

    @Test
    fun stopDuringSuspendedLoopLoadRestoresSettings() {
        try {
            suspendedResume(loop = true) {
                speakControl.stop()
                org.junit.Assert.assertFalse("Pending loop resume stop must restore settings",
                    net.bible.android.database.bookmarks.SpeakSettings.load().playbackSettings.isMemorizationLoop)
                org.junit.Assert.assertFalse(speakControl.isMemorizationLoop)
            }
        } finally {
            speakControl.stop(force = true)
            net.bible.android.database.bookmarks.SpeakSettings().save()
        }
    }

    @Test
    fun stopDuringSuspendedLoopLoadRemovesSleepTimer() {
        try {
            suspendedResume(loop = true) {
                org.junit.Assert.assertTrue(speakControl.sleepTimerActive())
                speakControl.stop()
                org.junit.Assert.assertFalse("Pending loop resume stop must cancel timer", speakControl.sleepTimerActive())
            }
        } finally {
            speakControl.stop(force = true)
            net.bible.android.database.bookmarks.SpeakSettings().save()
        }
    }

    @Test
    fun transportStopDuringSuspendedLoadDoesNotResurrectSpeech() {
        val transport = GlobalContext.get().get<net.bible.android.control.speak.SpeakTransportServiceImpl>()
        suspendedResume {
            transport.setTransportVisible(true)
            transport.stop()
            org.junit.Assert.assertFalse("Stopped transport keeps its hide behavior", transport.state.value.visible)
        }
        org.junit.Assert.assertFalse(GlobalContext.get().get<TextToSpeechServiceManager>().isSpeaking)
    }

    @Test
    fun twoSettingsChangesInMemorizationLoopDoNotCreatePhantomBookmark() {
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        GlobalContext.get().get<net.bible.android.control.speak.AdvancedSpeakSettings>().autoBookmark = true
        net.bible.android.database.bookmarks.SpeakSettings().save()
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        try {
            speakControl.speakMemorizationLoop(book, verse, verse)
            runBlocking { launcher.join(SPEAK_KEY) }
            for (speed in listOf(173, 183)) {
                val settings = net.bible.android.database.bookmarks.SpeakSettings.load()
                settings.playbackSettings = settings.playbackSettings.copy(speed = speed)
                settings.save(updateBookmark = true)
                runBlocking { launcher.join(SPEAK_KEY) }
            }
            assertEquals("Loop settings changes must not insert an intentionally unsaved bookmark", 0,
                runBlocking { bookmarkControl.allBibleBookmarks() }.size)
        } finally {
            speakControl.stop(force = true)
            runBlocking { launcher.join(SPEAK_KEY) }
            net.bible.android.database.bookmarks.SpeakSettings().save()
        }
    }

    @Test
    fun stopDuringSuspendedLoadDoesNotResurrectSpeech() {
        suspendedResume { speakControl.stop() }
        org.junit.Assert.assertFalse(GlobalContext.get().get<TextToSpeechServiceManager>().isSpeaking)
    }

    @Test
    fun providerSwitchDuringSuspendedLoadDoesNotApplyOldProvider() {
        suspendedResume { it.speakTextLegacy(book, listOf(book.getKey("Gen.2.1")), false) }
    }

    @Test
    fun stopThenNewStartDuringSuspendedLoadDoesNotApplyStoppedRequest() {
        suspendedResume {
            it.shutdown()
            val verse = (book.getKey("Gen.3.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
            it.speakBible(book, verse)
        }
    }

    @Test
    fun settingsSaveReinitializesChangedEnginePreference() {
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        runBlocking { launcher.join(SPEAK_KEY) }
        val before = fakePlatform.creations
        fakePlatform.preferredEnginePackage = "changed.engine"
        val settings = net.bible.android.database.bookmarks.SpeakSettings.load()
        settings.playbackSettings = settings.playbackSettings.copy(speed = settings.playbackSettings.speed + 10)
        settings.save()
        runBlocking { launcher.join(SPEAK_KEY) }
        assertEquals("Settings resume must honor changed engine preference", before + 1, fakePlatform.creations)
        org.junit.Assert.assertTrue(speakControl.isSpeaking)
    }

    @Test
    fun pauseThenSettingsSaveBeforeInsertCompletesUpdatesExistingId() {
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        GlobalContext.get().get<net.bible.android.control.speak.AdvancedSpeakSettings>().autoBookmark = true
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        runBlocking { launcher.join(SPEAK_KEY) }
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        launcher.launch(SPEAK_KEY) { entered.complete(Unit); release.await() }
        runBlocking { entered.await() }
        try {
            speakControl.pause()
            val settings = net.bible.android.database.bookmarks.SpeakSettings.load()
            settings.playbackSettings = settings.playbackSettings.copy(speed = 183)
            settings.save(updateBookmark = true)
        } finally {
            release.complete(Unit)
            runBlocking { launcher.join(SPEAK_KEY) }
        }
        assertEquals(183, runBlocking { bookmarkControl.speakBookmarkForVerse(verse) }!!.playbackSettings!!.speed)
    }

    @Test
    fun shutdownWaitsForGuardedApplyThenStopsSpeech() {
        val launcher = GlobalContext.get().get<OrderedLauncher>()
        val verse = (book.getKey("Gen.1.1") as org.crosswire.jsword.passage.RangedPassage).getVerseAt(0)
        speakControl.speakBible(book, verse)
        runBlocking { launcher.join(SPEAK_KEY) }
        speakControl.pause()
        runBlocking { launcher.join(SPEAK_KEY) }
        val manager = GlobalContext.get().get<TextToSpeechServiceManager>()
        val field = TextToSpeechServiceManager::class.java.getDeclaredField("mSpeakTextProvider").apply { isAccessible = true }
        val real = field.get(manager) as SpeakTextProvider
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val stopped = java.util.concurrent.CountDownLatch(1)
        field.set(manager, object : SpeakTextProvider by real {
            override fun prepareForStartSpeaking() {
                entered.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                real.prepareForStartSpeaking()
            }
        })
        val worker = Thread { manager.shutdown(); stopped.countDown() }
        try {
            manager.continueAfterPause()
            org.junit.Assert.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            worker.start()
            org.junit.Assert.assertFalse("Shutdown must serialize with guarded apply", stopped.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            release.countDown()
            worker.join(5000)
            runBlocking { launcher.join(SPEAK_KEY) }
        }
        org.junit.Assert.assertTrue(stopped.count == 0L)
        org.junit.Assert.assertFalse(manager.isSpeaking)
    }
}
