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
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.settings.ColorSettingsActivity
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings editor sheets T12: [ColorPreference.openDialog] no longer branches on
 * `use_compose_ui` at all -- it always launches the classic [ColorSettingsActivity] (unchanged
 * `startActivityForResult`/`COLORS_CHANGED` round-trip), regardless of the flag. The Compose
 * routing decision moved to `OptionsMenuStateBuilder.dispatch` (Settings editor sheets T11),
 * which sends COLORS to the reading view's in-place editor sheet before `openDialog` is ever
 * called; those tests own that contract. This test only guards that `openDialog` itself stayed
 * a plain, flag-independent classic launch after the dead Compose branch (and the
 * `startDestination = "colors"` extra it used to send) were deleted.
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

    @After
    fun tearDown() {
        CommonUtils.settings.removeBoolean("use_compose_ui")
    }

    private fun workspaceBundle() = SettingsBundle(level = SettingsLevel.WORKSPACE)

    private fun buildProbeActivity() = Robolectric.buildActivity(ProbeActivity::class.java).setup().get()

    @Test
    fun colorPreferenceLaunchesClassicWhenFlagOn() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val pref = ColorPreference(workspaceBundle())
        val activity = buildProbeActivity()

        pref.openDialog(activity, null, null)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ColorSettingsActivity::class.java.name, started?.component?.className)
        val forResult = shadowOf(activity).nextStartedActivityForResult
        assertEquals(MainBibleActivity.COLORS_CHANGED, forResult?.requestCode)
    }

    @Test
    fun colorPreferenceLaunchesClassicWhenFlagOff() {
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        val pref = ColorPreference(workspaceBundle())
        val activity = buildProbeActivity()

        pref.openDialog(activity, null, null)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ColorSettingsActivity::class.java.name, started?.component?.className)
        val forResult = shadowOf(activity).nextStartedActivityForResult
        assertEquals(MainBibleActivity.COLORS_CHANGED, forResult?.requestCode)
    }
}
