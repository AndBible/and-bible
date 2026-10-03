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
package net.bible.sharedcore.reading

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Reading-host re-typing R7b: a reading view is visible when a FOREGROUND host has registered
 * it.** [ReadingViewVisibility] still takes two kinds of registration — the destination's
 * ([ReadingViewVisibility.enter]/[ReadingViewVisibility.exit], owned by the `reading` destination's
 * `DisposableEffect`) and the Activity's ([ReadingViewVisibility.setActivityVisible], classic
 * `MainBibleActivity`'s lifecycle and `NavHostComposeActivity`'s bootstrap bridge) — but each is
 * keyed by its HOST's token and neither means "on screen" unless [ReadingHostPresence] says that
 * host is foreground.
 *
 * Every test here was rewritten by R7b, because the old rule this class pinned ("either input,
 * whoever registered it") is precisely the defect R7b fixes: a composition-scoped effect stays
 * entered while its host is in the background, which produced a dead back key under a classic
 * secondary Activity and volume keys dispatched into a reading view the user cannot see. The
 * composition of the inputs is still what the class is for, because the failure modes are silent in
 * both directions: one registration clobbering another turns the flag off under a reading view that
 * really is on screen (no `KeyHistoryItem`, and `HistoryManager.goBack` finishing the Activity),
 * and nothing else in the tree would fail. The two ends are tested where they are wired:
 * `ReadingHistoryAnchorTest` for the Activity call sites, `ReadingDestinationInGraphTest` for the
 * destination's `DisposableEffect`.
 *
 * Both objects are process-wide singletons, so each test resets them at both ends.
 */
class ReadingViewVisibilityTest {

    /** A host token, standing in for the Activity the production code passes as `this`. */
    private val host = Any()

    @BeforeTest
    fun reset() = resetSeams()

    @AfterTest
    fun clear() = resetSeams()

    private fun resetSeams() {
        ReadingViewVisibility.setVisible(false)
        ReadingHostPresence.setForeground(null)
    }

    private fun handlersFor(name: String) = ReadingViewHostHandlers(
        onKey = { false },
        onScreenTurnedOn = { lastScreenOn = name },
        onScreenTurnedOff = { },
    )

    private var lastScreenOn: String? = null

    /**
     * Either registration alone is enough while its host is foreground, and neither one's removal
     * clears the other's. Rewritten by R7b: the old version drove the inputs with no host at all,
     * which is the rule R7b replaced. Mutations that make this RED: fold `setActivityVisible` into
     * the destination list (the Activity's `false` then clears the composed destination); or drop
     * `activityHosts` from `isVisible` (the Activity input stops being an input at all).
     */
    @Test
    fun theTwoInputsCompose() {
        ReadingHostPresence.setForeground(host)

        // The destination alone, with the Activity input off.
        ReadingViewVisibility.enter(host)
        assertTrue(ReadingViewVisibility.isVisible, "a composed destination alone makes it visible")

        // The Activity arriving on top of it changes nothing…
        ReadingViewVisibility.setActivityVisible(host, true)
        assertTrue(ReadingViewVisibility.isVisible)

        // …and the Activity going away does not clear the destination.
        ReadingViewVisibility.setActivityVisible(host, false)
        assertTrue(
            ReadingViewVisibility.isVisible,
            "the Activity input going false must not clear a composed destination",
        )

        ReadingViewVisibility.exit(host)
        assertFalse(ReadingViewVisibility.isVisible, "both inputs off — and only then is it false")

        // The mirror image: the Activity alone, and a destination's exit must not clear IT. A
        // SECOND host's destination, since the same host's `enter` retires its own bridge.
        val other = Any()
        ReadingViewVisibility.setActivityVisible(host, true)
        assertTrue(ReadingViewVisibility.isVisible, "the Activity input alone makes it visible")

        ReadingViewVisibility.enter(other)
        ReadingViewVisibility.exit(other)
        assertTrue(
            ReadingViewVisibility.isVisible,
            "another host's balanced enter/exit must not clear the resumed Activity",
        )

        ReadingViewVisibility.setActivityVisible(host, false)
        assertFalse(ReadingViewVisibility.isVisible)
    }

    /**
     * **The dead-back-key divergence, as behaviour (R7b).** With the destination composed and its
     * host backgrounded under a classic secondary Activity, `depth > 0` used to keep `isVisible`
     * true, so `HistoryManager.goBack()`'s `finish()` never fired and `ActivityBase.onBackPressed`
     * returned without `super` — a dead back key on the secondary screen. Mutation: drop the
     * [ReadingHostPresence] gate from `isVisible` and the second assertion fails.
     */
    @Test
    fun aBackgroundedHostsComposedDestinationDoesNotCountAsVisible() {
        ReadingHostPresence.setForeground(host)
        ReadingViewVisibility.enter(host)
        assertTrue(ReadingViewVisibility.isVisible)

        ReadingHostPresence.setForeground(null)   // a classic secondary Activity came to the front
        assertFalse(ReadingViewVisibility.isVisible, "a backgrounded host's destination is not visible")

        // …and it comes back when the host does, without the destination doing anything: this is
        // why `isVisible` is computed rather than published from enter/exit.
        ReadingHostPresence.setForeground(host)
        assertTrue(ReadingViewVisibility.isVisible, "the same destination, its host back in front")
    }

    /**
     * The same gate on the Activity input (R7b). `MainBibleActivity`'s `onPause` clears its own
     * input, but a SECOND instance left resumed in another task would otherwise keep the flag on
     * for an instance the user cannot see.
     */
    @Test
    fun aBackgroundedHostsActivityInputDoesNotCountAsVisible() {
        ReadingHostPresence.setForeground(host)
        ReadingViewVisibility.setActivityVisible(host, true)
        assertTrue(ReadingViewVisibility.isVisible)

        ReadingHostPresence.setForeground(Any())  // another host came to the front
        assertFalse(
            ReadingViewVisibility.isVisible,
            "a backgrounded host's Activity input is not what the user is looking at",
        )
    }

    /**
     * **R7b: `ReadingViewHostCallbacks.current` resolves through the same presence**, so the two
     * seams cannot disagree about which reading view is on screen. Volume keys and screen-state
     * broadcasts arrive at the FOREGROUND Activity and must not be dispatched into a backgrounded
     * destination's handlers — nor may `enableGenericVolumeScroll`, which the host derives from
     * `current == null`, be reported for one.
     */
    @Test
    fun theHandlersOfABackgroundedHostAreNotCurrent() {
        val bg = Any(); val fg = Any()
        val unpublishBg = ReadingViewHostCallbacks.publish(handlersFor("bg"), bg)
        ReadingHostPresence.setForeground(fg)
        assertNull(
            ReadingViewHostCallbacks.current,
            "'last published' must not mean 'foreground' — volume keys arrive at the foreground " +
                "Activity and must not be dispatched into a backgrounded destination",
        )

        // The foreground host's own reading view IS current, even though it published later…
        val unpublishFg = ReadingViewHostCallbacks.publish(handlersFor("fg"), fg)
        assertNotNull(ReadingViewHostCallbacks.current).onScreenTurnedOn()
        assertEquals("fg", lastScreenOn, "…and they are the foreground host's own handlers")

        // …and when the backgrounded host comes back, ITS handlers are current again.
        unpublishFg()
        ReadingHostPresence.setForeground(bg)
        assertNotNull(ReadingViewHostCallbacks.current).onScreenTurnedOn()
        assertEquals("bg", lastScreenOn)

        unpublishBg()
        assertEquals(0, ReadingViewHostCallbacks.publishedCount, "…and nothing is left published")
    }

    /**
     * **The bootstrap bridge is RETIRED by the host's own composed destination (R7b).**
     * `NavHostComposeActivity.bootstrapIfNeeded()` declares the Activity input before the deep link
     * it dispatches, because the destination's effect cannot run that early; the destination's
     * `enter(host)` then takes over. Retiring rather than shadowing is what makes navigating from
     * `reading` to a sibling destination of the same host turn the flag OFF — with a bridge that
     * merely stopped counting, the second assertion would pass and the third would fail, and
     * `HistoryManager` would record a `KeyHistoryItem` on a Download screen.
     */
    @Test
    fun aComposedDestinationRetiresItsOwnHostsBootstrapBridge() {
        ReadingHostPresence.setForeground(host)
        ReadingViewVisibility.setActivityVisible(host, true)
        assertTrue(ReadingViewVisibility.isVisible, "the bridge covers the pre-composition window")

        ReadingViewVisibility.enter(host)
        assertTrue(ReadingViewVisibility.isVisible, "…and the destination takes over seamlessly")

        ReadingViewVisibility.exit(host)
        assertFalse(
            ReadingViewVisibility.isVisible,
            "a sibling destination of the same host is NOT the reading view — the bridge must not " +
                "come back when the reading destination is disposed",
        )
    }

    /**
     * The destination input counts registrations per host, and an unbalanced
     * [ReadingViewVisibility.exit] must not remove one that was never made — a removal that went
     * "below zero" would hide the caller's bug behind a flag that can no longer be turned on.
     * Mutation: make `exit` clear every registration for the host and the first assertion fails.
     */
    @Test
    fun theDepthCounterIsBalancedAndNeverGoesNegative() {
        ReadingHostPresence.setForeground(host)
        ReadingViewVisibility.enter(host)
        ReadingViewVisibility.enter(host)
        ReadingViewVisibility.exit(host)
        assertTrue(ReadingViewVisibility.isVisible, "two in, one out — one reading view is still on screen")

        ReadingViewVisibility.exit(host)
        ReadingViewVisibility.exit(host) // unbalanced, i.e. a bug in some caller
        assertFalse(ReadingViewVisibility.isVisible)

        ReadingViewVisibility.enter(host)
        assertTrue(ReadingViewVisibility.isVisible, "one enter must still be enough to turn it on")
    }

    /**
     * Two hosts' registrations are independent: an `exit` removes the registration of the host that
     * made it and no other (`FLAG_ACTIVITY_MULTIPLE_TASK` can make a second reading instance real).
     * R7b makes this stronger than the old depth counter did — the host token is now what tells the
     * two apart.
     *
     * **The FOREGROUND host is the one that exits here, and that is deliberate (fix round 1).** The
     * first version of this test exited the backgrounded instance first and named
     * `destinations.removeLast()` as its mutation — which does not fail it, because the last
     * registration made happened to be the one being removed. Running the named mutation is what
     * found that (it passed); exiting the FOREGROUND host while a LATER, backgrounded registration
     * is still held distinguishes the two, in both directions: a host-blind `exit` removes the
     * wrong one, so the flag stays on for a reading view that is gone (first assertion) and the
     * still-composed one has been silently un-registered (second).
     */
    @Test
    fun oneHostsRegistrationsAreNotAnothersToRemove() {
        val other = Any()
        ReadingHostPresence.setForeground(host)
        ReadingViewVisibility.enter(host)
        ReadingViewVisibility.enter(other)   // a second instance, registered LATER

        ReadingViewVisibility.exit(host)
        assertFalse(
            ReadingViewVisibility.isVisible,
            "the foreground host's own destination is gone — another host's registration must not " +
                "stand in for it",
        )

        // …and that other registration is untouched: it counts the moment its host is in front.
        ReadingHostPresence.setForeground(other)
        assertTrue(
            ReadingViewVisibility.isVisible,
            "the other instance was never un-registered — only the foreground host's was removed",
        )

        ReadingViewVisibility.exit(other)
        assertFalse(ReadingViewVisibility.isVisible, "…and the last one out does clear it")
    }

    /**
     * `setVisible` is the TEST-only forcing setter. It ignores both inputs AND the foreground host,
     * because the tests that use it (`ReadingHistoryAnchorTest`'s `HistoryManager` cases) drive the
     * predicate directly rather than composing a destination, and it CLEARS both inputs, because
     * otherwise one test's registration leaks into the next test's fixture.
     *
     * R7b changed the "true" half: it used to mean "a depth of exactly one", so a following `exit()`
     * landed on false. It cannot any more — a forced `true` belongs to no host, and `exit` needs
     * one. Forcing is now a state of its own, cleared only by another `setVisible`. Mutation: drop
     * the `destinations.clear()`/`activityHosts.clear()` from `setVisible` and the first assertion
     * fails.
     */
    @Test
    fun theTestOnlyResetClearsBothInputs() {
        ReadingHostPresence.setForeground(host)
        ReadingViewVisibility.setActivityVisible(host, true)
        ReadingViewVisibility.enter(host)

        ReadingViewVisibility.setVisible(false)
        assertFalse(ReadingViewVisibility.isVisible, "a reset must clear both registrations as well")

        // …and `true` forces it on with no host in the picture at all.
        ReadingHostPresence.setForeground(null)
        ReadingViewVisibility.setVisible(true)
        assertTrue(ReadingViewVisibility.isVisible, "the forcing setter ignores the foreground host")

        ReadingViewVisibility.setVisible(false)
        assertFalse(ReadingViewVisibility.isVisible)
    }
}
