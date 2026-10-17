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

package net.bible.service.llm.tools

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.llm.ToolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pins the iteration order of [ToolRegistry.getConfigurableToolsByCategory] (enum order). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolRegistryCategoryOrderTest {

    @Test
    fun categoriesAreListedInEnumOrder() {
        val keys = ToolRegistry.getConfigurableToolsByCategory().keys.toList()
        assertTrue("expected several categories", keys.size > 1)
        assertEquals(keys.sortedBy { it.ordinal }, keys)
        assertEquals(ToolCategory.entries.filter { it in keys }, keys)
    }
}
