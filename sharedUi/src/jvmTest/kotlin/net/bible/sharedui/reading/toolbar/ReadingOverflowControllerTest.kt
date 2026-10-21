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
package net.bible.sharedui.reading.toolbar

import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedui.strings.keyed.KeyedReadingMenuStrings
import net.bible.sharedui.strings.keyed.StringTable
import net.bible.sharedui.strings.lookup
import net.bible.sharedui.strings.lookupArray
import net.bible.sharedui.strings.lookupPlural
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The overflow menu's rows and click handling, over a recording [ReadingOverflowHost]. */
class ReadingOverflowControllerTest {
    private class FakeItem(
        override var value: Any,
        override val enabled: Boolean = true,
        override val visible: Boolean = true,
        override val title: String? = "Item",
        override val iconKey: String? = "icon",
    ) : PrefItemHandle {
        var handled = 0
        override fun setNonSpecific() = Unit
        override fun handle() { handled++ }
    }

    private class FakeHost : ReadingOverflowHost {
        val calls = mutableListOf<String>()
        var night = false
        var switchable = true
        var tiltOk = true
        var tilt = false
        var multi = false
        var reverse = false
        var autoPinStored = true // autoPin stored value; the menu row shows its inverse
        var recent: List<Pair<Types, PrefItemHandle>> = emptyList()
        var onReady: (() -> Unit)? = null

        override fun toggleFullScreen() { calls += "fullscreen" }
        override fun nightMode() = night
        override fun nightModeSwitchable() = switchable
        override fun setNightMode(on: Boolean) { night = on; calls += "night:$on" }
        override fun tiltPossible() = tiltOk
        override fun tiltToScroll() = tilt
        override fun setTiltToScroll(on: Boolean) { tilt = on; calls += "tilt:$on" }
        override fun isMultiWindow() = multi
        override fun reverseSplit() = reverse
        override fun setReverseSplit(on: Boolean) { reverse = on; calls += "split:$on" }
        override fun autoPin() = autoPinStored
        override fun setAutoPin(on: Boolean) { autoPinStored = on; calls += "pin:$on" }
        override fun recentTextOptions() = recent
        override fun showWorkspaceSheet() { calls += "workspaceSheet" }
        override fun openAllTextOptions() { calls += "allTextOptions" }
        override fun workspaceScope() = SettingsScope.Workspace("ws1")
        override fun showTextSettingEditor(scope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit) {
            calls += "editor:$scope:$page"; this.onReady = onReady
        }
        override fun applyWorkspaceTextSettings(types: Set<Types>) { calls += "apply:$types" }
    }

    private val host = FakeHost()
    private val table = StringTable.of("en", { lookup("en", it) }, { n, c -> lookupPlural("en", n, c) }, { lookupArray("en", it) })
    private val menu = ReadingOverflowController(host) { KeyedReadingMenuStrings(table) }

    private fun ids() = menu.build().map { it.id }

    @Test fun fullScreenRowIsFirstAndClosesTheMenu() {
        val row = menu.build().first()
        assertEquals("fullscreen", row.id)
        assertEquals("ic_full_screen_24", row.iconKey)
        assertFalse(row.checkable)
        assertFalse(menu.handle("fullscreen"), "the row closes the menu")
        assertEquals(listOf("fullscreen"), host.calls)
    }

    @Test fun rowsComeInAndroidsOrderAndHideWhatTheDeviceCannotDo() {
        assertEquals(listOf("fullscreen", "nightMode", "switchToWorkspace", "tiltToScroll", "autoPinMode", "allTextOptions"), ids())
        host.tiltOk = false; host.multi = true
        assertEquals(listOf("fullscreen", "nightMode", "switchToWorkspace", "splitMode", "autoPinMode", "allTextOptions"), ids())
    }

    @Test fun nightModeAndPinRowsShowTheirStateAndPinIsTheInverseOfAutoPin() {
        host.night = true; host.autoPinStored = false
        val items = menu.build().associateBy { it.id }
        assertTrue(items.getValue("nightMode").checked)
        assertTrue(items.getValue("autoPinMode").checked, "the row shows pinning, the inverse of the stored autoPin flag")
    }

    @Test fun nightModeRowIsDisabledWhenItCannotBeSwitchedAndClickingItChangesNothing() {
        host.switchable = false
        assertFalse(menu.build().first { it.id == "nightMode" }.enabled)
        assertTrue(menu.handle("nightMode"))
        assertTrue(host.calls.isEmpty())
    }

    @Test fun togglesFlipTheirValueAndKeepTheMenuOpen() {
        host.multi = true
        assertTrue(menu.handle("nightMode")); assertTrue(menu.handle("tiltToScroll"))
        assertTrue(menu.handle("splitMode")); assertTrue(menu.handle("autoPinMode"))
        assertEquals(listOf("night:true", "tilt:true", "split:true", "pin:false"), host.calls)
    }

    @Test fun switchToWorkspaceAndAllTextOptionsCloseTheMenu() {
        assertFalse(menu.handle("switchToWorkspace"))
        assertFalse(menu.handle("allTextOptions"))
        assertEquals(listOf("workspaceSheet", "allTextOptions"), host.calls)
    }

    @Test fun recentTextOptionsSitBetweenTheStaticRowsAndAllTextOptionsAndStartTheLastSection() {
        host.recent = listOf(Types.FOOTNOTES to FakeItem(true), Types.JUSTIFY to FakeItem(false, enabled = false))
        val items = menu.build()
        assertEquals(listOf("fullscreen", "nightMode", "switchToWorkspace", "tiltToScroll", "autoPinMode", "textOptionItem:0", "allTextOptions"), items.map { it.id })
        assertTrue(items.first { it.id == "textOptionItem:0" }.startsNewSection)
        assertFalse(items.first { it.id == "allTextOptions" }.startsNewSection)
        // With no recent row, allTextOptions carries the divider itself.
        host.recent = emptyList()
        assertTrue(menu.build().last().startsNewSection)
    }

    @Test fun aBooleanTextOptionFlipsHandlesAppliesAndKeepsTheMenuOpen() {
        val item = FakeItem(false)
        host.recent = listOf(Types.FOOTNOTES to item)
        assertTrue(menu.handle("textOptionItem:0"))
        assertEquals(true, item.value)
        assertEquals(1, item.handled)
        assertEquals(listOf("apply:[FOOTNOTES]"), host.calls)
    }

    @Test fun aValueTextOptionOpensItsEditorSheetOrTheFullScreenWhenItHasNone() {
        host.recent = listOf(Types.FONTSIZE to FakeItem(12), Types.HYPHENATION to FakeItem(2))
        assertFalse(menu.handle("textOptionItem:0"))
        assertEquals("editor:${SettingsScope.Workspace("ws1")}:${SettingsEditorPage.Row("FONTSIZE")}", host.calls.single())
        host.onReady!!.invoke()
        assertEquals("apply:[FONTSIZE]", host.calls.last())
        host.calls.clear()
        assertFalse(menu.handle("textOptionItem:1"))
        assertEquals(listOf("allTextOptions"), host.calls)
    }

    @Test fun aStaleOrUnknownIdDoesNothing() {
        assertFalse(menu.handle("textOptionItem:9"))
        assertFalse(menu.handle("textOptionItem:x"))
        assertFalse(menu.handle("bogus"))
        assertTrue(host.calls.isEmpty())
    }
}
