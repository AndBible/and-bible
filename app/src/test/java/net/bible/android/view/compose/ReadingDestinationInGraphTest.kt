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

import android.content.Context
import android.os.PowerManager
import android.view.InputDevice
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatDelegate
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
import net.bible.android.control.page.window.Window
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewHostCallbacks
import net.bible.sharedcore.reading.ReadingViewHostHandlers
import net.bible.sharedcore.reading.ReadingViewKey
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.reading.nav.readingNavGraph
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.reflect.KCallable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
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
 *  - `MainBibleActivity` must not FORCE the flag (`ReadingViewVisibility.setVisible`, the test-only
 *    reset that ignores every registration): its four temporary lifecycle call sites drive the
 *    separate `setActivityVisible(this, …)` input instead — see Task 6 fix round 1 and
 *    [ReadingViewVisibility]'s kdoc — so the destination's own registration, the input that
 *    survives `MainBibleActivity`'s deletion (Task 13), can never be clobbered by the Activity path.
 *
 * **R7b re-keyed both seams by HOST.** Every registration and every publication carries the token
 * of the host it belongs to ([ReadingNavDeps.host]) and counts only while [ReadingHostPresence]
 * says that host is foreground, so the fixture below declares [graphHost] in front before composing
 * — and the mutation notes on each test name the CURRENT signatures (`enter(host)`/`exit(host)`,
 * `publish(handlers, host)`), not the zero-argument ones R7b removed.
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

    /**
     * The host the composed destination belongs to (reading-host re-typing R7b): an opaque token,
     * exactly as `ReadingNavDeps.host` is declared, standing in for the Activity the production
     * host passes as `this`. [setGraph] declares it FOREGROUND, which is what a resumed host does
     * in `onResume` — without that, a composed destination is correctly invisible and half the
     * assertions below would be testing the wrong state.
     */
    private val graphHost = Any()

    @Before
    fun resetSeams() {
        ReadingViewVisibility.setVisible(false)
        ReadingHostPresence.setForeground(null)
        // R7b: `current` is the FOREGROUND host's reading view, so with no host in front it is null
        // whether or not something leaked — `publishedCount` is the leak detector that can still
        // see one. (A check that cannot fail is worse than no check.)
        assertEquals(
            0, ReadingViewHostCallbacks.publishedCount,
            "a previous test leaked a published reading view — every publish here is unpublished",
        )
    }

    /**
     * Every host [buildHost] builds, so [tearDown] can destroy it.
     *
     * **R8 made this necessary, and the mechanism is worth naming.** These hosts start on the
     * READING route now, so each one's `setContent` is a reading destination waiting to compose --
     * and Robolectric composes it as soon as anything drains the main looper and lets the pending
     * Choreographer traversal attach the decor view. `createComposeRule`'s own teardown does
     * exactly that, AFTER this class's `@After` has run, so an undestroyed host published its
     * handlers in the gap between two tests and [resetSeams]' leak assertion blamed the next one.
     * Destroying them here is what keeps that assertion able to see a REAL leak.
     */
    private val hostControllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        hostControllers.forEach { it.close() }
        hostControllers.clear()
        ReadingHostPresence.setForeground(null)
        DatabaseResetter.resetDatabase()
    }

    private fun deps() = ReadingNavDeps(
        host = graphHost,
        windowTitle = "AndBible",
        content = { Box(Modifier.testTag("readingView")) { Text("reading view") } },
        onKey = { key ->
            keys += key
            keyConsumed
        },
        onScreenTurnedOn = { screenOns++ },
        onScreenTurnedOff = { screenOffs++ },
        setWindowTitle = { windowTitles += it },
        // T8c: this test is about the destination's visibility/handler/title seams, not about the
        // answers its launches produce -- those are ReadingInGraphResultTest's. Explicit rather than
        // defaulted, because the slot has no default: see ReadingNavDeps.results.
        results = emptyList(),
    )

    private fun setGraph(d: ReadingNavDeps = deps()) {
        // The host is in front — see [graphHost]. Declared before the graph composes, as a resumed
        // Activity's onResume runs before its composition.
        ReadingHostPresence.setForeground(d.host)
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
     * pops. Mutation: delete `ReadingViewVisibility.enter(host)` from the arm's `DisposableEffect`
     * (the first assertion fails), or its `exit(host)` from `onDispose` (the second fails).
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
     * **REWRITTEN by reading-host re-typing R7b — this was `theVisibilityFlagIsADepthCounterNotABoolean`,
     * and its assertion was the divergence.**
     *
     * `FLAG_ACTIVITY_MULTIPLE_TASK` (`StartupActivity`) can make a second reading instance real, and
     * the old test asserted that the second one's registration keeps the flag ON after the composed,
     * on-screen one leaves. It cannot: that second instance is in ANOTHER TASK and its host is in
     * the background, so what the user is looking at once this destination pops is the sibling
     * screen. Keeping the flag on there is the dead back key (`HistoryManager.goBack`'s
     * `if (!isVisible) finish()`) this task exists to fix.
     *
     * The registrations are still per-host multisets and one instance still cannot un-register
     * another's — that property just cannot be SEEN through `isVisible` from a backgrounded host any
     * more, so it is pinned in `ReadingViewVisibilityTest.oneHostsRegistrationsAreNotAnothersToRemove`
     * (`:sharedCore`) instead. What this test pins now is the gate itself, through a real
     * composition. Mutation: drop the [ReadingHostPresence] gate from `isVisible` and the second
     * assertion fails.
     */
    @Test
    fun aSecondTasksReadingViewIsNotTheOneOnScreen() {
        setGraph()
        // A second reading view, as a second task would produce it: another host, in the background.
        val otherHost = Any()
        ReadingViewVisibility.enter(otherHost)
        assertTrue(ReadingViewVisibility.isVisible, "sanity: this host's destination is on screen")

        navigateTo(SIBLING)
        assertFalse(
            ReadingViewVisibility.isVisible,
            "a sibling SCREEN is what the user is looking at — a second task's reading view, whose " +
                "host is in the background, must not keep the flag on",
        )

        popBack()
        assertTrue(ReadingViewVisibility.isVisible, "…and popping back to this one turns it on again")

        ReadingViewVisibility.exit(otherHost)
    }

    /**
     * `MainBibleActivity` drives the ACTIVITY input (`setActivityVisible`, restored in Task 6 fix
     * round 1 because the destination's content slot cannot render the reading view yet) and must
     * never touch the forcing setter, which clears every registration — this destination's
     * included. A source scan because the thing asserted is an ABSENCE in production code that no
     * runtime path can prove: `MainBibleActivity` is still the launcher at this commit, so a
     * `setVisible(false)` smuggled into its `onPause` would clear a composed destination's
     * registration and keep every other test green.
     */
    @Test
    fun mainBibleActivityNoLongerDrivesTheVisibilityFlag() {
        val src = ClassicRemovalScan.codeLinesOf(MAIN_BIBLE_ACTIVITY)
        assertFalse(
            src.contains("ReadingViewVisibility.setVisible"),
            "the destination owns its own registration — MainBibleActivity must use the separate " +
                "setActivityVisible(this, …) input, never the forcing setter",
        )
    }

    /**
     * The tenth registration -- and, since reading-host re-typing R8, a RUNTIME assertion instead of
     * the source scan this used to be.
     *
     * The scan existed because "nothing navigates to `reading` until Task 8, so a missing
     * registration is invisible at runtime today". R8 is what removed that reason: the production
     * [net.bible.sharedui.reading.nav.ReadingNavDeps.content] slot builds the real reading view now,
     * so a real host started on the reading route composes the real destination. **The scan was
     * DELETED rather than kept alongside**, because the runtime test strictly subsumes it: with the
     * `readingNavGraph(navController, readingNavDeps)` line gone, this host's `NavHost` has no
     * `reading` arm and `startDestination = reading` throws `IllegalArgumentException` before any
     * assertion here is reached.
     *
     * What the scan could never have seen, and this does:
     *
     *  - the content slot actually COMPOSES. Its predecessor was a deliberate `error(...)`; a scan
     *    cannot tell a real slot from a loud one.
     *  - the whole reading view composes, panes included, against a host that is not
     *    `MainBibleActivity` -- the claim R6a--R6d's re-typing exists to make.
     *  - the ORDER constraints of the composition. This test found a real one: `ReadingCommands`'
     *    constructor calls `registerForActivityResult`, which throws once its owner is STARTED, so
     *    the host's `by lazy` command surface has to be forced in `onCreate`. Nothing in the source
     *    text says that, and no scan could.
     *
     * `.visible()` is what makes it a composition at all: Robolectric attaches the decor view there,
     * and Compose composes on attach. Mutations: restore the `error(...)`, drop the
     * `readingNavGraph(...)` registration, or move the `readingCommands` force in `onCreate` below
     * `setContent` -- each of the three fails this.
     */
    @Test
    fun theHostComposesTheRealReadingViewOnTheReadingRoute() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        try {
            val activity = controller.create().start().resume().visible().get()

            assertNotNull(
                activity.composeReadingViewHost,
                "the reading destination's content slot must have composed this host's own " +
                    "ComposeReadingViewHost -- it was an error(...) until R8",
            )
            assertTrue(
                ReadingViewVisibility.isVisible,
                "a composed reading destination under a resumed host IS the reading view on screen",
            )
            assertNotNull(
                ReadingViewHostCallbacks.current,
                "…and its key/screen handlers are the current ones",
            )
            assertEquals(
                activity.getString(net.bible.android.activity.R.string.app_name_short),
                activity.title.toString(),
                "the destination applies classic MainBibleActivity's manifest android:label",
            )
        } finally {
            // Destroys the Activity, which disposes the composition: the arm's onDispose is what
            // unpublishes the handlers this class's @Before refuses to inherit.
            controller.close()
        }
    }

    /**
     * **R8 fix round 1, review Important 2: the INITIAL CONTENT LOAD.**
     *
     * Classic `MainBibleActivity.setupUi` mounts the reading view and then, one line later
     * (`:624`), calls `windowControl.windowSync.reloadAllWindows(true)` -- which drives
     * `Window.updateOrScroll()` on every visible window and, with `force`, `Window.loadText()`.
     * Nothing else in the app does it: `DocumentViewManager.buildView` does not, and
     * `BibleViewFactory.getOrCreateBibleView` only creates and `initialise()`s the view. R8's first
     * cut ported `setupUi`'s `ComposeReadingViewHost` + `rebuildDrawer()` pair and stopped there, so
     * this host composed `BibleView`s that were never handed a document -- a blank reading view with
     * no error anywhere. [readingViewHost] makes the call now.
     *
     * Asserted through `Window.displayedKey`, which `loadText` assigns (`Window.kt:258-259`) BEFORE
     * handing the rest to `updateScope.launch(Dispatchers.IO)` -- so it is the one effect of the
     * load that is observable synchronously, and observing it means `updateOrScroll` really reached
     * `loadText` rather than the `scrollToText` branch or the `!isVisible` early return. Reflection
     * because the field is private to a model class; this class already reaches the host's private
     * members the same way.
     *
     * A weaker assertion was available and rejected: `WindowSync.lastForceSyncAll` advancing proves
     * only that `setResyncRequired()` ran, which is true of `reloadAllWindows(false)` too and says
     * nothing about any window loading anything.
     *
     * Mutation: delete `hostWindowRepository.windowSync.reloadAllWindows(true)` from
     * `readingViewHost()` and `displayedKey` stays null.
     */
    @Test
    fun theComposedReadingViewsWindowsGetTheirInitialContentLoad() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        try {
            val activity = controller.create().start().resume().visible().get()
            val window = activity.hostWindowRepository.activeWindow
            assertTrue(
                window.isVisible,
                "sanity: the workspace's active window is visible, so loadText cannot have " +
                    "returned early — without this the assertion below could not fail",
            )
            assertNotNull(
                displayedKeyOf(window),
                "the reading destination must run classic setupUi's reloadAllWindows(true): a " +
                    "window whose BibleView was never handed a document renders blank",
            )
        } finally {
            controller.close()
        }
    }

    /**
     * The two DRAWER debts reading-host re-typing R8 paid, both of which needed the content slot to
     * be real before they could be paid at all, and both of which were SILENT NO-OPS until it was.
     *
     *  - `NavHostComposeActivity.toggleDrawer()` was a documented no-op with a comment deferring it
     *    to slice 7 Task 11. It is the ☰ button's only target
     *    (`ReadingToolbarCallbacks.onHome`), so on this host the ☰ button did nothing at all the
     *    moment the reading view rendered. R8 made it classic's one-line
     *    `readingCommands.composeToggleDrawer()`.
     *  - `ExternalKeyboardBack` returned `true` and closed nothing --
     *    [net.bible.sharedui.reading.nav.ReadingNavDeps.content]'s owed-work item 1. Classic closed
     *    both drawers; only the Compose one exists here, and R8 closes it.
     *
     * Driven through `ReadingViewHostCallbacks.current`, not through the private method, so what is
     * exercised is the same path a real key event takes: `onKeyDown` -> decode -> the published
     * handler the destination's own `DisposableEffect` installed.
     *
     * Mutations: restore either no-op and one of the two `assertFalse`/`assertTrue` pairs fails.
     */
    @Test
    fun theHostsDrawerCommandsReachTheComposedReadingViewsDrawer() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        )
        try {
            val activity = controller.create().start().resume().visible().get()
            val readingView = assertNotNull(
                activity.composeReadingViewHost,
                "the content slot must have composed a reading view for this test to mean anything",
            )
            assertFalse(readingView.isDrawerOpen, "sanity: the drawer starts closed")

            activity.toggleDrawer()
            assertTrue(
                readingView.isDrawerOpen,
                "the host's toggleDrawer — the ☰ button's only target — must open the reading " +
                    "view's own Compose drawer",
            )

            val handlers = assertNotNull(ReadingViewHostCallbacks.current)
            assertTrue(
                handlers.onKey(ReadingViewKey.ExternalKeyboardBack),
                "classic consumed external-keyboard BACK unconditionally",
            )
            assertFalse(
                readingView.isDrawerOpen,
                "…and closed the drawer on the way, which is the half R8 owed",
            )
        } finally {
            controller.close()
        }
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
     * The published handlers must DELEGATE to whatever deps the arm currently has, not carry the
     * deps instance that happened to be current when the `DisposableEffect(Unit)` first ran. The
     * effect is keyed on `Unit` on purpose (a re-key would be an exit/enter pair History would read
     * as the reading view briefly leaving), so `onKey = deps.onKey` would pin the lambda forever:
     * safe only because today's host `remember`s its deps, and silently wrong for a host that writes
     * `ReadingNavDeps(...)` inline. `rememberUpdatedState` + delegating lambdas remove the
     * dependency on that accident.
     *
     * Asserted by IDENTITY rather than behaviour, and that is not laziness: `deps` reaches the arm
     * through the closure of the `composable(route) { … }` content lambda, which is built once when
     * `NavHost` creates its graph. Handing the graph a new deps instance means a new builder lambda,
     * which re-keys `NavHost`'s `remember` and rebuilds the graph — which pops and recreates the
     * back stack, disposes the destination and re-runs the effect, so a behavioural version of this
     * test would pass against the pinned version too. Identity is what actually distinguishes them.
     * Mutation: publish `deps.onKey`/`deps.onScreenTurnedOn`/`deps.onScreenTurnedOff` directly and
     * all three assertions fail.
     */
    @Test
    fun theDestinationPublishesDelegatingHandlersRatherThanThePinnedDepsLambdas() {
        val d = deps()
        setGraph(d)
        val handlers = assertNotNull(ReadingViewHostCallbacks.current)

        assertNotSame(d.onKey, handlers.onKey, "onKey must be a delegate, not the deps' own lambda")
        assertNotSame(d.onScreenTurnedOn, handlers.onScreenTurnedOn, "…and onScreenTurnedOn")
        assertNotSame(d.onScreenTurnedOff, handlers.onScreenTurnedOff, "…and onScreenTurnedOff")

        // …and the delegate still reaches the deps it delegates to.
        assertTrue(handlers.onKey(ReadingViewKey.VolumeUp))
        assertEquals(listOf(ReadingViewKey.VolumeUp), keys)
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
        val unpublish = publishProbe(activity)
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
        val unpublish = publishProbe(activity)
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
        val unpublish = publishProbe(activity)
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

        val unpublish = publishProbe(activity)
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
        val unpublish = publishProbe(activity)
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

    /**
     * **R7b fix round 1, review Important: the screen-off that `super.onPause()` itself dispatches
     * must still reach the reading view the host is leaving.**
     *
     * `ActivityBase.onPause` ends with `if (isScreenOn && !ScreenSettings.isScreenOn)
     * onScreenTurnedOff()` — the screen going off under a resumed Activity is delivered from INSIDE
     * `onPause`, and the foreground host is the right recipient, because that gate only trips when
     * this Activity's own screen went off. R7b's first cut retracted the host's presence BEFORE
     * `super.onPause()`, so `ReadingViewHostCallbacks.current` was already null by the time the
     * override ran and `BibleView.onScreenTurnedOff()` silently stopped being called on every
     * screen-off — a NEW divergence, not the one R7b set out to fix (before R7b, `current` was
     * `lastOrNull()` and fired).
     *
     * [theHostForwardsScreenOnAndOffToThePublishedHandler] cannot see this: it invokes the overrides
     * reflectively, outside the lifecycle, with the probe's host declared foreground — a check that
     * cannot fail on an ORDERING defect. This one drives the real `pause()` with the screen off.
     * Mutation: move `ReadingHostPresence.clearForeground(this)` above `super.onPause()` in
     * `NavHostComposeActivity.onPause` and it fails; that ordering is the RED this fix started from.
     */
    @Test
    fun theHostDeliversTheScreenOffThatItsOwnPauseDispatches() {
        val controller = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
        )
        val powerManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.POWER_SERVICE) as PowerManager
        val activity = controller.create().start().resume().get()
        val unpublish = publishProbe(activity)
        try {
            // The screen goes off under the resumed host; the pause that follows is where
            // ActivityBase notices it.
            shadowOf(powerManager).setIsScreenOn(false)
            controller.pause()
            assertEquals(
                1, probeScreenOffs,
                "the reading view the host is leaving must still be told the screen went off — " +
                    "ActivityBase dispatches it from inside super.onPause()",
            )
        } finally {
            shadowOf(powerManager).setIsScreenOn(true)
            unpublish()
            controller.close()
        }
    }

    // ------------------------------------------------------------------ the deletion that was NOT allowed

    /**
     * **`freeze()`/`unFreeze()` STAY until Task 13 deletes `MainBibleActivity` itself.**
     *
     * Task 6 deleted them (plan Step 5, design §9) on the premise that "the migration leaves one
     * host, so there is nothing to swap". Fix round 2 restored them, because the premise is false on
     * this branch and this test pins the reason: `StartupActivity.gotoMainBibleActivity()` launches
     * `MainBibleActivity` with `FLAG_ACTIVITY_MULTIPLE_TASK` for an `ACTION_VIEW` intent, so a deep
     * link opened while the app is running produces a SECOND live `MainBibleActivity` — the same
     * scenario [ReadingViewVisibility]'s and [ReadingViewHostCallbacks]'s per-host registrations
     * exist for (R7b; before it, a depth counter and a publish stack). Two instances registered on `ABEventBus` at once handle every bus event twice
     * (`AppToBackgroundEvent` syncing twice, `MainBibleAfterRestore` resetting twice,
     * `WorkspacesUpdatedViaSyncEvent` judging workspace deletion against the wrong repository), and
     * `freeze()`'s `ABEventBus.unregister(this)` is the only thing that prevents it.
     *
     * So this test is deliberately an argument, not just an assertion: a future deletion has to
     * make the MULTIPLE_TASK launch go away first, and will trip over this scan when it does not.
     */
    @Test
    fun freezeAndUnFreezeStayWhileASecondMainBibleActivityIsReachable() {
        assertTrue(
            ClassicRemovalScan.codeLinesOf(STARTUP_ACTIVITY).contains("FLAG_ACTIVITY_MULTIPLE_TASK"),
            "the reason freeze()/unFreeze() still exist: StartupActivity can launch a SECOND " +
                "MainBibleActivity. If this line is gone, re-argue the deletion — do not just " +
                "delete this test",
        )

        val members = ActivityBase::class.java.methods.map { it.name }
        assertTrue(members.contains("freeze"), "ActivityBase.freeze() must still exist")
        assertTrue(members.contains("unFreeze"), "ActivityBase.unFreeze() must still exist")
        assertTrue(
            CurrentActivityHolder::class.java.methods.map { it.name }.contains("getMainBibleActivities"),
            "MainBibleActivity.freeze() asks CurrentActivityHolder.mainBibleActivities whether it is " +
                "the only reading Activity there is",
        )
    }

    /**
     * The wiring, not just the members: `CurrentActivityHolder` unfreezes the Activity coming to the
     * front, freezes everything underneath it, and unfreezes whatever is left on top when one
     * leaves. Mutation: drop any one of the three calls and one assertion here fails.
     *
     * Bare `ActivityBase` instances, never created: `activate`/`deactivate` only add to a list, call
     * these two overrides and post the app-level foreground/background event, so no Activity
     * internals are touched and no Robolectric lifecycle is needed. Deltas rather than absolute
     * counts because other tests in this JVM leave Activities in the holder.
     */
    @Test
    fun activatingAnActivityOnTopFreezesTheOneUnderneathAndUnfreezesItOnTheWayBack() {
        val under = FreezeProbeActivity()
        val onTop = FreezeProbeActivity()
        try {
            CurrentActivityHolder.activate(under)
            assertEquals(1, under.unFreezes, "activate() unfreezes the Activity coming to the front")
            assertEquals(0, under.freezes, "…and nothing is on top of it yet")

            CurrentActivityHolder.activate(onTop)
            assertEquals(
                1,
                under.freezes,
                "an Activity on top freezes the one underneath — which is what takes the second " +
                    "MainBibleActivity's ABEventBus subscriptions out of the way",
            )
            assertEquals(1, onTop.unFreezes, "…and unfreezes the incoming one")

            CurrentActivityHolder.deactivate(onTop)
            assertEquals(2, under.unFreezes, "leaving unfreezes whatever is left on top")
        } finally {
            CurrentActivityHolder.deactivate(onTop)
            CurrentActivityHolder.deactivate(under)
        }
    }

    // ------------------------------------------------------------------ the host-side ports

    /**
     * The volume-key transposition gate. Every OTHER host-side test here publishes a probe handler,
     * so `readingViewKeyPressed` itself never runs — and its `BibleView` calls cannot run in a unit
     * test at all (no WebView is ever built, and `BibleView` is final). Swapping `volumeUpPressed()`
     * and `volumeDownPressed()` was therefore a mutation the whole suite survived. The mapping is
     * lifted into `readingViewScrollFor` for exactly this assertion; mutation: swap the two
     * references and both assertions fail.
     */
    @Test
    fun theVolumeKeysMapToTheMatchingBibleViewScroll() {
        val activity = buildHost()
        assertEquals("volumeDownPressed", scrollFor(activity, ReadingViewKey.VolumeDown).name)
        assertEquals("volumeUpPressed", scrollFor(activity, ReadingViewKey.VolumeUp).name)
    }

    /**
     * Classic's three gates, ported. Mutations: drop the `volume_keys_scroll` term (the second
     * assertion fails) or invert `!speakControl.isSpeaking` (the first fails — nothing is speaking
     * in a unit test, so the gate must be open).
     */
    @Test
    fun theVolumeKeyGatesAreClassicsOwn() {
        val activity = buildHost()
        try {
            assertTrue(
                volumeKeysOwned(activity),
                "nothing is speaking and no music is playing, and volume_keys_scroll defaults to " +
                    "on — the reading view takes the key",
            )

            CommonUtils.settings.setBoolean("volume_keys_scroll", false)
            assertFalse(
                volumeKeysOwned(activity),
                "volume_keys_scroll off means the key falls through to super.onKeyDown, as it did " +
                    "in classic",
            )
        } finally {
            CommonUtils.settings.removeBoolean("volume_keys_scroll")
        }
    }

    /**
     * The POSITIVE external-keyboard branch — the one
     * [theHostDoesNotClaimAnOrdinaryBackPressForTheReadingView] cannot reach, and the one whose
     * failure is silent (an external-keyboard BACK that is never claimed looks like working
     * software until someone plugs a keyboard in). Robolectric registers no input devices, so the
     * device half of the decode is replaced; the SOURCE half is real.
     *
     * Mutations: make `isExternalDevice` constantly false (the first assertion fails), or drop the
     * `SOURCE_KEYBOARD` term from `isExternalKeyboard` (the second fails).
     */
    @Test
    fun theHostClaimsBackOnlyFromAnExternalKeyboard() {
        val activity = buildHost()
        stubExternalDevice(activity, external = true)

        assertEquals(
            ReadingViewKey.ExternalKeyboardBack,
            keyFor(activity, KeyEvent.KEYCODE_BACK, keyEvent(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_KEYBOARD)),
            "BACK from an external keyboard is the reading view's",
        )
        assertNull(
            keyFor(activity, KeyEvent.KEYCODE_BACK, keyEvent(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_TOUCHSCREEN)),
            "…but the same external device on a non-keyboard source is not",
        )

        stubExternalDevice(activity, external = false)
        assertNull(
            keyFor(activity, KeyEvent.KEYCODE_BACK, keyEvent(KeyEvent.KEYCODE_BACK, InputDevice.SOURCE_KEYBOARD)),
            "…and neither is a keyboard that is not external (the on-screen one)",
        )
    }

    /**
     * The screen-on port re-reads night mode and re-applies the theme — classic's
     * `refreshIfNightModeChange()`. Only `applyTheme()` is observable from a test
     * (`AppCompatDelegate`'s default night mode is process-global);
     * `ScreenSettings.refreshNightMode()`/`checkMonitoring()` are no-ops unless automatic night mode
     * is on, which needs a light sensor. Mutation: drop `applyTheme()` from
     * `readingViewScreenTurnedOn` and this fails.
     */
    @Test
    fun theScreenOnPortReAppliesTheNightModeTheme() {
        val activity = buildHost()
        val prefs = CommonUtils.realSharedPreferences
        val hadNightMode = prefs.getBoolean("night_mode_pref", false)
        val previousDefault = AppCompatDelegate.getDefaultNightMode()
        try {
            prefs.edit().putBoolean("night_mode_pref", true).commit()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

            invokeProtected(activity, "readingViewScreenTurnedOn")

            assertEquals(
                AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.getDefaultNightMode(),
                "the screen came on with night mode set — classic re-applied the theme here",
            )
        } finally {
            prefs.edit().putBoolean("night_mode_pref", hadNightMode).commit()
            AppCompatDelegate.setDefaultNightMode(previousDefault)
        }
    }

    /**
     * Both screen-state ports forward to `windowControl.activeWindow.bibleView`, which is null until
     * a window has built its WebView — always, in a unit test. The forward itself therefore has no
     * assertion available; what IS worth pinning is that neither port throws on that null, because
     * `onScreenTurnedOff` runs on a broadcast and an exception there takes the host down.
     */
    @Test
    fun theScreenStatePortsSurviveAWindowWithNoBibleView() {
        val activity = buildHost()
        invokeProtected(activity, "readingViewScreenTurnedOff")
        invokeProtected(activity, "readingViewScreenTurnedOn")
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
     *
     * R7b: published on behalf of [host] AND with [host] declared foreground, because
     * `ReadingViewHostCallbacks.current` now answers only for the host in front. [buildHost] stops
     * at `create()`, so the Activity has not run the `onResume` that would declare it itself; doing
     * it here is that call, not a workaround for the gate — the gate's own behaviour is pinned in
     * `ReadingViewVisibilityTest.theHandlersOfABackgroundedHostAreNotCurrent` (`:sharedCore`).
     */
    private fun publishProbe(host: Any): () -> Unit {
        ReadingHostPresence.setForeground(host)
        val unpublish = ReadingViewHostCallbacks.publish(
            ReadingViewHostHandlers(
                onKey = { key ->
                    probeKeys += key
                    probeConsumes
                },
                onScreenTurnedOn = { probeScreenOns++ },
                onScreenTurnedOff = { probeScreenOffs++ },
            ),
            host = host,
        )
        return {
            unpublish()
            ReadingHostPresence.clearForeground(host)
        }
    }

    /**
     * The host these tests drive its Activity-level overrides on, **started on the READING route
     * since R8**. It used to be started on `AI_TOOL_INFO` because "the reading destination's
     * production content slot is the one thing the host cannot build yet" -- R8 built it, so a host
     * test that still avoided the reading route would be avoiding the thing under test: every
     * override below (the key decode, the volume gates, the screen-on/off ports) exists FOR the
     * reading destination, and on any other route the handlers they feed are never published.
     *
     * `.create()` and no further: `onCreate` is where `bootstrapIfNeeded()` and the command
     * surface's activity-result registration run, and Robolectric composes nothing until the decor
     * view is attached (`.visible()`), which only
     * [theHostComposesTheRealReadingViewOnTheReadingRoute] wants.
     */
    private fun buildHost(): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
    ).also { hostControllers += it }.create().get()

    private fun keyEvent(keyCode: Int) = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)

    /** A key event from device 7 on an explicit input SOURCE — what the external-keyboard decode reads. */
    private fun keyEvent(keyCode: Int, source: Int) = KeyEvent(
        0L, 0L, KeyEvent.ACTION_DOWN, keyCode, 0, 0, /* deviceId = */ 7, /* scancode = */ 0,
        /* flags = */ 0, source,
    )

    /**
     * Replaces the host's `isExternalDevice` seam. Written through the backing FIELD: the property
     * is `internal`, and going through the field keeps the test independent of Kotlin's
     * friend-module/name-mangling arrangements for the test source set.
     */
    private fun stubExternalDevice(activity: NavHostComposeActivity, external: Boolean) {
        NavHostComposeActivity::class.java
            .getDeclaredField("isExternalDevice")
            .apply { isAccessible = true }
            .set(activity) { _: Int -> external }
    }

    private fun keyFor(activity: NavHostComposeActivity, keyCode: Int, event: KeyEvent): ReadingViewKey? =
        NavHostComposeActivity::class.java
            .getDeclaredMethod("readingViewKeyFor", Int::class.java, KeyEvent::class.java)
            .apply { isAccessible = true }
            .invoke(activity, keyCode, event) as ReadingViewKey?

    private fun volumeKeysOwned(activity: NavHostComposeActivity): Boolean =
        NavHostComposeActivity::class.java
            .getDeclaredMethod("readingViewOwnsVolumeKeys")
            .apply { isAccessible = true }
            .invoke(activity) as Boolean

    private fun scrollFor(activity: NavHostComposeActivity, key: ReadingViewKey): KCallable<*> =
        NavHostComposeActivity::class.java
            .getDeclaredMethod("readingViewScrollFor", ReadingViewKey::class.java)
            .apply { isAccessible = true }
            .invoke(activity, key) as KCallable<*>

    private fun genericVolumeScroll(activity: NavHostComposeActivity): Boolean =
        NavHostComposeActivity::class.java
            .getDeclaredMethod("getEnableGenericVolumeScroll")
            .apply { isAccessible = true }
            .invoke(activity) as Boolean

    /**
     * `Window.displayedKey`, assigned by `Window.loadText` (`Window.kt:259`) and private to that
     * model class. A missing field THROWS rather than returning null, so a rename cannot turn
     * [theComposedReadingViewsWindowsGetTheirInitialContentLoad] into a check that always passes —
     * or always fails — for the wrong reason.
     */
    private fun displayedKeyOf(window: Window): Any? =
        requireNotNull(runCatching { Window::class.java.getDeclaredField("displayedKey") }.getOrNull()) {
            "Window.displayedKey is gone — re-anchor this test on whatever loadText() now assigns"
        }.apply { isAccessible = true }.get(window)

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
        private const val STARTUP_ACTIVITY =
            "src/main/java/net/bible/android/view/activity/StartupActivity.kt"
    }
}

/**
 * A bare [ActivityBase] whose only job is to count [ActivityBase.freeze]/[ActivityBase.unFreeze].
 * Never created as an Activity — see
 * [ReadingDestinationInGraphTest.activatingAnActivityOnTopFreezesTheOneUnderneathAndUnfreezesItOnTheWayBack].
 */
class FreezeProbeActivity : ActivityBase() {
    var freezes = 0
    var unFreezes = 0
    override fun freeze() { freezes++ }
    override fun unFreeze() { unFreezes++ }
}
