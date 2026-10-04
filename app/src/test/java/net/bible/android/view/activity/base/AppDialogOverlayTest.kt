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

package net.bible.android.view.activity.base

import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.resetComposeUiDispatcher
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class AppDialogOverlayTest {
    private val controllers = mutableListOf<ActivityController<*>>()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)

    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val dialogReset = AppDialogControllerResetRule()

    @Before
    fun setUp() {
        resetComposeUiDispatcher()
        dialogs.cancelAll()
    }

    @After
    fun tearDown() {
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        dialogs.cancelAll()
    }

    private fun calculator() =
        Robolectric.buildActivity(CalculatorComposeActivity::class.java).also { controllers += it }.setup()

    @Test
    fun theMostRecentlyResumedHostOwnsRendering() {
        val a = calculator()
        val b = calculator()
        assertSame(b.get(), AppDialogRendering.owner.value)
        b.pause().stop()
        a.pause().resume()
        assertSame(a.get(), AppDialogRendering.owner.value)
    }

    @Test
    fun pauseAloneKeepsOwnership() { // plan correction 3
        val a = calculator()
        a.pause()
        assertSame(a.get(), AppDialogRendering.owner.value)
    }

    @Test
    fun aNonOwnersStopChangesNothing() {
        val a = calculator()
        val b = calculator()
        a.pause().stop()
        assertSame(b.get(), AppDialogRendering.owner.value)
    }

    @Test
    fun theOwnerRendersTheQueueHead() {
        calculator()
        dialogs.post(AppDialogRequest.Message(null, "Rendered on the calculator", "OK"))
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertTrue(dialogTextVisible("Rendered on the calculator"))
    }

    @Test
    fun recreateWithAPendingConfirmAnswersTheCallerExactlyOnce() {
        val a = calculator()
        var answers = 0
        val result = GlobalScope.async(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
            dialogs.await(AppDialogRequest.Confirm("Sure?", null, "OK", "Cancel")).also { answers++ }
        }
        a.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertTrue(dialogTextVisible("Sure?")) // re-shown after recreation
        val id = dialogs.pending.value!!.id
        dialogs.respond(id, AppDialogResult.Ok)
        dialogs.respond(id, AppDialogResult.Ok)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AppDialogResult.Ok, result.getCompleted())
        assertEquals(1, answers)
    }

    /**
     * StartupActivity/ErrorActivity's mounting path: a bare `ComponentActivity` with no Compose
     * content of its own. Stands in for `startupActivityRendersAQueuedRequest`
     * (task-5-brief.md Step 2) — StartupActivity's own boot path needs a running database and the
     * real welcome/reading flow, which this scoped test does not set up; this exercises the same
     * [mountAppDialogOverlay] entry point those two Activities call.
     */
    private class DialogOverlayHostActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            mountAppDialogOverlay()
        }
    }

    @Test
    fun mountAppDialogOverlayRendersAQueuedRequestOnAPlainActivity() {
        dialogs.post(AppDialogRequest.Message(null, "Shown via mountAppDialogOverlay", "OK"))
        Robolectric.buildActivity(DialogOverlayHostActivity::class.java).also { controllers += it }.setup()
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertTrue(dialogTextVisible("Shown via mountAppDialogOverlay"))
    }

    /** True when a compose root (including dialog windows) currently shows [text]. */
    private fun dialogTextVisible(text: String): Boolean =
        runCatching { compose.onNodeWithText(text).assertExists() }.isSuccess

    /**
     * Task 9 step 4: `checkPoorTranslations` runs in `StartupActivity` in production (`UsableBible.kt`),
     * which Task 5 mounted the overlay on -- but `StartupActivity` cannot be built under Robolectric
     * (its boot path needs DB/Sword fixtures no test sets up). This exercises the same
     * `AppDialogController`-through-overlay path on the calculator host already used above; the
     * `StartupActivity`-hosted case is covered by Task 11's emulator smoke instead.
     */
    @Test
    fun checkPoorTranslationsRequestIsRenderedOnAnOverlayHost() {
        val originalLocale = Locale.getDefault()
        try {
            // The UI locale comes from the locale_pref preference (ActivityBase.attachBaseContext resets any bare default).
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(
                androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            ).edit().putString("locale_pref", "xx").commit()
            val activity = calculator().get()
            assertEquals("xx", Locale.getDefault().toLanguageTag())
            GlobalScope.async(Dispatchers.Main, start = CoroutineStart.UNDISPATCHED) {
                CommonUtils.checkPoorTranslations(activity)
            }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            // A literal fragment of R.string.incomplete_translation that survives its %1$s/%2$s/%3$s
            // substitutions verbatim.
            val found = runCatching {
                compose.onNodeWithText("is entirely developed by volunteers", substring = true).assertExists()
            }.isSuccess
            assertTrue(found)
        } finally {
            Locale.setDefault(originalLocale)
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(
                androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            ).edit().remove("locale_pref").commit()
        }
    }
}
