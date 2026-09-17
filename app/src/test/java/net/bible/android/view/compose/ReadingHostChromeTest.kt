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
import net.bible.android.view.activity.page.MainBibleActivity
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
 * R4: both hosts implement the reading-host contract, and the nav host's chrome is real.
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
    fun bothActivitiesAreReadingHosts() {
        assertTrue(
            "MainBibleActivity must implement ReadingHostActivity",
            ReadingHostActivity::class.java.isAssignableFrom(MainBibleActivity::class.java),
        )
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
        host.restorePaneFocus()
        // No drawer on this host until slice 7 Task 11; it must do nothing rather than crash.
        host.toggleDrawer()
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
        // Any route but READING: the reading destination's production content slot is the one thing
        // the host cannot build yet (R8 pays that), and none of these assertions need it.
        NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.AI_TOOL_INFO),
    ).create().get()
}
