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

import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.SettingsScope
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for Batch 12d-B T7: the host must render the internal Compose `colors` destination
 * (instead of the retired interim [ColorSettingsActivity] bridge) and wire the
 * [TextDisplaySettingsServiceImpl.imagePicker] seam to its registered `PickVisualMedia` launcher
 * during `onCreate`, before the Activity reaches RESUMED.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class TextDisplaySettingsComposeActivityColorsTest {

    @Test
    fun `host wires imagePicker seam and starts on the colors destination without crashing`() {
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        val repo = CommonUtils.windowControl.windowRepository
        val intent = TextDisplaySettingsComposeActivity.intentFor(
            org.robolectric.RuntimeEnvironment.getApplication(),
            SettingsScope.Workspace(repo.id.toString()),
            startDestination = "colors",
        )
        val controller = Robolectric.buildActivity(TextDisplaySettingsComposeActivity::class.java, intent).setup()
        val activity = controller.get()

        assertNotNull(
            "TextDisplaySettingsComposeActivity must set TextDisplaySettingsServiceImpl.imagePicker " +
                "(the PickVisualMedia seam) in onCreate so importBackgroundImage() has a launcher to call.",
            activity.serviceForTest.imagePicker,
        )
    }
}
