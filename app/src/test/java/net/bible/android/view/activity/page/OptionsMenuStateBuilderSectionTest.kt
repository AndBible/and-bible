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
package net.bible.android.view.activity.page

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities
import net.bible.service.common.CommonUtils
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [OptionsMenuStateBuilder.build]'s classic-parity reorder (batch 5 F1): `allTextOptions`
 * moved below the recent-settings rows, and the new [net.bible.sharedcore.reading.OptionsMenuItem.startsNewSection]
 * flag marking where the renderer should draw a divider. Exercises [OptionsMenuStateBuilder.build]
 * directly with a minimal fake [OptionsMenuItemInterface] (every static/dynamic row visible and
 * enabled) rather than the full [MainBibleActivity]/[net.bible.android.control.page.window.WindowRepository]
 * graph [OptionsMenuStateBuilderTest] builds — this test only cares about ordering/section flags,
 * not any individual item's real behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class OptionsMenuStateBuilderSectionTest {

    /**
     * `:app`'s unit test suite runs in ONE JVM with no `maxParallelForks`, so a test that leaves
     * `lastDisplaySettings` seeded leaks into whichever test runs next. Reset unconditionally
     * before every test (not just the three that want "no recents") rather than repeating the
     * null-out per test — this doubles as this class's OWN prior-test isolation, not just its
     * neighbours'.
     */
    @Before
    fun resetLastDisplaySettings() {
        CommonUtils.settings.setString("lastDisplaySettings", null)
    }

    /** Minimal fake for one menu row; only the fields `build()` reads. */
    private fun option(
        visible: Boolean = true,
        enabled: Boolean = true,
        title: String? = null,
    ): OptionsMenuItemInterface = object : OptionsMenuItemInterface {
        override var enabled = enabled
        override val visible = visible
        override val inherited = false
        override val title: String? = title
        override val summary: String? = null
        override val icon: Int? = null
        override var value: Any = false
        override val isBoolean = false
        override val opensDialog = false
        override fun handle() {}
    }

    @Test
    fun `all text options is the last row, below the recent settings rows`() {
        val itemsWithNoRecents = OptionsMenuStateBuilder.build { _, _ -> option() }
        assertEquals("allTextOptions", itemsWithNoRecents.last().id)
    }

    @Test
    fun `with no recent rows the divider falls before all text options`() {
        val items = OptionsMenuStateBuilder.build { _, _ -> option() }
        val sectionStarts = items.filter { it.startsNewSection }.map { it.id }
        assertEquals(listOf("allTextOptions"), sectionStarts)
    }

    @Test
    fun `no row before the first is ever a section start`() {
        val items = OptionsMenuStateBuilder.build { _, _ -> option() }
        assertFalse(items.first().startsNewSection)
    }

    @Test
    fun `the divider falls before the first recent row, and all text options stays last`() {
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.FONTSIZE)
        CommonUtils.displaySettingChanged(WorkspaceEntities.TextDisplaySettings.Types.MARGINSIZE)

        val items = OptionsMenuStateBuilder.build { resId, order ->
            option(title = if (resId == R.id.textOptionItem) "recent$order" else null)
        }

        val recentIndices = items.withIndex().filter { it.value.id.startsWith("textOptionItem:") }.map { it.index }
        assertTrue(recentIndices.isNotEmpty(), "expected recent rows")
        assertEquals("allTextOptions", items.last().id)
        assertTrue(recentIndices.max() < items.lastIndex, "recents come before allTextOptions")
        assertEquals(listOf(items[recentIndices.min()].id), items.filter { it.startsNewSection }.map { it.id })
    }
}
