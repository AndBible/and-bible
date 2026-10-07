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

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.reading.ReadingHostPresence
import net.bible.sharedcore.reading.ReadingViewVisibility
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading-host re-typing T8a item 3: **the bootstrap bridge is re-armed on resume, because T8a item
 * 2 gave the host a pre-composition producer of history items.**
 *
 * The window, in [ReadingViewVisibility]'s own terms: `bootstrapIfNeeded()` adds the host to
 * `activityHosts` (the bridge) before the deep link it dispatches; an `onPause` BEFORE the `reading`
 * destination's `DisposableEffect` ever calls `enter(host)` retires it; and `onResume` did not
 * re-arm it, so a host that resumes before its destination has composed reported
 * `isVisible == false`.
 *
 * **R7b accepted that as harmless and the class kdoc said why — and named its own expiry:** "A task
 * that gives the host another pre-composition producer of history items has to close this." T8a
 * item 2 is that task. Its `onResume` reconciliation calls `handlePendingAgentResult()`, which goes
 * `LinkControl.openAIDocument`/`openStudyPad` -> `showLink` ->
 * `CurrentPageManager.setCurrentDocumentAndKey` / `WindowControl.showLink` -> `setKey(addHistoryItem
 * = true)` -> a SYNCHRONOUS `HistoryManager.recordIfCreated` call, handled by
 * `HistoryManager.createHistoryItem` — the one consumer of `isVisible` with teeth. Inside the window
 * that reads false and records a wrong `IntentHistoryItem` carrying the host's own launch Intent
 * instead of a `KeyHistoryItem` for the verse, which is the same defect
 * `bootstrapIfNeeded`'s bridge exists to prevent, one lifecycle callback later.
 *
 * **Why re-arming does not resurrect what made `setActivityVisible` one-shot.** The kdoc's objection
 * is to an UNCONDITIONAL re-arm — it "would report a reading view on screen for every other
 * destination this host shows". The re-arm is conditional on `readingAppBootstrapped &&
 * composeReadingViewHost == null`, i.e. exactly the bridge's own meaning: this host owes a reading
 * view that has not composed yet. `composeReadingViewHost` is memoised for the host's whole life, so
 * once the destination has composed the re-arm can never fire again — which is what
 * [aDisposedDestinationStillTurnsTheFlagOffAfterAResume] pins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostBridgeRearmTest {

    private val controllers = mutableListOf<ActivityController<NavHostComposeActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.close() }
        controllers.clear()
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(false)
        DatabaseResetter.resetDatabase()
    }

    private fun hostOn(route: String): ActivityController<NavHostComposeActivity> =
        Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), route),
        ).also { controllers += it }

    /**
     * Anti-vacuity: `.create().start().resume()` WITHOUT `.visible()` really does leave the
     * destination uncomposed, which is the whole premise of the window. If Robolectric ever starts
     * composing at `resume()`, this fails and the tests below stop meaning what they say.
     */
    @Test
    fun aHostThatWasNeverMadeVisibleHasNotComposedItsReadingView() {
        val activity = hostOn(NavRoutes.READING).create().start().resume().get()
        assertNull(
            activity.composeReadingViewHost,
            "the reading view composes on attach (.visible()); without it this fixture is not the " +
                "pre-composition window it claims to be",
        )
        assertTrue(
            ReadingViewVisibility.isVisible,
            "sanity: the bootstrap bridge is armed on a freshly created, resumed reading host",
        )
    }

    /**
     * The window itself. Mutation: delete the re-arm from `onResume` and this fails.
     */
    @Test
    fun aHostThatResumesBeforeItsDestinationComposesStillReportsTheReadingView() {
        val controller = hostOn(NavRoutes.READING).apply { create().start().resume() }
        controller.pause()
        assertFalse(
            ReadingViewVisibility.isVisible,
            "sanity: the pause retires the bridge, which is R7b's own design",
        )

        controller.resume()

        assertNull(controller.get().composeReadingViewHost, "sanity: still not composed")
        assertTrue(
            ReadingViewVisibility.isVisible,
            "a reading host that is in front and owes a reading view IS the reading view as far as " +
                "HistoryManager is concerned — T8a item 2's handlePendingAgentResult() posts " +
                "AddHistoryItem from inside this very onResume, and a false flag there records a " +
                "wrong IntentHistoryItem instead of the verse",
        )
    }

    /**
     * The guard on the re-arm, from the other side: a host whose destination HAS composed and has
     * since been disposed (navigated away from, within the same host) must report false. An
     * unconditional re-arm passes the test above and fails this one — `HistoryManager` would record
     * a `KeyHistoryItem` on a Download screen and `goBack()` would never finish anything.
     *
     * `exit(activity)` is what the destination's own `DisposableEffect` calls on disposal
     * (`ReadingNavGraph.kt`), so this drives the production seam rather than a private field.
     */
    @Test
    fun aDisposedDestinationStillTurnsTheFlagOffAfterAResume() {
        val controller = hostOn(NavRoutes.READING).apply { create().start().resume().visible() }
        val activity = controller.get()
        assertNotNull(activity.composeReadingViewHost, "sanity: the destination composed")

        controller.pause()
        controller.resume()
        ReadingViewVisibility.exit(activity)

        assertFalse(
            ReadingViewVisibility.isVisible,
            "once the reading destination has composed, a resume must NOT re-arm the bridge: the " +
                "destination's own enter/exit owns the flag from then on, and a lingering bridge " +
                "would report a reading view on every other destination this host shows",
        )
    }

    /** A host that owns no reading workspace at all never arms the bridge, resumed or not. */
    @Test
    fun aHostOnANonReadingRouteNeverArmsTheBridge() {
        val controller = hostOn(NavRoutes.AI_TOOL_INFO).apply { create().start().resume() }
        controller.pause()
        controller.resume()

        assertFalse(
            ReadingViewVisibility.isVisible,
            "the re-arm is gated on this host having bootstrapped a reading workspace — " +
                "hostWindowRepository throws off the reading route by design",
        )
    }
}
