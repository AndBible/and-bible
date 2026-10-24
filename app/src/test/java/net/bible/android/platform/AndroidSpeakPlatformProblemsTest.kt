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
package net.bible.android.platform

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import android.provider.Settings
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.UserMessage
import net.bible.android.control.event.UserMessages
import net.bible.android.control.speak.SpeakSetupProblem
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * A device whose text-to-speech cannot speak must say why and lead to the system TTS settings,
 * not show the generic "An error has occurred" (support case: Galaxy A16, no working engine).
 * Which problem is reported when is covered by `TextToSpeechServiceManagerEngineTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fi", application = TestBibleApplication::class, sdk = [TEST_SDK])
class AndroidSpeakPlatformProblemsTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    private lateinit var app: TestBibleApplication
    private lateinit var platform: AndroidSpeakPlatform
    private lateinit var activityController: ActivityController<CalculatorComposeActivity>
    private val dialogs: AppDialogController get() = GlobalContext.get().get()

    @Before
    fun setUp() {
        app = BibleApplication.application as TestBibleApplication
        platform = AndroidSpeakPlatform(app)
        activityController = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        CurrentActivityHolder.activate(activityController.get())
    }

    @After
    fun tearDown() {
        CurrentActivityHolder.deactivate(activityController.get())
        runCatching { activityController.pause().stop().destroy() }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun pendingMessage() = dialogs.pending.value!!.request as AppDialogRequest.Message

    private fun answer(result: AppDialogResult) {
        dialogs.respond(dialogs.pending.value!!.id, result)
        idle()
    }

    private fun report(problem: SpeakSetupProblem) {
        platform.reportProblem(problem)
        idle()
    }

    @Test
    fun engineProblemExplainsAndOffersTtsSettings() {
        report(SpeakSetupProblem.ENGINE_NOT_AVAILABLE)

        val request = pendingMessage()
        assertEquals(app.getString(R.string.tts_engine_not_available), request.message)
        assertEquals(app.getString(R.string.open_settings), request.confirmText)
        assertNull("the device's TTS setup is not an AndBible bug: no report button", request.neutralText)

        answer(AppDialogResult.Ok)
        assertEquals("com.android.settings.TTS_SETTINGS", shadowOf(activityController.get()).nextStartedActivity.action)
    }

    @Test
    fun dismissingTheEngineMessageOpensNothing() {
        report(SpeakSetupProblem.ENGINE_NOT_AVAILABLE)

        answer(AppDialogResult.Cancel)
        assertNull(shadowOf(activityController.get()).nextStartedActivity)
    }

    @Test
    fun withoutATtsSettingsScreenTheGeneralSettingsOpen() {
        val shadowApp = shadowOf(app)
        shadowApp.checkActivities(true)
        try {
            report(SpeakSetupProblem.ENGINE_NOT_AVAILABLE)
            shadowOf(app.packageManager).addResolveInfoForIntent(
                Intent(Settings.ACTION_SETTINGS),
                ResolveInfo().apply {
                    activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "Settings" }
                },
            )
            answer(AppDialogResult.Ok)
            assertEquals(Settings.ACTION_SETTINGS, shadowOf(activityController.get()).nextStartedActivity.action)
        } finally {
            shadowApp.checkActivities(false)
        }
    }

    @Test
    fun unsupportedLanguageAlsoOffersTtsSettings() {
        report(SpeakSetupProblem.LANGUAGE_NOT_AVAILABLE)

        val request = pendingMessage()
        assertEquals(app.getString(R.string.tts_lang_not_available), request.message)
        assertEquals(app.getString(R.string.open_settings), request.confirmText)

        answer(AppDialogResult.Ok)
        assertEquals("com.android.settings.TTS_SETTINGS", shadowOf(activityController.get()).nextStartedActivity.action)
    }

    @Test
    fun deniedAudioFocusIsToldNotSilent() {
        val messages = mutableListOf<UserMessage>()
        val subscription = UserMessages.messages.subscribe { messages += it }
        try {
            report(SpeakSetupProblem.AUDIO_FOCUS_DENIED)
        } finally {
            subscription.cancel()
        }

        assertEquals(listOf(UserMessage.Toast(null, R.string.tts_audio_focus_denied, long = true)), messages)
        assertNull("a toast, not a dialog", dialogs.pending.value)
    }
}
