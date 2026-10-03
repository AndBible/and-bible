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

import android.os.Bundle
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [ColorPreference.openDialog] does not branch at all -- it launches the same thing regardless.
 * (It never consulted the old `use_compose_ui` setting either, which is what this paragraph used
 * to say.) The Compose routing decision lives in `OptionsMenuStateBuilder.dispatch`
 * (Settings editor sheets T11), which sends COLORS to the reading view's in-place editor sheet
 * before `openDialog` is ever called; those tests own that contract.
 *
 * Slice S12 repointed the target: what `openDialog` launches is now
 * [net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity] at its colours
 * destination, not the deleted classic `ColorSettingsActivity`, and it is a plain `startActivity` --
 * there is no `COLORS_CHANGED` round-trip left, because the Compose destination writes each edit
 * through as it is made. Slice 8 B6 repointed it again: the colours destination is a route of the
 * nav graph, `textDisplaySettingsRoute(scope, startAtColors = true)`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ColorPreferenceBridgeTest {

    /** A minimal, non-manifest [ActivityBase] stand-in -- same pattern as
     *  `ComposeHostActionBarTest.ActionBarProbeActivity` -- just enough to receive
     *  `startActivity`/`startActivityForResult` calls under Robolectric. */
    class ProbeActivity : ActivityBase() {
        override val doNotInitializeApp = true
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
        }
    }

    private fun workspaceBundle() = SettingsBundle(level = SettingsLevel.WORKSPACE)

    private fun windowBundle() = SettingsBundle(
        level = SettingsLevel.WINDOW,
        workspaceId = IdType(),
        windowId = IdType(),
    )

    private fun buildProbeActivity() = Robolectric.buildActivity(ProbeActivity::class.java).setup().get()

    @Test
    fun colorPreferenceOpensTheComposeColoursDestination() {
        val pref = ColorPreference(workspaceBundle())
        val activity = buildProbeActivity()

        pref.openDialog(activity, null, null)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(NavHostComposeActivity::class.java.name, started?.component?.className)
        val args = NavRoutes.readTextDisplaySettings(started!!.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE)!!)
        assertTrue(
            "the route must carry startAtColors, or it opens the text-settings list instead of colours",
            args.startAtColors,
        )
        assertEquals("a WORKSPACE-level bundle must open at workspace scope", "workspace", args.scopeLevel)
        assertEquals(
            "the COLORS_CHANGED round-trip is gone -- this must be a plain startActivity",
            -1,
            shadowOf(activity).nextStartedActivityForResult?.requestCode,
        )
    }

    /** The WINDOW arm was reachable from `SplitBibleArea` until this batch deleted it, and is
     *  still reachable from the window-pane menu. S12 left it unasserted (spec F.9); assert it
     *  here so a WINDOW-scoped launch is covered alongside the WORKSPACE one above. */
    @Test
    fun colorPreferenceOpensTheComposeColoursDestinationForAWindowScopedBundle() {
        val bundle = windowBundle()
        val pref = ColorPreference(bundle)
        val activity = buildProbeActivity()

        pref.openDialog(activity, null, null)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(NavHostComposeActivity::class.java.name, started?.component?.className)
        val args = NavRoutes.readTextDisplaySettings(started!!.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE)!!)
        assertTrue(args.startAtColors)
        assertEquals("a WINDOW-level bundle must open at window scope", "window", args.scopeLevel)
        assertEquals("the window id must be carried through", bundle.windowId.toString(), args.windowId)
    }
}
