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

package net.bible.sharedcore.reading

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawerMenuStateTest {

    private fun spec(id: String, visible: Boolean = true, enabled: Boolean = true) =
        DrawerItemSpec(id = id, label = id.uppercase(), iconKey = "ic_$id", enabled = enabled, visible = visible)

    @Test
    fun build_preservesDeclarationOrder() {
        val state = DrawerMenu.build(
            appName = "AndBible",
            versionText = "Version 5.1",
            groups = listOf(
                DrawerGroupSpec(title = null, items = listOf(spec("a"), spec("b"), spec("c"))),
            ),
        )
        assertEquals(listOf("a", "b", "c"), state.groups.single().items.map { it.id })
        assertEquals("AndBible", state.appName)
        assertEquals("Version 5.1", state.versionText)
    }

    @Test
    fun build_dropsInvisibleItems() {
        val state = DrawerMenu.build(
            appName = "AndBible", versionText = "v",
            groups = listOf(
                DrawerGroupSpec(title = null, items = listOf(spec("a"), spec("hidden", visible = false), spec("b"))),
            ),
        )
        assertEquals(listOf("a", "b"), state.groups.single().items.map { it.id })
    }

    @Test
    fun build_dropsGroupLeftEmptyByFiltering() {
        val state = DrawerMenu.build(
            appName = "AndBible", versionText = "v",
            groups = listOf(
                DrawerGroupSpec(title = null, items = listOf(spec("a"))),
                DrawerGroupSpec(title = "Contact", items = listOf(spec("onlyOne", visible = false))),
            ),
        )
        assertEquals(1, state.groups.size)
        assertEquals(null, state.groups.single().title)
    }

    @Test
    fun build_keepsDisabledItemsButMarksThemDisabled() {
        val state = DrawerMenu.build(
            appName = "AndBible", versionText = "v",
            groups = listOf(
                DrawerGroupSpec(title = null, items = listOf(spec("search", enabled = false), spec("b"))),
            ),
        )
        val items = state.groups.single().items
        assertEquals(listOf("search", "b"), items.map { it.id })
        assertTrue(!items[0].enabled)
        assertTrue(items[1].enabled)
    }

    @Test
    fun build_carriesLabelAndIconKeyThrough() {
        val state = DrawerMenu.build(
            appName = "AndBible", versionText = "v",
            groups = listOf(DrawerGroupSpec(title = "Administration", items = listOf(spec("download")))),
        )
        val item = state.groups.single().items.single()
        assertEquals("DOWNLOAD", item.label)
        assertEquals("ic_download", item.iconKey)
        assertEquals("Administration", state.groups.single().title)
    }

    @Test
    fun empty_hasNoGroups() {
        assertEquals(emptyList(), DrawerMenuState.EMPTY.groups)
    }
}
