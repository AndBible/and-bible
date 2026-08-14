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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.SettingsScope
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A/B feedback round 5, task 2: the Compose colour editor writes
 * `repo.textDisplaySettings.colors` (and friends) directly, bypassing `Preference.value`'s
 * setter — the only site that used to call [CommonUtils.displaySettingChanged] — so changing
 * colours never made COLORS show up as a recently-used setting in the overflow menu, though the
 * classic UI's [ColorSettingsActivity] path did (it goes through the same MainBibleActivity
 * COLORS_CHANGED write-back the doc comment on `applyColors` references, plus a direct call to
 * `displaySettingChanged`). This test constructs [TextDisplaySettingsServiceImpl] directly
 * (following [TextDisplaySettingsServiceImplColorsTest]'s setup), which is the honest route: the
 * service builds cleanly in the unit-test environment with only a fresh [WindowRepository], so no
 * fallback to testing an extracted-and-isolated tail was needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ColorSettingsRecentActionTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private val impl = TextDisplaySettingsServiceImpl()

    private fun recentTypes() = CommonUtils.lastDisplaySettingsSorted

    @Before fun setUp() {
        windowControl = CommonUtils.windowControl
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = repo
        windowControl.activeWindow // auto-creates window 1 + workspace
        CommonUtils.settings.setString("lastDisplaySettings", null)
    }

    @After fun tearDown() {
        CommonUtils.settings.setString("lastDisplaySettings", null)
        DatabaseResetter.resetDatabase()
    }

    @Test
    fun `changing a workspace colour records COLORS as a recently used setting`() {
        assertFalse(recentTypes().contains(WorkspaceEntities.TextDisplaySettings.Types.COLORS))

        impl.setWorkspaceColor(SettingsScope.Workspace(repo.id.toString()), 0xFF3366CCu.toInt())

        assertTrue(recentTypes().contains(WorkspaceEntities.TextDisplaySettings.Types.COLORS))
    }

    @Test
    fun `resetting the workspace colours records COLORS too`() {
        assertFalse(recentTypes().contains(WorkspaceEntities.TextDisplaySettings.Types.COLORS))

        impl.resetColors(SettingsScope.Workspace(repo.id.toString()))

        assertTrue(recentTypes().contains(WorkspaceEntities.TextDisplaySettings.Types.COLORS))
    }
}
