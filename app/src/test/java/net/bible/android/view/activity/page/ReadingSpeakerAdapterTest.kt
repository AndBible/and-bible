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


package net.bible.android.view.activity.page

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.AdvancedSpeakSettings
import net.bible.android.control.speak.SpeakChanges
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettingsChanges
import net.bible.service.device.speak.FakeSpeakEngine
import net.bible.service.device.speak.FakeSpeakPlatform
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.service.download.FakeBookFactory
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.reading.bridge.asReadingSpeaker
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * The [SpeakControl] adapter the bridge speaks through. A speak call that interrupts a running one
 * pauses with `willContinueAfterThis = true, toast = false`: the sleep timer must keep running (a
 * plain `pause()` stops it) and no "paused" toast is shown. SpeakControl is final and the sleep timer
 * is its observable difference, so this pins the arguments through the timer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingSpeakerAdapterTest {
    private fun withControl(block: suspend kotlinx.coroutines.test.TestScope.(SpeakControl, List<String>) -> Unit) = runTest {
        val strings = testCoreStrings()
        val toasts = mutableListOf<String>()
        val notifier = object : UserNotifier {
            override fun showInfo(message: String, long: Boolean) { toasts += message }
            override fun showError(message: String, cause: Throwable?) { error(message) }
        }
        val scope = AppCoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, _ -> })
        try {
            val settings = testAppSettings()
            val control = SpeakControl(
                lazy<TextToSpeechServiceManager> { throw IllegalStateException("TTS manager reached") },
                mock(WindowControl::class.java),
                FakeSpeakPlatform(FakeSpeakEngine(emptyMap())), notifier, strings, settings,
                scope, AdvancedSpeakSettings(settings), OrderedLauncher(scope), FakeBookFactory(strings),
            )
            ReflectionHelpers.callInstanceMethod<Unit>(
                control, "enableSleepTimer", ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 1))
            assertTrue("sanity: the sleep timer is running", control.sleepTimerActive())
            toasts.clear()
            block(control, toasts)
        } finally {
            scope.cancel()
            SpeakChanges.resetSubscribersForTest()
            SpeakSettingsChanges.resetSubscribersForTest()
        }
    }

    @Test fun pauseForNextSpeechKeepsTheSleepTimerRunning() = withControl { control, _ ->
        runCatching { control.asReadingSpeaker().pauseForNextSpeech() }
        assertTrue("willContinueAfterThis = true does not stop the timer", control.sleepTimerActive())
    }

    @Test fun aPlainPauseStopsTheSleepTimer() = withControl { control, _ ->
        runCatching { control.pause() }
        assertFalse("the control: pause() stops the timer, so the test above can fail", control.sleepTimerActive())
    }
}
