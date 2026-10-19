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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The row ids the dispatcher switches on: dynamic ids round-trip, static ones parse as themselves, junk is refused. */
class WindowPaneMenuIdsTest {
    @Test fun dynamicIdsRoundTrip() {
        assertEquals(WindowPaneMenuStateBuilder.ParsedId.MoveItem(2), WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForMoveItem(2)))
        assertEquals(WindowPaneMenuStateBuilder.ParsedId.SyncGroupItem(5), WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForSyncGroupItem(5)))
        assertEquals(WindowPaneMenuStateBuilder.ParsedId.TextOptionItem(0), WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForTextOptionItem(0)))
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.CopySettingsToWindow(1),
            WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.idForCopySettingsToWindow(1)),
        )
    }

    @Test fun staticIdsParseAsThemselves() {
        for (id in listOf(WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE, WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_SUBMENU)) {
            assertEquals(WindowPaneMenuStateBuilder.ParsedId.StaticItem(id), WindowPaneMenuStateBuilder.parseId(id))
        }
    }

    @Test fun anUnknownOrStaleIdIsRefused() {
        assertFailsWith<IllegalArgumentException> { WindowPaneMenuStateBuilder.parseId("nope") }
    }
}

/** Every static row id is either run by the dispatcher itself, routed to the platform, or a submenu container. */
class WindowPaneMenuRowCoverageTest {
    private val containers = setOf(
        WindowPaneMenuStateBuilder.ID_MOVE_WINDOW_SUBMENU, WindowPaneMenuStateBuilder.ID_SYNC_GROUP_SUBMENU,
        WindowPaneMenuStateBuilder.ID_TEXT_OPTIONS_SUBMENU, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_SUBMENU,
    )
    private val runByDispatcher = setOf(
        WindowPaneMenuStateBuilder.ID_WINDOW_NEW, WindowPaneMenuStateBuilder.ID_WINDOW_MAXIMISE,
        WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE, WindowPaneMenuStateBuilder.ID_CHANGE_TO_NORMAL,
        WindowPaneMenuStateBuilder.ID_PIN_MODE, WindowPaneMenuStateBuilder.ID_DISABLE_SYNC,
        WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE, WindowPaneMenuStateBuilder.ID_GO_TO_REFERENCE,
        WindowPaneMenuStateBuilder.ID_GO_TO_SPEAK, WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WORKSPACE,
        WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_GLOBAL,
    )

    @Test fun everyStaticIdIsClassified() {
        val classified = containers + runByDispatcher + WindowPaneMenuDispatcher.PLATFORM_ROWS
        assertEquals(emptySet(), WindowPaneMenuStateBuilder.staticIds - classified, "static ids nobody handles")
        assertEquals(emptySet(), classified - WindowPaneMenuStateBuilder.staticIds, "classified ids the builder does not know")
        assertTrue((runByDispatcher intersect WindowPaneMenuDispatcher.PLATFORM_ROWS).isEmpty())
    }
}
