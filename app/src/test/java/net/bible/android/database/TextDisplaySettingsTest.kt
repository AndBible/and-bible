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
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
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
}
