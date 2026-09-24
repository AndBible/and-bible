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
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R4: the nav host implements the reading-host contract, and its chrome is real. (Slice 8 F2: the
 * classic `MainBibleActivity` half of the contract assertion went with that class.)
 *
 * `ComposeReadingViewHost` is re-typed onto [ReadingHostActivity] at R6, which is the point at which
 * a missing member on EITHER Activity becomes a compile error. Until then these two assertions are
 * the only thing that notices, so they are deliberately about the contract itself rather than about
 * any one screen's behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingHostChromeTest {
    @Test
    fun theNavHostIsAReadingHost() {
        // Slice 8 F2: was `bothActivitiesAreReadingHosts`; its MainBibleActivity half went with the
        // class (spec §5.3). The nav host is the one reading host left.
        assertTrue(
            "NavHostComposeActivity must implement ReadingHostActivity",
            ReadingHostActivity::class.java.isAssignableFrom(NavHostComposeActivity::class.java),
        )
    }

    @Test
    fun theNavHostsFullScreenFlagIsTheSharedOneClassicUses() {
        // MainBibleActivity.toggleFullScreen delegates to SharedActivityState.instance, so the bit
        // is process-wide already; a nav host with its OWN boolean would silently disagree with the
        // reading view's own FullScreenEvent subscribers.
        val host = buildNavHost()
        try {
            val before = SharedActivityState.instance.isFullScreen
            host.fullScreen = !before
            assertEquals(!before, SharedActivityState.instance.isFullScreen)
            assertEquals(!before, host.fullScreen)
        } finally {
            SharedActivityState.instance.let { if (it.isFullScreen) host.fullScreen = false }
        }
    }

    /**
     * The chrome the nav host had NONE of before R4: the fullscreen hide/show pair, reached through
     * the two interface members the Compose drawer's edges call. A Robolectric window has no real
     * `WindowInsetsController`, so what is assertable here is that the calls are wired and do not
     * throw — a `TODO()`/`error(...)` stub or a member that never got ported fails this.
     */
    @Test
    fun theNavHostsChromeCallsAreWiredAndSurviveARobolectricWindow() {
        val host = buildNavHost()
        host.showSystemUiTransient()
        host.applyIdleSystemUi()
        // `restorePaneFocus` is deliberately NOT called here any more: R8 pointed it at this host's
        // OWN repository, which off the reading route does not exist. Its own test is
        // [restorePaneFocusReachesThisHostsOwnRepositoryAndIsNotSwallowedByItsGate].
        //
        // `toggleDrawer` IS still called, and is no longer the no-op this line used to assert: R8
        // made it classic's one-line delegation, which with no reading view installed takes
        // `composeToggleDrawer`'s native fallback -- this host's documented no-op, since it has no
        // `DrawerLayout` at all.
        host.toggleDrawer()
    }

    /**
     * Reading-host re-typing R8, both halves of [NavHostComposeActivity.restorePaneFocus] at once,
     * and neither half is assertable any other way on a Robolectric host.
     *
     *  - **The repository identity.** R8 re-pointed the body from `windowControl.windowRepository`
     *    -- whichever reading host most recently RESUMED -- to `hostWindowRepository`, this host's
     *    own (R6c1's identity finding). Off the reading route this host bootstrapped none, so the
     *    honest answer is the same loud failure
     *    [theNavHostsRepositoryIsItsOwnOrNothing] pins for the property itself. **Mutation: put
     *    `windowControl.windowRepository` back and nothing throws** -- `WindowControl`'s lazy
     *    fallback always answers -- which is precisely the defect: focus handed to the OTHER host's
     *    active window.
     *  - **The gate's POLARITY.** R8 also added classic's
     *    `shouldRestorePaneFocusOnDrawerClose(searchBarOpen = readingCommands.composeSearchModeActive)`
     *    guard, which R4 had to leave out. With no reading view installed the flag is false, so the
     *    call must fall THROUGH the gate and reach the repository. Mutation: invert the gate (drop
     *    the `!`) and this stops throwing.
     *
     * The gate's other branch -- a drawer close while the reading view's search bar is open must
     * NOT steal focus -- is the shared predicate's own property and is pinned in `:sharedCore`'s
     * `DrawerPaneFocusTest`; it needs a composed reading view in search mode to reach from here,
     * and the act it suppresses (a `requestFocus` on a `BibleView`) has no observable effect in a
     * Robolectric window either way.
     */
    @Test
    fun restorePaneFocusReachesThisHostsOwnRepositoryAndIsNotSwallowedByItsGate() {
        val host = buildNavHost()
        assertThrows(
            "restorePaneFocus must hand focus to THIS host's active window, and must not be " +
                "gated off while no search bar is open",
            UninitializedPropertyAccessException::class.java,
        ) { host.restorePaneFocus() }
    }

    /** `getString` is part of the contract so `activity.getString(...)` keeps its spelling at R6. */
    @Test
    fun theContractsGetStringResolvesThroughTheHost() {
        val host: ReadingHostActivity = buildNavHost()
        assertTrue(host.getString(net.bible.android.activity.R.string.app_name_andbible).isNotEmpty())
        assertEquals(host, host.hostContext)
    }

    /**
     * R6d widened the contract from seven members to twelve, and the whole argument for widening it
     * rather than taking a deps record was that `NavHostComposeActivity` can answer every addition
     * HONESTLY. This is that claim, executed: a member answered with `TODO()`, an `error(...)`, or
     * a construction this host cannot perform fails here, on a host built off the reading route.
     *
     * `assertSame` on the two collaborators is not a tautology: both are `by lazy` on this host
     * precisely so the reading destination gets ONE command surface and ONE inset ledger. A
     * `get() = ReadingCommands(...)` would satisfy the interface and hand out a second
     * `BibleViewFactory` on every read -- the silent per-host-object bug
     * `ReadingCommandsDelegationTest` pins on the classic side.
     */
    @Test
    fun theNavHostAnswersTheFiveMembersR6dAddedToTheContract() {
        val host: ReadingHostActivity = buildNavHost()
        assertSame("hostActivity is the host itself, never a cast", host, host.hostActivity)
        assertSame("the command surface must be ONE per host", host.readingCommands, host.readingCommands)
        assertSame("the inset ledger must be ONE per host", host.readingInsets, host.readingInsets)
        // No classic toolbar row on this host: the interface's default no-op, not a crash.
        host.hideClassicToolbarRow()
    }

    /**
     * The other half of [theNavHostAnswersTheFiveMembersR6dAddedToTheContract], and the one that
     * would be easiest to get wrong quietly: `hostWindowRepository` must be THIS host's own
     * repository or nothing at all. Off the reading route this host never bootstrapped one, so the
     * honest answer is to fail loudly -- NOT to fall back to `windowControl.windowRepository`,
     * which is whichever reading host most recently resumed and is exactly the identity defect
     * R6c1 found and R6d discharges.
     */
    @Test
    fun theNavHostsRepositoryIsItsOwnOrNothing() {
        val host: ReadingHostActivity = buildNavHost()
        assertThrows(
            "off the reading route this host has no repository of its own, and must not answer " +
                "with another host's",
            UninitializedPropertyAccessException::class.java,
        ) { host.hostWindowRepository }
    }

    private fun buildNavHost(): NavHostComposeActivity = Robolectric.buildActivity(
        NavHostComposeActivity::class.java,
        // Any route but READING, and after R8 that is a POSITIVE choice rather than the "the host
        // cannot build the reading view yet" it used to be: these assertions are about what this
        // host answers when it is NOT a reading host -- `hostWindowRepository` absent,
        // `composeReadingViewHost` null -- which is the half a reading-route fixture cannot show.
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
    ).create().get()
}
