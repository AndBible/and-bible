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

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingViewHostCallbacks
import net.bible.sharedcore.reading.ReadingViewHostHandlers
import net.bible.sharedcore.reading.ReadingViewKey
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.reading.nav.readingNavGraph
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Nav-graph slice 7 Task 6: `MainBibleActivity`'s composition becomes the `reading` destination.
 *
 * The destination itself is three lines of arm around one platform-supplied content slot, and all
 * three lines are seams that fail SILENTLY when they break, which is why each gets a test here:
 *
 *  - [ReadingViewVisibility] is the only producer of `KeyHistoryItem` (design §5.1). If the arm
 *    stops entering it, the verse back-stack and history persistence stop working with no compile
 *    error and no other failing test — `ReadingHistoryAnchorTest` drives the flag by hand, so it
 *    cannot see a missing owner.
 *  - [ReadingViewHostCallbacks] is how the two per-destination `ActivityBase` members (design §4.1)
 *    reach a destination at all: a key event and a screen-on broadcast arrive at the HOST Activity.
 *    If the arm stops publishing, or the host stops consulting, volume-key scrolling and the
 *    screen-on night-mode refresh quietly do nothing.
 *  - The four temporary `ReadingViewVisibility.setVisible` calls Task 3 left in `MainBibleActivity`
 *    (`onCreate`, `onResume`, `onPause`, `onActivityResult`) must be gone, or the flag has two
 *    owners and the one that survives `MainBibleActivity`'s deletion (Task 13) is untested.
 *
 * The host-side half is driven against the REAL [NavHostComposeActivity] (the
 * `Robolectric.buildActivity(...).create()` idiom `CloudDocumentsControllerRebuildIsolationTest`
 * uses), not against a probe subclass: a probe would re-implement the very overrides under test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingDestinationInGraphTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private var keys = mutableListOf<ReadingViewKey>()
    private var screenOns = 0
    private var screenOffs = 0
    private var windowTitles = mutableListOf<String>()

    /** What [deps]' `onKey` answers — flipped by the fall-through test. */
    private var keyConsumed = true

    @Before
    fun resetSeams() {
        ReadingViewVisibility.setVisible(false)
        assertNull(
            ReadingViewHostCallbacks.current,
            "a previous test leaked a published reading view — every publish here is unpublished",
        )
    }

    @After
    fun tearDown() = DatabaseResetter.resetDatabase()

    private fun deps() = ReadingNavDeps(
        windowTitle = "AndBible",
        content = { Box(Modifier.testTag("readingView")) { Text("reading view") } },
        onKey = { key ->
            keys += key
            keyConsumed
        },
        onScreenTurnedOn = { screenOns++ },
        onScreenTurnedOff = { screenOffs++ },
        setWindowTitle = { windowTitles += it },
    )

    private fun setGraph(d: ReadingNavDeps = deps()) {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = NavRoutes.READING) {
                readingNavGraph(navController, d)
                // A sibling SCREEN (not a sheet) — design §5.1's other half: a screen over the
                // reading view is what must turn the predicate off.
                composable(SIBLING) { Text("sibling") }
            }
        }
        compose.waitForIdle()
    }

    private fun navigateTo(route: String) {
        compose.runOnIdle { navController.navigate(route) }
        compose.waitForIdle()
    }

    private fun popBack() {
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()
    }

    // ------------------------------------------------------------------ the destination itself

    /**
     * The arm renders [ReadingNavDeps.content] and nothing else. Mutation that proves it: drop the
     * `deps.content()` call from `readingNavGraph` (or wrap it in anything that swallows it) and
     * the tagged node is not found.
     */
    @Test
    fun theReadingDestinationRendersItsPlatformContent() {
        setGraph()
        compose.onNodeWithTag("readingView").assertExists()
        assertEquals(listOf("AndBible"), windowTitles, "the destination sets the host window title")
    }

    /**
     * The flag's whole point, in the shape design §5.1 argues is behaviour-neutral: TRUE while the
     * reading destination is current, FALSE while a screen is over it, TRUE again when that screen
     * pops. Mutation: delete `ReadingViewVisibility.enter()` from the arm's `DisposableEffect` (the
     * first assertion fails), or its `exit()` from `onDispose` (the second fails).
     */
    @Test
    fun theReadingDestinationOwnsTheVisibilityFlag() {
        setGraph()
        assertTrue(ReadingViewVisibility.isVisible, "composed and current — the reading view is visible")

        navigateTo(SIBLING)
        assertFalse(ReadingViewVisibility.isVisible, "a SCREEN over the reading view turns it off")

        popBack()
        assertTrue(ReadingViewVisibility.isVisible, "popping back to it turns it on again")
    }

    /**
     * The predicate is a DEPTH counter, not a boolean (Task 3's carried finding):
     * `FLAG_ACTIVITY_MULTIPLE_TASK` (`StartupActivity`) can make a second reading instance real, and
     * a process-wide boolean would let the instance that leaves clear the flag for the one still on
     * screen. Mutation: make `exit()` do `_isVisible.value = false` (a boolean) and the second
     * assertion fails.
     */
    @Test
    fun theVisibilityFlagIsADepthCounterNotABoolean() {
        setGraph()
        // A second reading view, as a second task would produce it.
        ReadingViewVisibility.enter()

        navigateTo(SIBLING)
        assertTrue(
            ReadingViewVisibility.isVisible,
            "one instance leaving must not clear the flag for the other, still-composed one",
        )

        ReadingViewVisibility.exit()
        assertFalse(ReadingViewVisibility.isVisible, "…and the last one out does clear it")
    }

    /**
     * Task 3's four temporary setters are gone, so the flag has exactly one owner. A source scan
     * because the thing asserted is an ABSENCE in production code that no runtime path can prove:
     * `MainBibleActivity` is still the launcher at this commit, so a leftover setter would keep
     * every other test green.
     */
    @Test
    fun mainBibleActivityNoLongerDrivesTheVisibilityFlag() {
        val src = ClassicRemovalScan.codeLinesOf(MAIN_BIBLE_ACTIVITY)
        assertFalse(
            src.contains("ReadingViewVisibility.setVisible"),
            "the reading destination owns the flag now — MainBibleActivity must not set it",
        )
    }

    /**
     * The tenth registration. A scan, for the reason [mainBibleActivityNoLongerDrivesTheVisibilityFlag]
     * is one: nothing navigates to `reading` until Task 8, so a missing registration is invisible at
     * runtime today. Mutation: delete the `readingNavGraph(navController, readingNavDeps)` line from
     * the host's `NavHost` block.
     */
    @Test
    fun theHostRegistersTheReadingDestinationInItsNavHost() {
        val src = ClassicRemovalScan.codeLinesOf(NAV_HOST_ACTIVITY)
        assertTrue(
            src.contains("readingNavGraph(navController, readingNavDeps)"),
            "NavHostComposeActivity must register the reading destination in its NavHost",
        )
    }

    // ------------------------------------------------------------------ the two callback families

    /**
     * The destination publishes its handlers for as long as it is on screen, and they are the ones
     * the deps carry. Mutation: drop the `ReadingViewHostCallbacks.publish(...)` from the arm (the
     * first assertion fails), or drop `unpublish()` from `onDispose` (the last one fails).
     */
    @Test
    fun theDestinationPublishesItsHostHandlersWhileItIsOnScreen() {
        setGraph()
        val handlers = assertNotNull(
            ReadingViewHostCallbacks.current,
            "the reading destination must publish its key/screen handlers",
        )

        assertTrue(handlers.onKey(ReadingViewKey.VolumeDown), "…routed to the deps' own onKey")
        assertEquals(listOf(ReadingViewKey.VolumeDown), keys)

        handlers.onScreenTurnedOn()
        handlers.onScreenTurnedOff()
        assertEquals(1, screenOns, "…and to the deps' own onScreenTurnedOn")
        assertEquals(1, screenOffs, "…and onScreenTurnedOff")

        navigateTo(SIBLING)
        assertNull(
            ReadingViewHostCallbacks.current,
            "a screen over the reading view unpublishes it — the host must not keep dispatching keys " +
                "into a composition that is gone",
        )
    }

    /**
     * The host sends a volume key to the published handler and reports the key CONSUMED. Driven
     * through the real `NavHostComposeActivity.onKeyDown`. Mutation: delete the `onKeyDown` override
     * from the host and the assertion fails (the base class's generic scroll finds no scrollable
     * view and returns false).
     */
    @Test
    fun theHostSendsVolumeKeysToThePublishedHandler() {
        val activity = buildHost()
        val unpublish = publishProbe()
        try {
            assertTrue(
                activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, keyEvent(KeyEvent.KEYCODE_VOLUME_DOWN)),
                "the host must let the reading view consume volume down",
            )
            assertTrue(
                activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, keyEvent(KeyEvent.KEYCODE_VOLUME_UP)),
                "…and volume up",
            )
            assertEquals(listOf(ReadingViewKey.VolumeDown, ReadingViewKey.VolumeUp), probeKeys)
        } finally {
            unpublish()
        }
    }

    /**
     * The handler's `false` means "not mine" and the host must fall through, exactly as classic's
     * gates did (`volume_keys_scroll` off, speaking, music playing). Mutation: make the host
     * `return true` whenever a handler is published, regardless of what it answered.
     */
    @Test
    fun theHostFallsThroughWhenTheReadingViewDeclinesTheKey() {
        val activity = buildHost()
        probeConsumes = false
        val unpublish = publishProbe()
        try {
            assertFalse(
                activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, keyEvent(KeyEvent.KEYCODE_VOLUME_DOWN)),
                "a declined key must reach super.onKeyDown, not be swallowed",
            )
            assertEquals(listOf(ReadingViewKey.VolumeDown), probeKeys, "…having been offered first")
        } finally {
            unpublish()
        }
    }

    /**
     * BACK is claimed only for an EXTERNAL KEYBOARD (classic's `InputDevice.isExternal` +
     * `SOURCE_KEYBOARD` branch); an ordinary back press is not the reading view's to swallow — the
     * `PlatformBackHandler`s and `onBackPressed` own that. Mutation: drop the source/external gate
     * from the host's decode and this fails, which is the failure that would otherwise show up as
     * "back does nothing".
     */
    @Test
    fun theHostDoesNotClaimAnOrdinaryBackPressForTheReadingView() {
        val activity = buildHost()
        val unpublish = publishProbe()
        try {
            activity.onKeyDown(KeyEvent.KEYCODE_BACK, keyEvent(KeyEvent.KEYCODE_BACK))
            assertEquals(emptyList<ReadingViewKey>(), probeKeys, "a non-keyboard BACK must not be offered as ExternalKeyboardBack")
        } finally {
            unpublish()
        }
    }

    /**
     * `enableGenericVolumeScroll` is the third per-destination member, expressed by inversion:
     * `MainBibleActivity` overrode it to FALSE because the reading view handles the volume keys
     * itself, while every other classic Activity took the base's `true`. Mutation: make the host's
     * override a constant (either constant fails one of the two assertions).
     */
    @Test
    fun theHostOptsOutOfGenericVolumeScrollOnlyWhileAReadingViewIsPublished() {
        val activity = buildHost()
        assertTrue(
            genericVolumeScroll(activity),
            "no reading view on screen — the base class's generic volume scroll stays on, as it was " +
                "for every non-reading Activity",
        )

        val unpublish = publishProbe()
        try {
            assertFalse(
                genericVolumeScroll(activity),
                "the reading view owns the volume keys, exactly as MainBibleActivity's override said",
            )
        } finally {
            unpublish()
        }
    }

    /**
     * The screen-state callbacks reach the destination. Mutation: delete either override from the
     * host — `ActivityBase`'s own implementations only log and flip `isScreenOn`, so nothing else
     * notices.
     */
    @Test
    fun theHostForwardsScreenOnAndOffToThePublishedHandler() {
        val activity = buildHost()
        val unpublish = publishProbe()
        try {
            invokeProtected(activity, "onScreenTurnedOn")
            invokeProtected(activity, "onScreenTurnedOff")
            assertEquals(1, probeScreenOns)
            assertEquals(1, probeScreenOffs)
        } finally {
            unpublish()
        }
        // Unpublished: the host must not blow up when nothing is on screen to forward to.
        invokeProtected(activity, "onScreenTurnedOn")
        assertEquals(1, probeScreenOns, "…and nothing is delivered to a destination that is gone")
    }

    // ------------------------------------------------------------------ the one allowed deletion

    /**
     * `freeze()`/`unFreeze()` swap content views between MULTIPLE Activities; there is one host, so
     * they are deleted rather than moved (plan Step 5, design §9), and with them the only caller of
     * `CurrentActivityHolder.mainBibleActivities`. Reflection rather than a source scan: what must
     * be gone is the CALLABLE member, and a scan would also match the word in a comment.
     */
    @Test
    fun freezeAndUnFreezeAreGoneWithTheirOnlyCaller() {
        val members = ActivityBase::class.java.methods.map { it.name }
        assertFalse(members.contains("freeze"), "ActivityBase.freeze() is deleted")
        assertFalse(members.contains("unFreeze"), "ActivityBase.unFreeze() is deleted")
        assertFalse(
            CurrentActivityHolder::class.java.methods.map { it.name }.contains("getMainBibleActivities"),
            "CurrentActivityHolder.mainBibleActivities had exactly one caller, inside freeze()",
        )
    }

    // ------------------------------------------------------------------ fixture

    private var probeKeys = mutableListOf<ReadingViewKey>()
    private var probeScreenOns = 0
    private var probeScreenOffs = 0
    private var probeConsumes = true

    /**
     * A reading view published WITHOUT composing the graph: the host-side tests are about the host's
     * overrides, and composing a real `NavHost` inside a second Activity would only add moving parts
     * the assertions do not read.
     */
    private fun publishProbe(): () -> Unit = ReadingViewHostCallbacks.publish(
        ReadingViewHostHandlers(
            onKey = { key ->
                probeKeys += key
                probeConsumes
            },
            onScreenTurnedOn = { probeScreenOns++ },
            onScreenTurnedOff = { probeScreenOffs++ },
        ),
    )

    private fun buildHost(): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        // Any route but READING: this test drives the host's Activity-level overrides, and the
        // reading destination's production content slot is the one thing the host cannot build yet
        // (see ReadingNavDeps' kdoc).
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
    ).create().get()

    private fun keyEvent(keyCode: Int) = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)

    private fun genericVolumeScroll(activity: NavHostComposeActivity): Boolean =
        NavHostComposeActivity::class.java
            .getDeclaredMethod("getEnableGenericVolumeScroll")
            .apply { isAccessible = true }
            .invoke(activity) as Boolean

    private fun invokeProtected(activity: NavHostComposeActivity, name: String) {
        NavHostComposeActivity::class.java
            .getDeclaredMethod(name)
            .apply { isAccessible = true }
            .invoke(activity)
    }

    companion object {
        private const val SIBLING = "sibling"
        private const val MAIN_BIBLE_ACTIVITY =
            "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"
        private const val NAV_HOST_ACTIVITY =
            "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"
    }
}
