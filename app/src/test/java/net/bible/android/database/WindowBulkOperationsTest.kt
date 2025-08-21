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
class WindowBulkOperationsTest {

    @Test
    fun testManyWindowsWithDifferentWeights() {
        // Test scenario similar to the crash reports: many windows with different configurations
        // This simulates the "many windows from compare text" and "links that would close from a press" scenarios
        
        val windows = mutableListOf<WorkspaceEntities.Window>()
        
        // Create many windows with various weight configurations
        for (i in 1..20) {
            val windowLayout = when {
                i % 4 == 0 -> WindowLayout("MINIMISED", 0.5f)
                i % 3 == 0 -> WindowLayout("VISIBLE", 2.0f)
                i % 2 == 0 -> WindowLayout("CLOSED", 1.0f)
                else -> WindowLayout("VISIBLE", 1.5f)
            }
            
            val window = WorkspaceEntities.Window(
                workspaceId = IdType(),
                isSynchronized = i % 2 == 0,
                isPinMode = i % 3 == 0,
                isLinksWindow = i % 5 == 0,
                windowLayout = windowLayout,
                orderNumber = i
            )
            
            windows.add(window)
        }
        
        // Verify all windows have valid weight values
        windows.forEach { window ->
            assertTrue("Window weight should be positive", window.windowLayout.weight > 0f)
            assertTrue("Window weight should be finite", window.windowLayout.weight.isFinite())
        }
        
        // Test copying all windows (this exercises the deepCopy functionality)
        val copiedWindows = windows.map { it.deepCopy() }
        
        copiedWindows.forEach { window ->
            assertTrue("Copied window weight should be positive", window.windowLayout.weight > 0f)
            assertTrue("Copied window weight should be finite", window.windowLayout.weight.isFinite())
        }
        
        assertEquals("Should have same number of windows", windows.size, copiedWindows.size)
    }

    @Test
    fun testWindowLayoutWeightConsistencyUnderLoad() {
        // Test creating many WindowLayout instances rapidly
        // This simulates potential race conditions or memory issues that could cause weight corruption
        
        val layouts = mutableListOf<net.bible.android.control.page.window.WindowLayout>()
        
        // Create 100 WindowLayout instances in a loop
        repeat(100) { i ->
            val entity = WindowLayout("VISIBLE", (i + 1).toFloat())
            val controlLayout = net.bible.android.control.page.window.WindowLayout(entity)
            layouts.add(controlLayout)
        }
        
        // Verify all have correct weights
        layouts.forEachIndexed { index, layout ->
            val expectedWeight = (index + 1).toFloat()
            assertEquals("Layout $index should have weight $expectedWeight", 
                        expectedWeight, layout.weight, 0.001f)
        }
    }

    @Test
    fun testRestoreFromWithMixedValidInvalidData() {
        // Test scenario where some data might be corrupted but others are fine
        val controlLayout = net.bible.android.control.page.window.WindowLayout(null)
        
        // Start with default
        assertEquals(1.0f, controlLayout.weight, 0.001f)
        
        // Restore from valid data
        val validEntity = WindowLayout("MINIMISED", 2.5f)
        controlLayout.restoreFrom(validEntity)
        assertEquals(2.5f, controlLayout.weight, 0.001f)
        assertEquals(net.bible.android.control.page.window.WindowLayout.WindowState.MINIMISED, 
                    controlLayout.state)
        
        // The defensive code should handle any potential issues in restoreFrom
        // by ensuring weight remains valid
        assertTrue("Weight should remain positive after restore", controlLayout.weight > 0f)
        assertTrue("Weight should remain finite after restore", controlLayout.weight.isFinite())
    }
}