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
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.SettingsBundle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenuItem

/**
 * Regression tests for #3867. `BackgroundImageChooserActivity` declares
 * `parentActivityName=ColorSettingsActivity` in the manifest, so AppCompat showed an Up caret whose
 * default handling synthesizes a bare Intent for the parent — no `settingsBundle` extra. Color
 * Settings then NPEd out of `onCreate` (`!!` on the missing extra) and the app crashed.
 *
 * Both halves of the fix are pinned here: the chooser no longer triggers up-navigation, and the
 * settings screens survive a bare Intent from any other source (task restore, external launch).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SettingsActivityMissingBundleTest {
    private fun intentFor(cls: Class<*>, withBundle: Boolean) =
        Intent(RuntimeEnvironment.getApplication(), cls).apply {
            if(withBundle) putExtra("settingsBundle", SettingsBundle().toJson())
        }

    @Test
    fun colorSettingsWithoutSettingsBundleFinishesInsteadOfCrashing() {
        val activity = Robolectric
            .buildActivity(ColorSettingsActivity::class.java, intentFor(ColorSettingsActivity::class.java, false))
            .create()
            .get()

        assertTrue("ColorSettingsActivity must finish when launched without a settingsBundle", activity.isFinishing)
    }

    @Test
    fun textDisplaySettingsWithoutSettingsBundleFinishesInsteadOfCrashing() {
        val activity = Robolectric
            .buildActivity(
                TextDisplaySettingsActivity::class.java,
                intentFor(TextDisplaySettingsActivity::class.java, false)
            )
            .create()
            .get()

        assertTrue(
            "TextDisplaySettingsActivity must finish when launched without a settingsBundle",
            activity.isFinishing
        )
    }

    /** The guard must not swallow the normal case: with a bundle the screen opens as before. */
    @Test
    fun colorSettingsWithSettingsBundleOpensNormally() {
        val activity = Robolectric
            .buildActivity(ColorSettingsActivity::class.java, intentFor(ColorSettingsActivity::class.java, true))
            .create()
            .get()

        assertFalse("A settingsBundle was given, so the screen must stay open", activity.isFinishing)
    }

    /**
     * Up in the image chooser must cancel it, not navigate to the manifest parent — that
     * navigation is what produced the extras-less ColorSettingsActivity Intent.
     *
     * What is actually pinned is that the chooser consumes `android.R.id.home` itself: AppCompat's
     * real up-navigation (`NavUtils.navigateUpFromSameTask`) does not run under Robolectric, so an
     * unhandled item is as close as a unit test gets to reproducing the device behaviour.
     */
    @Test
    fun upInBackgroundImageChooserCancelsWithoutRelaunchingColorSettings() {
        val controller = Robolectric.buildActivity(BackgroundImageChooserActivity::class.java).create()
        val activity = controller.get()

        val handled = activity.onOptionsItemSelected(RoboMenuItem(android.R.id.home))

        assertTrue("Up must be handled by the chooser itself", handled)
        assertTrue("Up must finish the chooser", activity.isFinishing)
        assertNull(
            "Up must not launch anything; a started activity here means up-navigation ran and " +
                "ColorSettingsActivity got a bare Intent again",
            shadowOf(activity).nextStartedActivity,
        )
    }
}
