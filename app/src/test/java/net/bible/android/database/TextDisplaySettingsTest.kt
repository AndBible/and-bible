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

import net.bible.android.database.WorkspaceEntities.Colors
import net.bible.android.database.WorkspaceEntities.MarginSize
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

    @Test
    fun `resetting global to empty restores default for every type including ordinals`() {
        // Models the GLOBAL-level reset (TextDisplaySettingsActivity.reset): the global overrides
        // are replaced with an empty TextDisplaySettings, so every effective value must fall back
        // to its built-in default. Regression guard for ordinals (and any future setting) being
        // left out of the reset.
        val customizedGlobal = TextDisplaySettings(showOrdinals = true, fontSize = 28, strongsMode = 2)
        val before = TextDisplaySettings.actual(null, TextDisplaySettings(), customizedGlobal)
        assertEquals(true, before.showOrdinals)
        assertEquals(28, before.fontSize)

        val resetGlobal = TextDisplaySettings()
        val after = TextDisplaySettings.actual(null, TextDisplaySettings(), resetGlobal)
        for (type in Types.values()) {
            assertEquals(
                "After global reset, $type should equal its default",
                TextDisplaySettings.default.getValue(type), after.getValue(type)
            )
        }
        assertEquals(false, after.showOrdinals)
    }

    // --- actual() sub-object field-level merge tests ---
    //
    // Regression tests for the bug where workspace/window stored a MarginSize with
    // marginLeft/marginRight set but maxWidth=null (e.g. after migration 93e575de2 which
    // nulled the legacy default 170). The whole-object resolution in actual() would return
    // this MarginSize verbatim, and the null maxWidth reached BibleView as JSON null; in
    // JavaScript `null * mmInPx = 0` caused the margin-tap divs to cover half the screen
    // each, hijacking all taps. Fixed by merging sub-objects field-by-field against the
    // fallback chain so any null field inherits from the next level (global → default).

    @Test
    fun `actual MarginSize merges maxWidth null field from default`() {
        // Workspace has marginLeft/Right set but maxWidth null — regression scenario.
        val workspace = TextDisplaySettings(
            marginSize = MarginSize(marginLeft = 1, marginRight = 1, maxWidth = null)
        )
        val result = TextDisplaySettings.actual(null, workspace, TextDisplaySettings())

        val margins = result.marginSize!!
        assertEquals(1, margins.marginLeft)
        assertEquals(1, margins.marginRight)
        assertEquals(
            "maxWidth should fall back to default when null at every level",
            TextDisplaySettings.default.marginSize!!.maxWidth, margins.maxWidth
        )
    }

    @Test
    fun `actual MarginSize merges null fields from global when set`() {
        // Workspace has only marginLeft; global provides maxWidth; rest from default.
        val workspace = TextDisplaySettings(
            marginSize = MarginSize(marginLeft = 5, marginRight = null, maxWidth = null)
        )
        val global = TextDisplaySettings(
            marginSize = MarginSize(marginLeft = null, marginRight = null, maxWidth = 200)
        )
        val result = TextDisplaySettings.actual(null, workspace, global)

        val margins = result.marginSize!!
        assertEquals("workspace.marginLeft takes precedence", 5, margins.marginLeft)
        assertEquals(
            "marginRight falls back to default",
            TextDisplaySettings.default.marginSize!!.marginRight, margins.marginRight
        )
        assertEquals("maxWidth from global overrides default", 200, margins.maxWidth)
    }

    @Test
    fun `actual MarginSize page manager overrides workspace per field`() {
        val page = TextDisplaySettings(
            marginSize = MarginSize(marginLeft = 7, marginRight = null, maxWidth = null)
        )
        val workspace = TextDisplaySettings(
            marginSize = MarginSize(marginLeft = 3, marginRight = 3, maxWidth = 200)
        )
        val result = TextDisplaySettings.actual(page, workspace, TextDisplaySettings())

        val margins = result.marginSize!!
        assertEquals("page.marginLeft wins", 7, margins.marginLeft)
        assertEquals("marginRight from workspace", 3, margins.marginRight)
        assertEquals("maxWidth from workspace", 200, margins.maxWidth)
    }

    @Test
    fun `actual MarginSize all-null workspace inherits everything from default`() {
        val workspace = TextDisplaySettings(
            marginSize = MarginSize(marginLeft = null, marginRight = null, maxWidth = null)
        )
        val result = TextDisplaySettings.actual(null, workspace, TextDisplaySettings())

        assertEquals(TextDisplaySettings.default.marginSize, result.marginSize)
    }

    @Test
    fun `actual MarginSize falls back entirely to default when none set`() {
        val result = TextDisplaySettings.actual(
            null, TextDisplaySettings(), TextDisplaySettings()
        )
        assertEquals(TextDisplaySettings.default.marginSize, result.marginSize)
    }

    @Test
    fun `actual Colors merges null fields from default`() {
        val workspace = TextDisplaySettings(
            colors = Colors(
                dayTextColor = 0xFF0000.toInt().or(0xFF shl 24),
                dayBackground = null,
                dayNoise = null,
                nightTextColor = null,
                nightBackground = null,
                nightNoise = null,
                dayBackgroundImage = null,
                nightBackgroundImage = null,
                dayBackgroundImageOpacity = null,
                nightBackgroundImageOpacity = null,
            )
        )
        val result = TextDisplaySettings.actual(null, workspace, TextDisplaySettings())

        val colors = result.colors!!
        val defaultColors = TextDisplaySettings.default.colors!!
        assertEquals(
            "dayTextColor from workspace",
            0xFF0000.toInt().or(0xFF shl 24), colors.dayTextColor
        )
        assertEquals("dayBackground from default", defaultColors.dayBackground, colors.dayBackground)
        assertEquals("dayNoise from default", defaultColors.dayNoise, colors.dayNoise)
        assertEquals("nightTextColor from default", defaultColors.nightTextColor, colors.nightTextColor)
        assertEquals("nightBackground from default", defaultColors.nightBackground, colors.nightBackground)
        assertEquals("nightNoise from default", defaultColors.nightNoise, colors.nightNoise)
    }

    @Test
    fun `actual Colors all levels non-null pick most specific per field`() {
        val page = TextDisplaySettings(
            colors = Colors(
                dayTextColor = 1, dayBackground = null, dayNoise = null,
                nightTextColor = null, nightBackground = null, nightNoise = null,
                dayBackgroundImage = null, nightBackgroundImage = null,
                dayBackgroundImageOpacity = null, nightBackgroundImageOpacity = null,
            )
        )
        val workspace = TextDisplaySettings(
            colors = Colors(
                dayTextColor = 2, dayBackground = 3, dayNoise = null,
                nightTextColor = null, nightBackground = null, nightNoise = null,
                dayBackgroundImage = null, nightBackgroundImage = null,
                dayBackgroundImageOpacity = null, nightBackgroundImageOpacity = null,
            )
        )
        val global = TextDisplaySettings(
            colors = Colors(
                dayTextColor = 4, dayBackground = 5, dayNoise = 6,
                nightTextColor = 7, nightBackground = null, nightNoise = null,
                dayBackgroundImage = null, nightBackgroundImage = null,
                dayBackgroundImageOpacity = null, nightBackgroundImageOpacity = null,
            )
        )
        val result = TextDisplaySettings.actual(page, workspace, global)

        val colors = result.colors!!
        val defaultColors = TextDisplaySettings.default.colors!!
        assertEquals("dayTextColor from page", 1, colors.dayTextColor)
        assertEquals("dayBackground from workspace", 3, colors.dayBackground)
        assertEquals("dayNoise from global", 6, colors.dayNoise)
        assertEquals("nightTextColor from global", 7, colors.nightTextColor)
        assertEquals(
            "nightBackground from default", defaultColors.nightBackground, colors.nightBackground
        )
        assertEquals("nightNoise from default", defaultColors.nightNoise, colors.nightNoise)
    }

    @Test
    fun `MarginSize merge returns this when override is null`() {
        val base = MarginSize(marginLeft = 3, marginRight = 3, maxWidth = 170)
        assertEquals(base, base.merge(null))
    }

    @Test
    fun `MarginSize merge overrides only non-null fields`() {
        val base = MarginSize(marginLeft = 3, marginRight = 3, maxWidth = 170)
        val override = MarginSize(marginLeft = 9, marginRight = null, maxWidth = null)
        val merged = base.merge(override)
        assertEquals(9, merged.marginLeft)
        assertEquals(3, merged.marginRight)
        assertEquals(170, merged.maxWidth)
    }

    @Test
    fun `Colors merge returns this when override is null`() {
        val base = Colors(
            dayTextColor = 1, dayBackground = 2, dayNoise = 3,
            nightTextColor = 4, nightBackground = 5, nightNoise = 6,
            dayBackgroundImage = null, nightBackgroundImage = null,
            dayBackgroundImageOpacity = null, nightBackgroundImageOpacity = null,
        )
        assertEquals(base, base.merge(null))
    }

    @Test
    fun `Colors merge overrides only non-null fields`() {
        val base = Colors(
            dayTextColor = 1, dayBackground = 2, dayNoise = 3,
            nightTextColor = 4, nightBackground = 5, nightNoise = 6,
            dayBackgroundImage = null, nightBackgroundImage = null,
            dayBackgroundImageOpacity = null, nightBackgroundImageOpacity = null,
        )
        val override = Colors(
            dayTextColor = 100, dayBackground = null, dayNoise = null,
            nightTextColor = null, nightBackground = 500, nightNoise = null,
            dayBackgroundImage = null, nightBackgroundImage = null,
            dayBackgroundImageOpacity = null, nightBackgroundImageOpacity = null,
        )
        val merged = base.merge(override)
        assertEquals(100, merged.dayTextColor)
        assertEquals(2, merged.dayBackground)
        assertEquals(3, merged.dayNoise)
        assertEquals(4, merged.nightTextColor)
        assertEquals(500, merged.nightBackground)
        assertEquals(6, merged.nightNoise)
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

    // --- In-memory workspace→window propagation tests ---
    // Tests the algorithm used in WindowRepository.updateWindowTextDisplaySettingsValues():
    // compares window values directly against workspace values (not the full parent chain).

    /**
     * Helper simulating WindowRepository.updateWindowTextDisplaySettingsValues():
     * for each dirty type, if window value == workspace value → null it.
     */
    private fun simulateWorkspaceToWindowPropagation(
        dirtyTypes: Set<Types>,
        workspaceSettings: TextDisplaySettings,
        windowSettingsList: List<TextDisplaySettings>
    ) {
        for (win in windowSettingsList) {
            for (t in dirtyTypes) {
                if (win.getValue(t) == workspaceSettings.getValue(t)) {
                    win.setNonSpecific(t)
                }
            }
        }
    }

    @Test
    fun `ws-to-window propagation nulls matching window values`() {
        val ws = TextDisplaySettings(fontSize = 18)
        val win1 = TextDisplaySettings(fontSize = 18)
        val win2 = TextDisplaySettings(fontSize = 22)

        simulateWorkspaceToWindowPropagation(setOf(Types.FONTSIZE), ws, listOf(win1, win2))

        assertNull("win1 fontSize nulled (matches ws)", win1.fontSize)
        assertEquals("win2 fontSize kept (differs)", 22, win2.fontSize)
    }

    @Test
    fun `ws-to-window propagation leaves null window values untouched`() {
        val ws = TextDisplaySettings(fontSize = 18)
        val win = TextDisplaySettings() // already null

        simulateWorkspaceToWindowPropagation(setOf(Types.FONTSIZE), ws, listOf(win))

        assertNull("win fontSize stays null", win.fontSize)
    }

    @Test
    fun `ws-to-window propagation only processes dirty types`() {
        val ws = TextDisplaySettings(fontSize = 18, showRedLetters = true)
        val win = TextDisplaySettings(fontSize = 18, showRedLetters = true)

        simulateWorkspaceToWindowPropagation(setOf(Types.FONTSIZE), ws, listOf(win))

        assertNull("fontSize nulled (dirty)", win.fontSize)
        assertEquals("redLetters untouched (not dirty)", true, win.showRedLetters)
    }

    @Test
    fun `ws-to-window propagation with null workspace value nulls matching null windows`() {
        // Both ws and window have null fontSize → they match (null == null) → window gets nulled (no-op)
        val ws = TextDisplaySettings()
        val win = TextDisplaySettings()

        simulateWorkspaceToWindowPropagation(setOf(Types.FONTSIZE), ws, listOf(win))

        assertNull("win fontSize stays null", win.fontSize)
    }

    // --- Full in-memory global propagation simulation ---
    // Simulates the complete in-memory path:
    // 1. Null active workspace TDS values matching global
    // 2. Then propagate to windows using already-nulled workspace as parent

    /**
     * Helper simulating the full in-memory propagation from
     * WindowRepository.propagateGlobalTextDisplaySettingsChange():
     * first nulls workspace, then propagates to windows.
     */
    private fun simulateInMemoryGlobalPropagation(
        dirtyTypes: Set<Types>,
        globalSettings: TextDisplaySettings,
        activeWorkspaceTds: TextDisplaySettings,
        windowSettingsList: List<TextDisplaySettings>
    ) {
        // Step 1: null active workspace values matching global
        for (t in dirtyTypes) {
            if (activeWorkspaceTds.getValue(t) == globalSettings.getValue(t)) {
                activeWorkspaceTds.setNonSpecific(t)
            }
        }
        // Step 2: propagate to windows using (now updated) workspace
        simulateWorkspaceToWindowPropagation(dirtyTypes, activeWorkspaceTds, windowSettingsList)
    }

    @Test
    fun `in-memory global propagation nulls workspace and cascades to windows`() {
        val ws = TextDisplaySettings(fontSize = 15)
        val win = TextDisplaySettings(fontSize = 15)
        val global = TextDisplaySettings(fontSize = 15)

        simulateInMemoryGlobalPropagation(setOf(Types.FONTSIZE), global, ws, listOf(win))

        assertNull("ws fontSize nulled (matches global)", ws.fontSize)
        // After ws is nulled, ws.fontSize is null. win.fontSize=15 != null → NOT nulled by ws-to-window.
        // This is correct: ws-to-window compares against ws value (now null), not global.
        // The window keeps 15, but actual() will still resolve correctly since the DB path handles it.
        assertEquals("win fontSize kept (15 != null ws value)", 15, win.fontSize)
    }

    @Test
    fun `in-memory global propagation keeps workspace override and nulls matching windows`() {
        val ws = TextDisplaySettings(fontSize = 18) // differs from global
        val win = TextDisplaySettings(fontSize = 18) // matches ws
        val global = TextDisplaySettings(fontSize = 15)

        simulateInMemoryGlobalPropagation(setOf(Types.FONTSIZE), global, ws, listOf(win))

        assertEquals("ws fontSize kept (18 != 15)", 18, ws.fontSize)
        assertNull("win fontSize nulled (matches ws 18)", win.fontSize)
    }

    @Test
    fun `in-memory global propagation with mixed types`() {
        val ws = TextDisplaySettings(fontSize = 15, showRedLetters = false, lineSpacing = 20)
        val win = TextDisplaySettings(fontSize = 15, showRedLetters = false, lineSpacing = 24)
        val global = TextDisplaySettings(fontSize = 15, showRedLetters = true, lineSpacing = 20)
        val dirtyTypes = setOf(Types.FONTSIZE, Types.REDLETTERS, Types.LINE_SPACING)

        simulateInMemoryGlobalPropagation(dirtyTypes, global, ws, listOf(win))

        // ws: fontSize=15==15 → null, redLetters=false!=true → kept, lineSpacing=20==20 → null
        assertNull("ws fontSize nulled", ws.fontSize)
        assertEquals("ws redLetters kept", false, ws.showRedLetters)
        assertNull("ws lineSpacing nulled", ws.lineSpacing)

        // win: compared against now-updated ws
        // fontSize: win=15 vs ws=null → not equal → kept
        assertEquals("win fontSize kept (ws is now null)", 15, win.fontSize)
        // redLetters: win=false vs ws=false → equal → nulled
        assertNull("win redLetters nulled (matches ws)", win.showRedLetters)
        // lineSpacing: win=24 vs ws=null → not equal → kept
        assertEquals("win lineSpacing kept (24 != null)", 24, win.lineSpacing)
    }

    // --- SettingsBundle.inheritedFrom tests ---
    // These verify that the icon-overlay logic correctly identifies where the effective value
    // for a given Type comes from. Drives the gear vs. workspace overlay shown in Text options.

    @Test
    fun `inheritedFrom at GLOBAL level is always NONE`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.GLOBAL,
            globalSettings = TextDisplaySettings(showVerseNumbers = true),
        )
        assertEquals(InheritedFrom.NONE, bundle.inheritedFrom(Types.VERSENUMBERS))
        assertEquals(InheritedFrom.NONE, bundle.inheritedFrom(Types.FONTSIZE))
    }

    @Test
    fun `inheritedFrom at WORKSPACE level is NONE when workspace has explicit value`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceSettings = TextDisplaySettings(showVerseNumbers = false),
            globalSettings = TextDisplaySettings(showVerseNumbers = true),
        )
        assertEquals(InheritedFrom.NONE, bundle.inheritedFrom(Types.VERSENUMBERS))
    }

    @Test
    fun `inheritedFrom at WORKSPACE level is GLOBAL when workspace null`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WORKSPACE,
            workspaceSettings = TextDisplaySettings(),
            globalSettings = TextDisplaySettings(showVerseNumbers = true),
        )
        assertEquals(InheritedFrom.GLOBAL, bundle.inheritedFrom(Types.VERSENUMBERS))
    }

    @Test
    fun `inheritedFrom at WINDOW level is NONE when window has explicit value`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = TextDisplaySettings(showVerseNumbers = false),
            workspaceSettings = TextDisplaySettings(showVerseNumbers = true),
            globalSettings = TextDisplaySettings(),
            windowId = IdType.empty(),
        )
        assertEquals(InheritedFrom.NONE, bundle.inheritedFrom(Types.VERSENUMBERS))
    }

    @Test
    fun `inheritedFrom at WINDOW level is WORKSPACE when window null and workspace has value`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = TextDisplaySettings(),
            workspaceSettings = TextDisplaySettings(showVerseNumbers = false),
            globalSettings = TextDisplaySettings(showVerseNumbers = true),
            windowId = IdType.empty(),
        )
        assertEquals(InheritedFrom.WORKSPACE, bundle.inheritedFrom(Types.VERSENUMBERS))
    }

    @Test
    fun `inheritedFrom at WINDOW level is GLOBAL when window and workspace null`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = TextDisplaySettings(),
            workspaceSettings = TextDisplaySettings(),
            globalSettings = TextDisplaySettings(showVerseNumbers = true),
            windowId = IdType.empty(),
        )
        assertEquals(InheritedFrom.GLOBAL, bundle.inheritedFrom(Types.VERSENUMBERS))
    }

    @Test
    fun `inheritedFrom at WINDOW level is GLOBAL when pageManagerSettings is null and workspace null`() {
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = null,
            workspaceSettings = TextDisplaySettings(),
            globalSettings = TextDisplaySettings(),
            windowId = IdType.empty(),
        )
        assertEquals(InheritedFrom.GLOBAL, bundle.inheritedFrom(Types.VERSENUMBERS))
    }

    @Test
    fun `inheritedFrom survives JSON roundtrip preserving null vs non-null fields`() {
        val original = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = TextDisplaySettings(),
            workspaceSettings = TextDisplaySettings(showVerseNumbers = false, fontSize = 20),
            globalSettings = TextDisplaySettings(showRedLetters = false),
            windowId = IdType.empty(),
        )
        val restored = SettingsBundle.fromJson(original.toJson())

        // After JSON roundtrip the bundle must still correctly identify inheritance source.
        assertEquals(InheritedFrom.WORKSPACE, restored.inheritedFrom(Types.VERSENUMBERS))
        assertEquals(InheritedFrom.WORKSPACE, restored.inheritedFrom(Types.FONTSIZE))
        assertEquals(InheritedFrom.GLOBAL, restored.inheritedFrom(Types.REDLETTERS))
        assertEquals(InheritedFrom.GLOBAL, restored.inheritedFrom(Types.SECTIONTITLES))
    }

    @Test
    fun `inheritedFrom at WINDOW level uses workspace value of false (not null)`() {
        // Regression: false is a valid non-null Boolean and must not be confused with null.
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = TextDisplaySettings(),
            workspaceSettings = TextDisplaySettings(showVerseNumbers = false),
            globalSettings = TextDisplaySettings(showVerseNumbers = true),
            windowId = IdType.empty(),
        )
        assertEquals(InheritedFrom.WORKSPACE, bundle.inheritedFrom(Types.VERSENUMBERS))
    }
}
