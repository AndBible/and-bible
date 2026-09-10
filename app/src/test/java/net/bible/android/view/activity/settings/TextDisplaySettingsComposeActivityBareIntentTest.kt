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
package net.bible.android.view.activity.settings

import android.content.Intent
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The Compose-path successor to current-stable's `SettingsActivityMissingBundleTest` (#3867),
 * which pinned the fix on the classic `ColorSettingsActivity`/`TextDisplaySettingsActivity`/
 * `BackgroundImageChooserActivity` — all three deleted in Z-late slice S12, so that test cannot
 * compile here and was dropped when current-stable merged in.
 *
 * On classic the defect had two halves: the image chooser's Up caret let AppCompat synthesize a
 * bare Intent for its manifest `parentActivityName` (ColorSettingsActivity), and the settings
 * screens then NPEd out of `onCreate` on `!!` over the missing `settingsBundle` extra. Neither
 * half exists on the Compose path — colours and the image chooser are INTERNAL destinations of
 * [TextDisplaySettingsComposeActivity] rather than separate Activities, and its manifest parent is
 * MainBibleActivity, which needs no extras — but nothing pinned that, so this is what replaces it:
 * a bare Intent must open the GLOBAL scope and stay open, never crash and never finish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class TextDisplaySettingsComposeActivityBareIntentTest {

    @Test
    fun `a bare Intent opens global settings instead of crashing`() {
        val intent = Intent(RuntimeEnvironment.getApplication(), TextDisplaySettingsComposeActivity::class.java)

        val activity = Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).create().get()

        assertFalse(
            "A bare Intent (no scope extras) must fall back to SettingsScope.Global and keep the " +
                "screen open — classic's equivalent used to NPE out of onCreate (#3867).",
            activity.isFinishing,
        )
        assertNull(
            "Opening global settings must not launch anything else; a started activity here would " +
                "mean an up-navigation/re-launch path like the one #3867 was caused by.",
            shadowOf(activity).nextStartedActivity,
        )
    }
}
