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
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextSettingRowValue
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class TextDisplaySettingsServiceImplTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private val service = TextDisplaySettingsServiceImpl()

    @Before fun setUp() {
        windowControl = CommonUtils.windowControl
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = repo
        windowControl.activeWindow    // auto-creates window 1 + workspace
    }
    @After fun tearDown() { DatabaseResetter.resetDatabase() }

    private fun wsScope() = SettingsScope.Workspace(repo.id.toString())
    private fun globalScope() = SettingsScope.Global
    private fun windowScope() = SettingsScope.Window(repo.activeWindow.id.toString(), repo.id.toString())

    @Test fun loadText_hasAll35RowsWithEffectiveValues() {
        val snap = service.loadText(wsScope())
        assertEquals(35, snap.rows.size)
        // JUSTIFY default true (TextDisplaySettings.default.justifyText)
        assertEquals(TextSettingRowValue.Bool(true), snap.rows[TextSettingType.JUSTIFY]!!.value)
        // FONTSIZE default 16, range 1..60
        val fs = snap.rows[TextSettingType.FONTSIZE]!!.value as TextSettingRowValue.Numeric
        assertEquals(16, fs.value); assertEquals(1, fs.min); assertEquals(60, fs.max)
        // MARGINSIZE maxes verbatim from MarginSizeWidget
        val ms = snap.rows[TextSettingType.MARGINSIZE]!!.value as TextSettingRowValue.Margins
        assertEquals(30, ms.leftMax); assertEquals(30, ms.rightMax); assertEquals(500, ms.maxWidthMax)
        // FONTFAMILY entries come from availableFonts
        val ff = snap.rows[TextSettingType.FONTFAMILY]!!.value as TextSettingRowValue.Choice
        assertTrue(ff.entries.any { it.value == "sans-serif" })
    }

    @Test fun setBoolAtWorkspace_writesEntityAndMarksInherited() {
        service.setValue(wsScope(), TextSettingType.JUSTIFY, TextSettingValue.BoolValue(false))
        assertEquals(false, repo.textDisplaySettings.justifyText)
        // reload reflects the change and inheritedFrom NONE (own value at workspace)
        val snap = service.loadText(wsScope())
        assertEquals(TextSettingRowValue.Bool(false), snap.rows[TextSettingType.JUSTIFY]!!.value)
        assertEquals(InheritedFrom.NONE, snap.rows[TextSettingType.JUSTIFY]!!.inheritedFrom)
    }

    @Test fun setValueEqualToParentStoresNull_sparseOverrideAtWindow() {
        // window value equal to the (default) parent -> stored null (setNonSpecific)
        val w = repo.activeWindow
        service.setValue(windowScope(), TextSettingType.JUSTIFY, TextSettingValue.BoolValue(true)) // default is true
        assertNull(w.pageManager.textDisplaySettings.justifyText)   // sparse: not a redundant override
        assertEquals(InheritedFrom.GLOBAL, service.loadText(windowScope()).rows[TextSettingType.JUSTIFY]!!.inheritedFrom)
    }

    @Test fun setIntStrongsAtWorkspace() {
        service.setValue(wsScope(), TextSettingType.STRONGS, TextSettingValue.IntValue(2))
        assertEquals(2, repo.textDisplaySettings.strongsMode)
    }

    @Test fun setMarginsMergesFieldByField() {
        service.setValue(wsScope(), TextSettingType.MARGINSIZE, TextSettingValue.MarginsValue(5, 6, 200))
        val m = repo.textDisplaySettings.marginSize!!
        assertEquals(5, m.marginLeft); assertEquals(6, m.marginRight); assertEquals(200, m.maxWidth)
    }

    @Test fun revertAtWorkspaceClearsOverride() {
        service.setValue(wsScope(), TextSettingType.REDLETTERS, TextSettingValue.BoolValue(false))
        assertEquals(false, repo.textDisplaySettings.showRedLetters)
        service.revert(wsScope(), TextSettingType.REDLETTERS)
        assertNull(repo.textDisplaySettings.showRedLetters)
    }

    @Test fun resetWorkspaceClearsAll() {
        service.setValue(wsScope(), TextSettingType.JUSTIFY, TextSettingValue.BoolValue(false))
        service.reset(wsScope())
        assertNull(repo.textDisplaySettings.justifyText)   // empty TextDisplaySettings after reset
    }

    @Test fun setGlobalPersistsAndPropagates() {
        service.setValue(globalScope(), TextSettingType.JUSTIFY, TextSettingValue.BoolValue(false))
        assertEquals(false, CommonUtils.globalTextDisplaySettings.justifyText)   // persisted via DAO setter
    }
}
