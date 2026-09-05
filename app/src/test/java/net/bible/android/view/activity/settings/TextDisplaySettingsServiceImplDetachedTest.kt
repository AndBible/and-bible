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
import net.bible.android.database.IdType
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TextDisplaySettingsServiceImplDetachedTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository

    @Before fun setUp() {
        windowControl = CommonUtils.windowControl
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = repo
        windowControl.activeWindow    // auto-creates window 1 + workspace
    }
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    private fun detachedBundle(name: String = "Other workspace"): SettingsBundle =
        SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceId = IdType(), workspaceName = name,
            workspaceSettings = WorkspaceEntities.TextDisplaySettings(),
            globalSettings = CommonUtils.globalTextDisplaySettings,
        )

    @Test
    fun detachedLoadReadsTheNamedWorkspaceNotTheActiveOne() {
        val bundle = detachedBundle()
        val edit = DetachedWorkspaceEdit(bundle)
        val service = TextDisplaySettingsServiceImpl(edit)
        val snapshot = service.loadText(SettingsScope.Workspace(bundle.workspaceId.toString()))
        assertEquals("Other workspace", snapshot.workspaceName)
    }

    @Test
    fun detachedWriteDoesNotTouchTheActiveWorkspace() {
        val edit = DetachedWorkspaceEdit(detachedBundle())
        val service = TextDisplaySettingsServiceImpl(edit)
        val activeBefore = CommonUtils.windowControl.windowRepository.textDisplaySettings.strongsMode
        service.setValue(
            SettingsScope.Workspace(edit.bundle.workspaceId.toString()),
            TextSettingType.STRONGS, TextSettingValue.IntValue(2),
        )
        assertEquals(
            "detached edit leaked into the ACTIVE workspace",
            activeBefore, CommonUtils.windowControl.windowRepository.textDisplaySettings.strongsMode,
        )
        assertTrue("the edit did not reach the detached bundle", edit.dirty)
    }

    @Test
    fun detachedWriteLandsInTheDetachedBundle() {
        val edit = DetachedWorkspaceEdit(detachedBundle())
        val service = TextDisplaySettingsServiceImpl(edit)
        service.setValue(
            SettingsScope.Workspace(edit.bundle.workspaceId.toString()),
            TextSettingType.STRONGS, TextSettingValue.IntValue(2),
        )
        assertEquals(2, edit.bundle.workspaceSettings.strongsMode)
    }

    @Test
    fun detachedResetClearsTheBundleAndFlagsReset() {
        val edit = DetachedWorkspaceEdit(detachedBundle())
        val service = TextDisplaySettingsServiceImpl(edit)
        val scope = SettingsScope.Workspace(edit.bundle.workspaceId.toString())
        service.setValue(scope, TextSettingType.STRONGS, TextSettingValue.IntValue(2))
        service.reset(scope)
        assertTrue(edit.reset)
        assertEquals(
            WorkspaceEntities.TextDisplaySettings().strongsMode,
            edit.bundle.workspaceSettings.strongsMode,
        )
    }

    @Test
    fun anUntouchedDetachedEditReportsNoChange() {
        val edit = DetachedWorkspaceEdit(detachedBundle())
        val service = TextDisplaySettingsServiceImpl(edit)
        service.loadText(SettingsScope.Workspace(edit.bundle.workspaceId.toString()))
        assertFalse("merely loading must not mark the edit changed -- plan D3", edit.changed)
    }

    @Test
    fun detachedModeRejectsAWindowScope() {
        val edit = DetachedWorkspaceEdit(detachedBundle())
        val service = TextDisplaySettingsServiceImpl(edit)
        assertThrows(IllegalStateException::class.java) {
            service.loadText(SettingsScope.Window(windowId = IdType().toString(), workspaceId = edit.bundle.workspaceId.toString()))
        }
    }

    @Test
    fun theSingletonIsUnaffectedByDetachedInstances() {
        val plain = TextDisplaySettingsServiceImpl()
        val edit = DetachedWorkspaceEdit(detachedBundle())
        TextDisplaySettingsServiceImpl(edit).setValue(
            SettingsScope.Workspace(edit.bundle.workspaceId.toString()),
            TextSettingType.STRONGS, TextSettingValue.IntValue(2),
        )
        val snapshot = plain.loadText(SettingsScope.Workspace(CommonUtils.windowControl.windowRepository.id.toString()))
        assertEquals(
            "a detached instance changed what the shared (Koin singleton) instance reports",
            CommonUtils.windowControl.windowRepository.name, snapshot.workspaceName,
        )
    }
}
