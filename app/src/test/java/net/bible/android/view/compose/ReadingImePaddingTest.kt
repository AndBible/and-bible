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

package net.bible.android.view.compose

import android.os.Looper
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.base.firstTime
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * F59. The nav host computes the keyboard shrink and hands it to a no-op
 * (`NavHostComposeActivity.kt`, `applyImeBottomPadding = { /* Ruling C */ }`), and nothing anywhere
 * applies `WindowInsets.ime` at the content -- `imePadding()` has never existed in this repository. So
 * the reading content sits UNDER the keyboard on every API level.
 *
 * The T9 walk (spec §3.1.1) then measured that "one mode on every API level" is unsafe: below API 30
 * `WindowInsetsCompat.Type.ime()` is synthesised from the system-window inset, which only carries the
 * keyboard while the framework is actually resizing the window for it, so `ADJUST_NOTHING` there would
 * remove the framework's resize with no replacement. Phase B therefore narrows to API 30+:
 *
 *  - [theReadingScreenShrinksByTheImePadding] measures a real layout bound, API-independent. It fails
 *    today with a compile error (`ReadingViewScreen` has no such parameter), which is the honest
 *    pre-state.
 *  - [theNavHostSinkReceivesTheShrinkOnApi30] proves the value actually ARRIVES on a >=30 device, where
 *    the listener is now ungated.
 *  - [theSinkStaysZeroBelowApi30] is the narrowing's regression guard: below 30 the framework still
 *    resizes under `adjustResize`, so the ledger must NOT also be fed -- that would double-shrink the
 *    content. It is a guard for the NARROWING, not for F59's original defect: it does not fail on the
 *    pre-fix tree (there the listener is gated off below API 35, so it is already off at API 28, and
 *    `imeBottomPaddingPx` is already permanently 0 for the same reason `theNavHostSinkReceivesTheShrinkOnApi30`
 *    fails there). Proved live instead by temporarily lowering `NavHostComposeActivity.onCreate`'s
 *    listener gate to `Build.VERSION_CODES.M` (23) and observing this test fail with
 *    `imeBottomPaddingPx.value == 300`; restored after.
 *  - [theSoftInputModeIsAdjustNothingOnApi30]/[theSoftInputModeIsAdjustResizeBelowApi30] pin
 *    `setSoftKeyboardMode()`'s (and `onCreate`'s) per-API split. The `sdk = [30]` one FAILS on the
 *    pre-fix tree, where every API level (including 30) is left at the manifest's `adjustResize`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class ReadingImePaddingTest {

    @get:Rule val compose = createComposeRule()

    /**
     * Every controller [host] builds is kept here so [tearDown] can `.close()` it -- the
     * `hostControllers` idiom `ReadingHostBackChainTest`/`SelfLaunchAwaitIntentTest` use, for the
     * same reason: an undestroyed host can leak state into the next test in this JVM.
     */
    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    /**
     * `firstTime` is pinned false first, for the same reason `SelfLaunchAwaitIntentTest.host()`
     * does: it is a file-level `var` in `ActivityBase.kt` that Robolectric does not reset between
     * test METHODS in this JVM, and `ActivityBase.fixNightMode()` arms a delayed `recreate()` while
     * it is true.
     */
    private fun hostController(): ActivityController<NavHostComposeActivity> {
        firstTime = false
        return Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(
                ApplicationProvider.getApplicationContext(),
                NavRoutes.READING,
            ),
        ).also { hostControllers += it }.setup()
    }

    private fun host(): NavHostComposeActivity = hostController().get()

    @After
    fun tearDown() {
        hostControllers.forEach { it.close() }
        hostControllers.clear()
    }

    // ——— fixtures, copied from ReadingViewScreenGoldenTest/ReadingSplitGoldenTest rather than
    // invented (same drawables, same no-op callbacks, same single-window layout) ————————————————

    private fun singleWindowLayout() = WindowLayoutState(
        windows = listOf(
            WindowSnapshot(
                id = "A", state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
                isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
            ),
        ),
        activeWindowId = "A", maximizedWindowId = null, reverseSplitMode = false,
        restoreButtonsVisible = true,
    )

    @Composable
    private fun stubToolbarIcons() = ReadingToolbarIcons(
        home = painterResource(R.drawable.ic_menu),
        search = painterResource(R.drawable.ic_search_24dp),
        speak = painterResource(R.drawable.ic_baseline_headphones_24),
        strongs = painterResource(R.drawable.ic_strongs_hebrew),
        bible = painterResource(R.drawable.ic_bible_24dp),
        commentary = painterResource(R.drawable.ic_commentary),
        workspace = painterResource(R.drawable.ic_workspace_solid_24dp),
        overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
        sync = painterResource(R.drawable.ic_syncdb_24dp),
    )

    private fun stubToolbarCallbacks() = ReadingToolbarCallbacks(
        onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
        onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
        onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
        onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
    )

    @Test
    fun theReadingScreenShrinksByTheImePadding() {
        // A single `setContent`, with the padding as mutable state re-measured after a mutation,
        // rather than the brief's two separate `compose.setContent` calls: this compose-testing
        // library version throws "has already set content" on a second call within one test (the
        // brief's literal code is stale for it -- common-rules' "adapt minimally, keep the intent").
        // The measurement is the same either way: the SAME composition, before and after the padding
        // changes.
        val imeBottomPaddingState = mutableStateOf(0.dp)
        compose.setContent {
            // ReadingToolbar reads LocalStrings, which defaults to error(...) -- every real host
            // wraps its content in this (AppLocals.kt's kdoc), so a bare setContent crashes here,
            // same as it would in production without it.
            ProvideAppLocals {
                Box(Modifier.fillMaxSize().testTag("root")) {
                    ReadingViewScreen(
                        layout = singleWindowLayout(),
                        toolbar = ToolbarState.EMPTY,
                        toolbarIcons = stubToolbarIcons(),
                        toolbarCallbacks = stubToolbarCallbacks(),
                        fullScreen = false,
                        onWindowActivated = {},
                        onSeparatorCommitted = { _, _, _, _ -> },
                        pane = { Box(Modifier.fillMaxSize().testTag("pane")) },
                        imeBottomPadding = imeBottomPaddingState.value,
                    )
                }
            }
        }
        val withoutPadding = compose.onNodeWithTag("pane").fetchSemanticsNode().size.height.toFloat()

        imeBottomPaddingState.value = 200.dp
        compose.waitForIdle()
        val withPadding = compose.onNodeWithTag("pane").fetchSemanticsNode().size.height.toFloat()

        assertTrue(
            "the reading content must shrink by the IME padding -- 200.dp of keyboard must take 200.dp " +
                "of height from the pane (was $withoutPadding, now $withPadding)",
            withoutPadding - withPadding > 0f,
        )
    }

    private fun dispatchImeInsets(activity: NavHostComposeActivity, imeBottomPx: Int) {
        val root: ViewGroup = activity.findViewById(android.R.id.content)
        ViewCompat.dispatchApplyWindowInsets(
            root,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 80, 0, 39))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottomPx))
                .build(),
        )
    }

    @Config(sdk = [30])
    @Test
    fun theNavHostSinkReceivesTheShrinkOnApi30() {
        val activity = host()
        dispatchImeInsets(activity, 300)

        assertEquals(
            "on API 30+ the insets listener is now ungated (NavHostComposeActivity.kt's onCreate, " +
                "moved from >= VANILLA_ICE_CREAM to >= R on the T9 walk's spec-§3.1.1 measurement), " +
                "so the sink must receive the shrink and the content must stop sitting under the keyboard",
            300,
            activity.imeBottomPaddingPx.value,
        )
    }

    /**
     * The narrowing's regression guard (spec §3.1.1's negative API-28 measurement): below API 30 the
     * framework still resizes the window under `adjustResize`, so the sink must stay at 0 even though
     * the SAME insets dispatch on a >=30 device (the test above) feeds it 300 -- otherwise the
     * framework's own resize and this host's padding would both apply, the double shrink spec §3 warns
     * about.
     *
     * **This does not fail on the pre-fix tree.** Pre-fix the listener is gated off below API 35 (so
     * also at 28), for the same "framework already resizes it" reason this narrowing keeps below 30 --
     * the sink is 0 there both before and after this task's fix. It was proved live during development
     * (fix round 1) by temporarily lowering `NavHostComposeActivity.appOwnsImeInsetFromSdk` to
     * `Build.VERSION_CODES.M` (23) and confirming this test then fails -- `imeBottomPaddingPx.value`
     * came back **39, not 300**: at sdk 28 `WindowInsetsCompat.Type.ime()` is ITSELF synthesised from
     * the system-window inset (spec §3.1.1's own finding), so the listener reads back the system-bars
     * bottom (39, this test's own `dispatchImeInsets` helper) rather than the 300 explicitly set on
     * `Type.ime()` -- a live demonstration of the exact mechanism the measurement names, not a test
     * bug. Either way the assertion below (`== 0`) goes red on a non-zero value; the gate was restored
     * to `Build.VERSION_CODES.R` afterwards.
     */
    @Config(sdk = [28])
    @Test
    fun theSinkStaysZeroBelowApi30() {
        val activity = host()
        dispatchImeInsets(activity, 300)

        assertEquals(
            "below API 30 the framework itself resizes the window under adjustResize (kept exactly as " +
                "it was), so the sink must NOT also pad on top of it -- that would double-shrink the " +
                "content (spec §3)",
            0,
            activity.imeBottomPaddingPx.value,
        )
    }

    /**
     * Pins `setSoftKeyboardMode()`'s (and `onCreate`'s early call) API-30 branch. FAILS on the
     * pre-fix tree, where every API level -- including 30 -- is left at the manifest's
     * `adjustResize`.
     */
    @Config(sdk = [30])
    @Test
    fun theSoftInputModeIsAdjustNothingOnApi30() {
        val activity = host()
        val mode = activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(
            "API 30+ must be ADJUST_NOTHING -- the API 28 measurement (spec §3.1.1) does not apply " +
                "here, and ReadingAppBootstrap.setSoftKeyboardMode()/NavHostComposeActivity.onCreate " +
                "must agree",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            mode,
        )
    }

    /**
     * The API 28 half of the same pin: below API 30 the mode must stay exactly what it was before
     * this task (`adjustResize`, via `ReadingAppBootstrap.setSoftKeyboardMode()`'s unchanged fallback
     * branch for a non-multi-window host) -- the spec §3.1.1 measurement is why it must NOT become
     * `ADJUST_NOTHING` there.
     */
    @Config(sdk = [28])
    @Test
    fun theSoftInputModeIsAdjustResizeBelowApi30() {
        val activity = host()
        val mode = activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(
            "below API 30 the mode must stay ADJUST_RESIZE (today's non-multi-window fallback) -- " +
                "ADJUST_NOTHING there would remove the framework's resize with nothing to replace it " +
                "(spec §3.1.1's negative API 28 measurement)",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode,
        )
    }

    /**
     * F59 fix round 2 (extra adversarial review). Task 7's original fix
     * set `ADJUST_NOTHING` for the window's WHOLE life on `SDK_INT >= appOwnsImeInsetFromSdk`, which
     * un-lifted every non-reading destination this host hosts on API 30-34 -- e.g.
     * `CustomRepositoryEditorScreen`'s `OutlinedTextField`s, `AiConnectionSettingsScreen`'s
     * multi-line field, `PromptEditScreen`'s editor -- none of which has an IME sink of its own
     * (only `reading`'s `ReadingViewScreen.imeBottomPadding` exists, spec §3.3). This test drives the
     * real, composed graph -- `reading` -> a non-reading destination -> back to `reading` -- through
     * `onNewIntent`, the same `navigateToRoute` path `NavHostComposeActivity` itself uses for a
     * self-launch or an external route Intent, and reads the ACTUAL `Window.attributes
     * .softInputMode` at each step.
     *
     * FAILS on the pre-round-2 tree (HEAD `5f97794c9`): the mode STAYS `ADJUST_NOTHING` after
     * navigating away from `reading`, because `onCreate` set it once, unconditionally, for the
     * window's whole life and nothing reverted it when the graph moved to another destination.
     */
    @Config(sdk = [30])
    @Test
    fun theModeFollowsTheCurrentDestinationNotJustTheStartRoute() {
        val controller = hostController()
        val activity = controller.get()
        fun mode() = activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        assertEquals(
            "starting on reading must set ADJUST_NOTHING, as before",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            mode(),
        )

        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.download()))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "navigating to a non-reading destination (download) must give the window back " +
                "ADJUST_RESIZE -- the manifest's own value -- because that destination has no IME " +
                "sink and ADJUST_NOTHING would hide its text fields behind the keyboard",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode(),
        )

        controller.newIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.READING))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "navigating back to reading must restore ADJUST_NOTHING -- reading's own one-shot " +
                "bootstrap (bootstrapIfNeeded) does not run a second time on this host, so only the " +
                "destination-changed listener can be what restores it",
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            mode(),
        )
    }
}
