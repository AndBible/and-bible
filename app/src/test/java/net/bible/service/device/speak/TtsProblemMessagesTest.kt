/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.service.device.speak

import android.content.Context
import android.media.AudioManager
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.RangedPassage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * A device whose text-to-speech cannot speak must say why and lead to the system TTS settings,
 * not show the generic "An error has occurred" (support case: Galaxy A16, no working engine).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fi", application = TestBibleApplication::class, sdk = [TEST_SDK])
class TtsProblemMessagesTest {
    private lateinit var app: TestBibleApplication
    private lateinit var speakControl: SpeakControl
    private lateinit var ttsManager: TextToSpeechServiceManager
    private lateinit var activityController: ActivityController<CalculatorComposeActivity>
    private val dialogs: AppDialogController get() = GlobalContext.get().get()

    @Before
    fun setUp() {
        app = BibleApplication.application as TestBibleApplication
        val koin = GlobalContext.get()
        // Koin is stopped after every test (as in SpeakIntegrationTestBase), so the dialog queue
        // is cleared here and in tearDown rather than by AppDialogControllerResetRule.
        dialogs.cancelAll()
        speakControl = koin.get()
        ttsManager = koin.get()
        val windowControl: WindowControl = koin.get()
        windowControl.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository.initialize()
        activityController = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        CurrentActivityHolder.activate(activityController.get())
    }

    @After
    fun tearDown() {
        runCatching { speakControl.stop(force = true) }
        CurrentActivityHolder.deactivate(activityController.get())
        runCatching { activityController.pause().stop().destroy() }
        dialogs.cancelAll()
        DatabaseResetter.resetDatabase()
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun pendingMessage() = dialogs.pending.value!!.request as AppDialogRequest.Message

    private fun answer(result: AppDialogResult) {
        dialogs.respond(dialogs.pending.value!!.id, result)
        idle()
    }

    private fun speakRomans() {
        val book = Books.installed().getBook("FinRK") as SwordBook
        speakControl.speakBible(book, (book.getKey("Rom.1.1") as RangedPassage).getVerseAt(0))
        idle()
    }

    /** Prepares a speak request (as a real "Speak" tap does) whose engine then fails to start. */
    private fun failEngineInit() {
        speakControl.setupMockedTts()
        speakRomans()
        speakControl.stop(force = true)
        ttsManager.onTtsInit(TextToSpeech.ERROR)
    }

    @Test(timeout = 60000)
    fun engineInitFailureExplainsAndOffersTtsSettings() {
        failEngineInit()
        idle()

        val request = pendingMessage()
        assertEquals(app.getString(R.string.tts_engine_not_available), request.message)
        assertEquals(app.getString(R.string.open_settings), request.confirmText)
        assertNull("the device's TTS setup is not an AndBible bug: no report button", request.neutralText)

        answer(AppDialogResult.Ok)
        val started = shadowOf(activityController.get()).nextStartedActivity
        assertEquals("com.android.settings.TTS_SETTINGS", started.action)
    }

    @Test(timeout = 60000)
    fun dismissingTheEngineMessageOpensNothing() {
        failEngineInit()
        idle()

        answer(AppDialogResult.Cancel)
        assertNull(shadowOf(activityController.get()).nextStartedActivity)
    }

    @Test(timeout = 60000)
    fun withoutATtsSettingsScreenTheGeneralSettingsOpen() {
        val shadowApp = shadowOf(app)
        shadowApp.checkActivities(true)
        try {
            failEngineInit()
            idle()
            shadowOf(app.packageManager).addResolveInfoForIntent(
                android.content.Intent(Settings.ACTION_SETTINGS),
                android.content.pm.ResolveInfo().apply {
                    activityInfo = android.content.pm.ActivityInfo().apply {
                        packageName = "com.android.settings"; name = "Settings"
                    }
                },
            )
            answer(AppDialogResult.Ok)
            assertEquals(Settings.ACTION_SETTINGS, shadowOf(activityController.get()).nextStartedActivity.action)
        } finally {
            shadowApp.checkActivities(false)
        }
    }

    @Test(timeout = 60000)
    fun unsupportedLanguageAlsoOffersTtsSettings() {
        // Robolectric's TextToSpeech supports no language unless one is added, so init succeeds
        // and every locale in the preference list is refused.
        speakRomans()

        val request = pendingMessage()
        assertEquals(app.getString(R.string.tts_lang_not_available), request.message)
        assertEquals(app.getString(R.string.open_settings), request.confirmText)

        answer(AppDialogResult.Ok)
        assertEquals(
            "com.android.settings.TTS_SETTINGS",
            shadowOf(activityController.get()).nextStartedActivity.action,
        )
    }

    @Test(timeout = 60000)
    fun deniedAudioFocusIsToldNotSilent() {
        speakControl.setupMockedTts()
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        shadowOf(am).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)

        speakRomans()

        assertEquals(app.getString(R.string.tts_audio_focus_denied), ShadowToast.getTextOfLatestToast())
    }
}
