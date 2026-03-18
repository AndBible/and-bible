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

package net.bible.android.database

import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDisplaySettingsTest {

    // --- actual() tests: 3-level fallback chain ---

    @Test
    fun `actual uses page manager value when set`() {
        val page = TextDisplaySettings(fontSize = 20)
        val workspace = TextDisplaySettings(fontSize = 18)
        val global = TextDisplaySettings(fontSize = 14)

        val result = TextDisplaySettings.actual(page, workspace, global)
        assertEquals(20, result.fontSize)
    }

    @Test
    fun `actual falls back to workspace when page manager is null for setting`() {
        val page = TextDisplaySettings() // fontSize = null
        val workspace = TextDisplaySettings(fontSize = 18)
        val global = TextDisplaySettings(fontSize = 14)

        val result = TextDisplaySettings.actual(page, workspace, global)
        assertEquals(18, result.fontSize)
    }

    @Test
    fun `actual falls back to global when page and workspace are null for setting`() {
        val page = TextDisplaySettings()
        val workspace = TextDisplaySettings()
        val global = TextDisplaySettings(fontSize = 14)

        val result = TextDisplaySettings.actual(page, workspace, global)
        assertEquals(14, result.fontSize)
    }

    @Test
    fun `actual falls back to default when all three levels are null for setting`() {
        val page = TextDisplaySettings()
        val workspace = TextDisplaySettings()
        val global = TextDisplaySettings()

        val result = TextDisplaySettings.actual(page, workspace, global)
        assertEquals(TextDisplaySettings.default.fontSize, result.fontSize)
    }

    @Test
    fun `actual with null page manager falls back to workspace`() {
        val workspace = TextDisplaySettings(fontSize = 18)
        val global = TextDisplaySettings(fontSize = 14)

        val result = TextDisplaySettings.actual(null, workspace, global)
        assertEquals(18, result.fontSize)
    }

    @Test
    fun `actual with null page manager falls back to global when workspace null`() {
        val workspace = TextDisplaySettings()
        val global = TextDisplaySettings(fontSize = 14)

        val result = TextDisplaySettings.actual(null, workspace, global)
        assertEquals(14, result.fontSize)
    }

    @Test
    fun `actual resolves each type independently across levels`() {
        val page = TextDisplaySettings(fontSize = 20)
        val workspace = TextDisplaySettings(showRedLetters = false)
        val global = TextDisplaySettings(lineSpacing = 24)

        val result = TextDisplaySettings.actual(page, workspace, global)
        assertEquals(20, result.fontSize)
        assertEquals(false, result.showRedLetters)
        assertEquals(24, result.lineSpacing)
    }

    @Test
    fun `actual fills all types without nulls`() {
        val result = TextDisplaySettings.actual(
            TextDisplaySettings(), TextDisplaySettings(), TextDisplaySettings()
        )
        for (type in Types.values()) {
            assertNotNull("Type $type should not be null in actual result", result.getValue(type))
        }
    }

    // --- markNonSpecific() tests ---

    @Test
    fun `markNonSpecific nulls page value that matches workspace value`() {
        val page = TextDisplaySettings(fontSize = 18)
        val workspace = TextDisplaySettings(fontSize = 18)
        val global = TextDisplaySettings()

        TextDisplaySettings.markNonSpecific(page, workspace, global)
        assertNull(page.fontSize)
    }

    @Test
    fun `markNonSpecific keeps page value that differs from workspace`() {
        val page = TextDisplaySettings(fontSize = 20)
        val workspace = TextDisplaySettings(fontSize = 18)
        val global = TextDisplaySettings()

        TextDisplaySettings.markNonSpecific(page, workspace, global)
        assertEquals(20, page.fontSize)
    }

    @Test
    fun `markNonSpecific nulls page value that matches global when workspace is null`() {
        val page = TextDisplaySettings(fontSize = 14)
        val workspace = TextDisplaySettings() // fontSize null
        val global = TextDisplaySettings(fontSize = 14)

        TextDisplaySettings.markNonSpecific(page, workspace, global)
        assertNull(page.fontSize)
    }

    @Test
    fun `markNonSpecific nulls page value that matches default when workspace and global are null`() {
        val page = TextDisplaySettings(fontSize = TextDisplaySettings.default.fontSize)
        val workspace = TextDisplaySettings()
        val global = TextDisplaySettings()

        TextDisplaySettings.markNonSpecific(page, workspace, global)
        assertNull(page.fontSize)
    }

    @Test
    fun `markNonSpecific does nothing when page is null`() {
        val workspace = TextDisplaySettings(fontSize = 18)
        val global = TextDisplaySettings()
        // Should not throw
        TextDisplaySettings.markNonSpecific(null, workspace, global)
    }

    @Test
    fun `markNonSpecific handles boolean types correctly`() {
        val page = TextDisplaySettings(showRedLetters = false)
        val workspace = TextDisplaySettings(showRedLetters = false)
        val global = TextDisplaySettings()

        TextDisplaySettings.markNonSpecific(page, workspace, global)
        assertNull(page.showRedLetters)
    }

    @Test
    fun `markNonSpecific keeps boolean that differs from parent`() {
        val page = TextDisplaySettings(showRedLetters = false)
        val workspace = TextDisplaySettings(showRedLetters = true)
        val global = TextDisplaySettings()

        TextDisplaySettings.markNonSpecific(page, workspace, global)
        assertEquals(false, page.showRedLetters)
    }

    // --- SettingsBundle.actualSettings tests ---

    @Test
    fun `SettingsBundle actualSettings uses 3-level chain`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = TextDisplaySettings(fontSize = 22),
            workspaceSettings = TextDisplaySettings(showRedLetters = false),
            globalSettings = TextDisplaySettings(lineSpacing = 24),
        )

        val actual = bundle.actualSettings
        assertEquals(22, actual.fontSize)
        assertEquals(false, actual.showRedLetters)
        assertEquals(24, actual.lineSpacing)
    }

    @Test
    fun `SettingsBundle actualSettings with no page manager settings`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceSettings = TextDisplaySettings(fontSize = 18),
            globalSettings = TextDisplaySettings(lineSpacing = 24),
        )

        val actual = bundle.actualSettings
        assertEquals(18, actual.fontSize)
        assertEquals(24, actual.lineSpacing)
    }

    @Test
    fun `SettingsBundle actualSettings at global level uses global then defaults`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.GLOBAL,
            globalSettings = TextDisplaySettings(fontSize = 14),
        )

        val actual = bundle.actualSettings
        assertEquals(14, actual.fontSize)
        // Other values should come from defaults
        assertEquals(TextDisplaySettings.default.showRedLetters, actual.showRedLetters)
    }

    // --- Global change propagation tests ---
    // These test the algorithm used in WindowRepository.propagateGlobalTextDisplaySettingsChange()

    @Test
    fun `global propagation nulls workspace value that matches new global value`() {
        val workspace = TextDisplaySettings(fontSize = 15)
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        assertNull("Workspace fontSize should be nulled when it matches global", workspace.fontSize)
    }

    @Test
    fun `global propagation keeps workspace value that differs from new global value`() {
        val workspace = TextDisplaySettings(fontSize = 20)
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        assertEquals("Workspace fontSize should be kept when different from global", 20, workspace.fontSize)
    }

    @Test
    fun `global propagation does not touch workspace null values`() {
        val workspace = TextDisplaySettings() // fontSize null = inherits
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        assertNull("Workspace fontSize should remain null", workspace.fontSize)
    }

    @Test
    fun `global propagation nulls window value that matches effective parent`() {
        val workspace = TextDisplaySettings(fontSize = 18) // workspace overrides
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val window = TextDisplaySettings(fontSize = 18) // matches workspace
        val dirtyTypes = setOf(Types.FONTSIZE)

        // Simulate window propagation: parent = workspace ?? global ?? default
        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Window fontSize should be nulled when it matches workspace parent", window.fontSize)
    }

    @Test
    fun `global propagation nulls window value that matches global when workspace is null`() {
        val workspace = TextDisplaySettings() // fontSize null
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val window = TextDisplaySettings(fontSize = 15) // matches global (effective parent)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Window fontSize should be nulled when it matches global parent", window.fontSize)
    }

    @Test
    fun `global propagation keeps window value that differs from effective parent`() {
        val workspace = TextDisplaySettings() // fontSize null
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val window = TextDisplaySettings(fontSize = 22) // differs from global
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertEquals("Window fontSize should be kept when different from parent", 22, window.fontSize)
    }

    @Test
    fun `global propagation handles multiple dirty types independently`() {
        val workspace = TextDisplaySettings(fontSize = 15, showRedLetters = false)
        val newGlobal = TextDisplaySettings(fontSize = 15, showRedLetters = true)
        val dirtyTypes = setOf(Types.FONTSIZE, Types.REDLETTERS)

        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        assertNull("fontSize should be nulled (matches global)", workspace.fontSize)
        assertEquals("showRedLetters should be kept (differs from global)", false, workspace.showRedLetters)
    }

    @Test
    fun `global propagation nulls window value that matches default when workspace and global are null`() {
        val workspace = TextDisplaySettings()
        val newGlobal = TextDisplaySettings() // fontSize not set
        val window = TextDisplaySettings(fontSize = TextDisplaySettings.default.fontSize)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Window fontSize should be nulled when it matches default", window.fontSize)
    }

    // --- Workspace → Window propagation tests ---
    // These test the algorithm used in WindowRepository.updateWindowTextDisplaySettingsValues()

    @Test
    fun `workspace propagation nulls window value that matches new workspace value`() {
        val workspace = TextDisplaySettings(fontSize = 18)
        val window = TextDisplaySettings(fontSize = 18)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            if (window.getValue(t) == workspace.getValue(t)) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Window fontSize should be nulled when it matches workspace", window.fontSize)
    }

    @Test
    fun `workspace propagation keeps window value that differs from workspace`() {
        val workspace = TextDisplaySettings(fontSize = 18)
        val window = TextDisplaySettings(fontSize = 22)
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            if (window.getValue(t) == workspace.getValue(t)) {
                window.setNonSpecific(t)
            }
        }

        assertEquals("Window fontSize should be kept when different from workspace", 22, window.fontSize)
    }

    @Test
    fun `workspace propagation does not touch null window values`() {
        val workspace = TextDisplaySettings(fontSize = 18)
        val window = TextDisplaySettings() // fontSize null = inherits
        val dirtyTypes = setOf(Types.FONTSIZE)

        for (t in dirtyTypes) {
            if (window.getValue(t) == workspace.getValue(t)) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Window fontSize should remain null", window.fontSize)
    }

    @Test
    fun `workspace propagation handles multiple dirty types independently`() {
        val workspace = TextDisplaySettings(fontSize = 18, showRedLetters = true)
        val window = TextDisplaySettings(fontSize = 18, showRedLetters = false)
        val dirtyTypes = setOf(Types.FONTSIZE, Types.REDLETTERS)

        for (t in dirtyTypes) {
            if (window.getValue(t) == workspace.getValue(t)) {
                window.setNonSpecific(t)
            }
        }

        assertNull("fontSize should be nulled (matches workspace)", window.fontSize)
        assertEquals("showRedLetters should be kept (differs from workspace)", false, window.showRedLetters)
    }

    @Test
    fun `workspace propagation only processes dirty types`() {
        val workspace = TextDisplaySettings(fontSize = 18, showRedLetters = true)
        val window = TextDisplaySettings(fontSize = 18, showRedLetters = true) // both match
        val dirtyTypes = setOf(Types.FONTSIZE) // only fontSize is dirty

        for (t in dirtyTypes) {
            if (window.getValue(t) == workspace.getValue(t)) {
                window.setNonSpecific(t)
            }
        }

        assertNull("fontSize should be nulled (dirty and matches)", window.fontSize)
        assertEquals("showRedLetters should be kept (not in dirtyTypes)", true, window.showRedLetters)
    }

    // --- Full cascade tests ---

    // --- Integration tests using propagateGlobalChange() ---

    @Test
    fun `propagateGlobalChange nulls matching workspace and window values across multiple workspaces`() {
        // 2 workspaces, each with 2 windows
        val ws1 = TextDisplaySettings(fontSize = 15, showRedLetters = false)
        val ws1win1 = TextDisplaySettings(fontSize = 15)  // matches ws → should be nulled
        val ws1win2 = TextDisplaySettings(fontSize = 22)  // differs → kept

        val ws2 = TextDisplaySettings(fontSize = 18)      // differs from global → kept
        val ws2win1 = TextDisplaySettings(fontSize = 18)  // matches ws → should be nulled
        val ws2win2 = TextDisplaySettings()               // already null → stays null

        val newGlobal = TextDisplaySettings(fontSize = 15, showRedLetters = true)
        val dirtyTypes = setOf(Types.FONTSIZE, Types.REDLETTERS)

        val workspaces = listOf(
            ws1 to listOf(ws1win1, ws1win2),
            ws2 to listOf(ws2win1, ws2win2),
        )

        val changed = TextDisplaySettings.propagateGlobalChange(dirtyTypes, newGlobal, workspaces)

        assertTrue("Something should have changed", changed)

        // ws1: fontSize=15 matches global=15 → nulled; redLetters=false != true → kept
        assertNull("ws1 fontSize nulled", ws1.fontSize)
        assertEquals("ws1 redLetters kept", false, ws1.showRedLetters)

        // ws1win1: fontSize=15, parent is now global=15 → nulled
        assertNull("ws1win1 fontSize nulled (matches global after ws nulled)", ws1win1.fontSize)
        // ws1win2: fontSize=22, parent=global=15 → kept
        assertEquals("ws1win2 fontSize kept", 22, ws1win2.fontSize)

        // ws2: fontSize=18 != 15 → kept
        assertEquals("ws2 fontSize kept", 18, ws2.fontSize)
        // ws2win1: fontSize=18, parent=ws2=18 → nulled
        assertNull("ws2win1 fontSize nulled (matches workspace)", ws2win1.fontSize)
        // ws2win2: already null → still null
        assertNull("ws2win2 fontSize still null", ws2win2.fontSize)
    }

    @Test
    fun `propagateGlobalChange returns false when nothing changes`() {
        val ws = TextDisplaySettings(fontSize = 20)
        val win = TextDisplaySettings(fontSize = 22)
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val dirtyTypes = setOf(Types.FONTSIZE)

        val changed = TextDisplaySettings.propagateGlobalChange(
            dirtyTypes, newGlobal, listOf(ws to listOf(win))
        )

        assertFalse("Nothing should change when no values match", changed)
        assertEquals(20, ws.fontSize)
        assertEquals(22, win.fontSize)
    }

    @Test
    fun `propagateGlobalChange handles empty workspace list`() {
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val changed = TextDisplaySettings.propagateGlobalChange(
            setOf(Types.FONTSIZE), newGlobal, emptyList()
        )
        assertFalse(changed)
    }

    @Test
    fun `propagateGlobalChange handles workspace with no windows`() {
        val ws = TextDisplaySettings(fontSize = 15)
        val newGlobal = TextDisplaySettings(fontSize = 15)

        val changed = TextDisplaySettings.propagateGlobalChange(
            setOf(Types.FONTSIZE), newGlobal, listOf(ws to emptyList())
        )

        assertTrue(changed)
        assertNull("ws fontSize nulled", ws.fontSize)
    }

    @Test
    fun `propagateGlobalChange full scenario with three levels and all setting types`() {
        // Workspace has some overrides, some null; window has some overrides
        val ws = TextDisplaySettings(
            fontSize = 15,
            lineSpacing = 20,
            showRedLetters = true,  // differs from global
        )
        val win = TextDisplaySettings(
            fontSize = 15,     // matches ws (which will be nulled) → matches global → nulled
            lineSpacing = 20,  // matches ws → nulled
            topMargin = 5,     // not in dirtyTypes → untouched
        )
        val newGlobal = TextDisplaySettings(fontSize = 15, lineSpacing = 20, showRedLetters = false)
        val dirtyTypes = setOf(Types.FONTSIZE, Types.LINE_SPACING, Types.REDLETTERS)

        TextDisplaySettings.propagateGlobalChange(
            dirtyTypes, newGlobal, listOf(ws to listOf(win))
        )

        // Workspace
        assertNull("ws fontSize nulled (15==15)", ws.fontSize)
        assertNull("ws lineSpacing nulled (20==20)", ws.lineSpacing)
        assertEquals("ws redLetters kept (true!=false)", true, ws.showRedLetters)

        // Window: after ws nulling, effective parents are global values
        assertNull("win fontSize nulled (15==global 15)", win.fontSize)
        assertNull("win lineSpacing nulled (20==global 20)", win.lineSpacing)
        assertEquals("win topMargin untouched (not dirty)", 5, win.topMargin)
    }

    @Test
    fun `propagateGlobalChange workspace already null stays null, window matches global`() {
        // Workspace fontSize is null (inherits). Window has fontSize=15 matching global.
        val ws = TextDisplaySettings() // fontSize null
        val win = TextDisplaySettings(fontSize = 15)
        val newGlobal = TextDisplaySettings(fontSize = 15)

        TextDisplaySettings.propagateGlobalChange(
            setOf(Types.FONTSIZE), newGlobal, listOf(ws to listOf(win))
        )

        assertNull("ws fontSize should stay null", ws.fontSize)
        assertNull("win fontSize nulled (matches global, ws is null)", win.fontSize)
    }

    @Test
    fun `propagateGlobalChange window matches default when workspace and global are both null`() {
        val ws = TextDisplaySettings()
        val win = TextDisplaySettings(fontSize = TextDisplaySettings.default.fontSize)
        val newGlobal = TextDisplaySettings() // fontSize not set

        TextDisplaySettings.propagateGlobalChange(
            setOf(Types.FONTSIZE), newGlobal, listOf(ws to listOf(win))
        )

        assertNull("ws fontSize stays null", ws.fontSize)
        assertNull("win fontSize nulled (matches default fallback)", win.fontSize)
    }

    // --- Full cascade tests (inline logic, kept for completeness) ---

    @Test
    fun `global propagation cascades correctly through workspace nulling to window`() {
        // Scenario: global fontSize changes to 15, workspace had 15 (gets nulled),
        // window had 15 (should still be nulled because effective parent is now global=15)
        val workspace = TextDisplaySettings(fontSize = 15)
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val window = TextDisplaySettings(fontSize = 15)
        val dirtyTypes = setOf(Types.FONTSIZE)

        // Step 1: propagate to workspace
        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        // Step 2: propagate to window (using already-updated workspace)
        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Workspace fontSize should be nulled", workspace.fontSize)
        assertNull("Window fontSize should also be nulled", window.fontSize)
    }

    @Test
    fun `global cascade keeps window value when it differs from new effective parent`() {
        // Global changes to 15, workspace had 15 (gets nulled),
        // but window has 22 (differs from new effective parent=global=15)
        val workspace = TextDisplaySettings(fontSize = 15)
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val window = TextDisplaySettings(fontSize = 22)
        val dirtyTypes = setOf(Types.FONTSIZE)

        // Step 1: workspace
        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        // Step 2: window
        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertNull("Workspace fontSize should be nulled", workspace.fontSize)
        assertEquals("Window fontSize should be kept (22 != 15)", 22, window.fontSize)
    }

    @Test
    fun `global cascade with workspace override intact`() {
        // Global changes to 15, workspace has 18 (kept), window has 18 (matches workspace → nulled)
        val workspace = TextDisplaySettings(fontSize = 18)
        val newGlobal = TextDisplaySettings(fontSize = 15)
        val window = TextDisplaySettings(fontSize = 18)
        val dirtyTypes = setOf(Types.FONTSIZE)

        // Step 1: workspace (18 != 15, kept)
        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        // Step 2: window (18 == workspace's 18 → nulled)
        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertEquals("Workspace fontSize should be kept (18 != 15)", 18, workspace.fontSize)
        assertNull("Window fontSize should be nulled (matches workspace)", window.fontSize)
    }

    // --- Copy to target tests (algorithm used in copy-to-global) ---

    @Test
    fun `copy selected settings copies only checked types`() {
        val source = TextDisplaySettings(fontSize = 20, showRedLetters = false, lineSpacing = 24)
        val target = TextDisplaySettings(fontSize = 14, showRedLetters = true, lineSpacing = 16)
        val types = Types.values()
        val checkedTypes = BooleanArray(types.size) { false }
        checkedTypes[types.indexOf(Types.FONTSIZE)] = true

        for ((tIdx, type) in types.withIndex()) {
            if (checkedTypes[tIdx]) {
                target.setValue(type, source.getValue(type))
            }
        }

        assertEquals(20, target.fontSize)
        assertEquals(true, target.showRedLetters)
        assertEquals(16, target.lineSpacing)
    }

    @Test
    fun `copy all settings copies every type`() {
        val source = TextDisplaySettings(fontSize = 20, showRedLetters = false, lineSpacing = 24)
        val target = TextDisplaySettings(fontSize = 14, showRedLetters = true, lineSpacing = 16)
        val types = Types.values()
        val checkedTypes = BooleanArray(types.size) { true }

        for ((tIdx, type) in types.withIndex()) {
            if (checkedTypes[tIdx]) {
                target.setValue(type, source.getValue(type))
            }
        }

        assertEquals(20, target.fontSize)
        assertEquals(false, target.showRedLetters)
        assertEquals(24, target.lineSpacing)
    }

    @Test
    fun `copy null source value overwrites target with null`() {
        val source = TextDisplaySettings()
        val target = TextDisplaySettings(fontSize = 14)
        val types = Types.values()
        val checkedTypes = BooleanArray(types.size) { true }

        for ((tIdx, type) in types.withIndex()) {
            if (checkedTypes[tIdx]) {
                target.setValue(type, source.getValue(type))
            }
        }

        assertNull(target.fontSize)
    }

    @Test
    fun `copy no settings changes nothing`() {
        val source = TextDisplaySettings(fontSize = 20)
        val target = TextDisplaySettings(fontSize = 14)
        val types = Types.values()
        val checkedTypes = BooleanArray(types.size) { false }

        for ((tIdx, type) in types.withIndex()) {
            if (checkedTypes[tIdx]) {
                target.setValue(type, source.getValue(type))
            }
        }

        assertEquals(14, target.fontSize)
    }

    @Test
    fun `after copy to global, actual reflects new global values for inheriting levels`() {
        val source = TextDisplaySettings(fontSize = 22, showRedLetters = false)
        val global = TextDisplaySettings(fontSize = 14, showRedLetters = true)

        val types = Types.values()
        for (type in types) {
            global.setValue(type, source.getValue(type))
        }

        val workspace = TextDisplaySettings()
        val window = TextDisplaySettings()

        val actual = TextDisplaySettings.actual(window, workspace, global)
        assertEquals(22, actual.fontSize)
        assertEquals(false, actual.showRedLetters)
    }

    @Test
    fun `after copy to global, workspace override still takes precedence`() {
        val source = TextDisplaySettings(fontSize = 22)
        val global = TextDisplaySettings(fontSize = 14)

        global.setValue(Types.FONTSIZE, source.getValue(Types.FONTSIZE))

        val workspace = TextDisplaySettings(fontSize = 30)
        val window = TextDisplaySettings()

        val actual = TextDisplaySettings.actual(window, workspace, global)
        assertEquals(30, actual.fontSize)
    }

    @Test
    fun `after copy to global, window override still takes precedence`() {
        val source = TextDisplaySettings(fontSize = 22)
        val global = TextDisplaySettings(fontSize = 14)

        global.setValue(Types.FONTSIZE, source.getValue(Types.FONTSIZE))

        val workspace = TextDisplaySettings()
        val window = TextDisplaySettings(fontSize = 40)

        val actual = TextDisplaySettings.actual(window, workspace, global)
        assertEquals(40, actual.fontSize)
    }

    @Test
    fun `partial copy to global only affects checked types in hierarchy`() {
        val global = TextDisplaySettings(fontSize = 14, showRedLetters = true, lineSpacing = 16)
        val source = TextDisplaySettings(fontSize = 22, showRedLetters = false, lineSpacing = 28)

        global.setValue(Types.FONTSIZE, source.getValue(Types.FONTSIZE))

        val workspace = TextDisplaySettings()
        val window = TextDisplaySettings()
        val actual = TextDisplaySettings.actual(window, workspace, global)

        assertEquals(22, actual.fontSize)
        assertEquals(true, actual.showRedLetters)
        assertEquals(16, actual.lineSpacing)
    }

    // --- Full cascade tests (inline logic, kept for completeness) ---

    @Test
    fun `global cascade with multiple types and mixed outcomes`() {
        // fontSize: global=15, ws=15 → ws nulled; window=15 → nulled (matches global)
        // redLetters: global=true, ws=false → ws kept; window=false → kept (matches ws)
        // lineSpacing: global=20, ws=null, window=20 → window nulled (matches global)
        val workspace = TextDisplaySettings(fontSize = 15, showRedLetters = false)
        val newGlobal = TextDisplaySettings(fontSize = 15, showRedLetters = true, lineSpacing = 20)
        val window = TextDisplaySettings(fontSize = 15, showRedLetters = false, lineSpacing = 20)
        val dirtyTypes = setOf(Types.FONTSIZE, Types.REDLETTERS, Types.LINE_SPACING)

        // Step 1: workspace
        for (t in dirtyTypes) {
            if (workspace.getValue(t) == newGlobal.getValue(t)) {
                workspace.setNonSpecific(t)
            }
        }

        // Step 2: window
        for (t in dirtyTypes) {
            val parentValue = workspace.getValue(t)
                ?: newGlobal.getValue(t)
                ?: TextDisplaySettings.default.getValue(t)
            if (window.getValue(t) == parentValue) {
                window.setNonSpecific(t)
            }
        }

        assertNull("ws fontSize nulled (matched global)", workspace.fontSize)
        assertEquals("ws redLetters kept (false != true)", false, workspace.showRedLetters)
        assertNull("ws lineSpacing was already null", workspace.lineSpacing)

        assertNull("window fontSize nulled (15 == global 15)", window.fontSize)
        assertNull("window redLetters nulled (false == ws false = parent)", window.showRedLetters)
        assertNull("window lineSpacing nulled (20 == global 20)", window.lineSpacing)
    }
}
