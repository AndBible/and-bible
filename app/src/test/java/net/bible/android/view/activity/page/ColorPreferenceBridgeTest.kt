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
import net.bible.android.view.activity.settings.TextDisplaySettingsComposeActivity
import net.bible.service.common.CommonUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [ColorPreference.openDialog] does not branch on `use_compose_ui` at all -- it launches the same
 * thing either way, which is what both halves below assert. The Compose routing decision lives in
 * `OptionsMenuStateBuilder.dispatch` (Settings editor sheets T11), which sends COLORS to the
 * reading view's in-place editor sheet before `openDialog` is ever called; those tests own that
 * contract.
 *
 * Slice S12 repointed the target: what `openDialog` launches is now
 * [TextDisplaySettingsComposeActivity] at its colours destination
 * ([TextDisplaySettingsComposeActivity.intentForColors]), not the deleted classic
 * `ColorSettingsActivity`, and it is a plain `startActivity` -- there is no `COLORS_CHANGED`
 * round-trip left, because the Compose destination writes each edit through as it is made. The
 * `EXTRA_START_AT_COLORS` assertion is what separates this from an ordinary text-settings launch:
 * without it the user would land on the settings LIST rather than on colours.
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
    fun colorPreferenceLaunchesComposeColorsWhenFlagOn() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val pref = ColorPreference(workspaceBundle())
        val activity = buildProbeActivity()

        pref.openDialog(activity, null, null)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(TextDisplaySettingsComposeActivity::class.java.name, started?.component?.className)
        assertTrue(
            "the launch must carry EXTRA_START_AT_COLORS, or it opens the text-settings list " +
                "instead of the colours destination",
            started!!.getBooleanExtra(TextDisplaySettingsComposeActivity.EXTRA_START_AT_COLORS, false),
        )
        assertEquals(
            "a WORKSPACE-level bundle must launch at workspace scope",
            "workspace",
            started.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_SCOPE_LEVEL),
        )
        // Robolectric records a plain startActivity as a for-result launch with requestCode -1,
        // so "no round-trip" is that sentinel rather than a missing record.
        assertEquals(
            "the COLORS_CHANGED round-trip is gone -- this must be a plain startActivity",
            -1,
            shadowOf(activity).nextStartedActivityForResult!!.requestCode,
        )
    }

    @Test
    fun colorPreferenceLaunchesComposeColorsWhenFlagOff() {
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        val pref = ColorPreference(workspaceBundle())
        val activity = buildProbeActivity()

        pref.openDialog(activity, null, null)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(TextDisplaySettingsComposeActivity::class.java.name, started?.component?.className)
        assertTrue(
            "the launch must carry EXTRA_START_AT_COLORS, or it opens the text-settings list " +
                "instead of the colours destination",
            started!!.getBooleanExtra(TextDisplaySettingsComposeActivity.EXTRA_START_AT_COLORS, false),
        )
        assertEquals(
            "a WORKSPACE-level bundle must launch at workspace scope",
            "workspace",
            started.getStringExtra(TextDisplaySettingsComposeActivity.EXTRA_SCOPE_LEVEL),
        )
        // Robolectric records a plain startActivity as a for-result launch with requestCode -1,
        // so "no round-trip" is that sentinel rather than a missing record.
        assertEquals(
            "the COLORS_CHANGED round-trip is gone -- this must be a plain startActivity",
            -1,
            shadowOf(activity).nextStartedActivityForResult!!.requestCode,
        )
    }
}
