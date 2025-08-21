/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.WorkspaceEntities.WindowLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class WindowLayoutTest {

    @Test
    fun testWindowLayoutDefaultConstructor() {
        // Test creating WindowLayout with only state parameter
        val layout = WindowLayout("VISIBLE")
        assertEquals("VISIBLE", layout.state)
        assertEquals(1.0f, layout.weight, 0.001f)
    }

    @Test
    fun testWindowLayoutExplicitWeight() {
        // Test creating WindowLayout with explicit weight
        val layout = WindowLayout("VISIBLE", 2.5f)
        assertEquals("VISIBLE", layout.state)
        assertEquals(2.5f, layout.weight, 0.001f)
    }

    @Test
    fun testWindowLayoutCopy() {
        // Test copying WindowLayout preserves weight
        val original = WindowLayout("VISIBLE", 3.0f)
        val copy = original.copy()
        assertEquals("VISIBLE", copy.state)
        assertEquals(3.0f, copy.weight, 0.001f)
    }

    @Test
    fun testWindowLayoutCopyWithModifications() {
        // Test copying WindowLayout with state modifications
        val original = WindowLayout("VISIBLE", 2.0f)
        val copy = original.copy(state = "MINIMISED")
        assertEquals("MINIMISED", copy.state)
        assertEquals(2.0f, copy.weight, 0.001f)
    }

    @Test
    fun testWindowEntityWithWindowLayout() {
        // Test creating Window entity with WindowLayout
        val windowLayout = WindowLayout("VISIBLE", 1.5f)
        val window = WorkspaceEntities.Window(
            workspaceId = IdType(),
            isSynchronized = true,
            isPinMode = false,
            isLinksWindow = false,
            windowLayout = windowLayout
        )
        
        assertEquals("VISIBLE", window.windowLayout.state)
        assertEquals(1.5f, window.windowLayout.weight, 0.001f)
    }

    @Test
    fun testWindowEntityCopy() {
        // Test copying Window entity preserves WindowLayout weight
        val windowLayout = WindowLayout("VISIBLE", 2.0f)
        val window = WorkspaceEntities.Window(
            workspaceId = IdType(),
            isSynchronized = true,
            isPinMode = false,
            isLinksWindow = false,
            windowLayout = windowLayout
        )
        
        val copy = window.deepCopy()
        assertEquals("VISIBLE", copy.windowLayout.state)
        assertEquals(2.0f, copy.windowLayout.weight, 0.001f)
    }

    @Test
    fun testWindowLayoutFromNullEntity() {
        // Test WindowLayout creation from null entity - should use default weight
        val windowLayout = net.bible.android.control.page.window.WindowLayout(null)
        assertEquals(net.bible.android.control.page.window.WindowLayout.WindowState.VISIBLE, windowLayout.state)
        assertEquals(1.0f, windowLayout.weight, 0.001f)
    }

    @Test
    fun testWindowLayoutFromValidEntity() {
        // Test WindowLayout creation from valid entity
        val entity = WindowLayout("MINIMISED", 3.0f)
        val windowLayout = net.bible.android.control.page.window.WindowLayout(entity)
        assertEquals(net.bible.android.control.page.window.WindowLayout.WindowState.MINIMISED, windowLayout.state)
        assertEquals(3.0f, windowLayout.weight, 0.001f)
    }

    @Test
    fun testWindowLayoutInvalidWeight() {
        // Test WindowLayout construction with invalid weight values
        try {
            WindowLayout("VISIBLE", 0.0f) // zero weight should fail
            fail("Expected IllegalArgumentException for zero weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("weight must be a positive finite number"))
        }

        try {
            WindowLayout("VISIBLE", -1.0f) // negative weight should fail
            fail("Expected IllegalArgumentException for negative weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("weight must be a positive finite number"))
        }

        try {
            WindowLayout("VISIBLE", Float.NaN) // NaN weight should fail
            fail("Expected IllegalArgumentException for NaN weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("weight must be a positive finite number"))
        }
    }
}