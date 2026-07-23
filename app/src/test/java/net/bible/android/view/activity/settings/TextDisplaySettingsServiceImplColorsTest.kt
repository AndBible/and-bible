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
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.defaultWorkspaceColor
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.SettingsScope
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TextDisplaySettingsServiceImplColorsTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private val impl = TextDisplaySettingsServiceImpl()

    @Before fun setUp() {
        windowControl = CommonUtils.windowControl
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = repo
        windowControl.activeWindow    // auto-creates window 1 + workspace
    }
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    @Test fun setColorAtWorkspaceWritesWorkspaceColorsAndKeepsOtherFields() {
        val scope = SettingsScope.Workspace(repo.id.toString())
        impl.setColor(scope, ColorField.DAY_TEXT, 0x123456 or (0xFF shl 24))
        val stored = repo.textDisplaySettings.colors!!
        assertEquals(0x123456 or (0xFF shl 24), stored.dayTextColor)
        assertEquals(TextDisplaySettings.white, stored.dayBackground)   // untouched merged field preserved
    }

    @Test fun setWorkspaceColorPersistsIntoWorkspaceSettings() {
        val scope = SettingsScope.Workspace(repo.id.toString())
        impl.setWorkspaceColor(scope, -65536)
        assertEquals(-65536, repo.workspaceSettings.workspaceColor)
    }

    @Test fun setColorAtWindowWritesPageManagerColorsOnly() {
        val w = repo.activeWindow
        val scope = SettingsScope.Window(w.id.toString(), repo.id.toString())
        impl.setColor(scope, ColorField.NIGHT_BACKGROUND, -1)
        assertEquals(-1, w.pageManager.textDisplaySettings.colors!!.nightBackground)
    }

    @Test fun loadColorsHidesWorkspaceColorAtWindowScope() {
        val w = repo.activeWindow
        assertFalse(impl.loadColors(SettingsScope.Window(w.id.toString(), repo.id.toString())).workspaceColorVisible)
        assertTrue(impl.loadColors(SettingsScope.Workspace(repo.id.toString())).workspaceColorVisible)
    }

    @Test fun setBackgroundImageAndOpacityRoundTripThroughLoad() {
        val scope = SettingsScope.Workspace(repo.id.toString())
        impl.setBackgroundImage(scope, night = false, initials = "BGIMG_x")
        impl.setBackgroundOpacity(scope, night = false, opacity = 40)
        val snap = impl.loadColors(scope)
        assertEquals("BGIMG_x", snap.dayBackgroundImageInitials)
        assertEquals(40, snap.dayBackgroundImageOpacity)
    }

    @Test fun resetColorsAtWorkspaceRestoresDefaults() {
        val scope = SettingsScope.Workspace(repo.id.toString())
        impl.setColor(scope, ColorField.DAY_TEXT, -65536)
        impl.setWorkspaceColor(scope, -65536)
        impl.resetColors(scope)
        assertEquals(TextDisplaySettings.default.colors!!.dayTextColor, repo.textDisplaySettings.colors!!.dayTextColor)
        assertEquals(defaultWorkspaceColor, repo.workspaceSettings.workspaceColor)
    }

    @Test fun resetColorsAtWindowClearsToInherit() {
        val w = repo.activeWindow
        val scope = SettingsScope.Window(w.id.toString(), repo.id.toString())
        impl.setColor(scope, ColorField.NIGHT_BACKGROUND, -1)
        impl.resetColors(scope)
        assertNull(w.pageManager.textDisplaySettings.colors)
    }
}
