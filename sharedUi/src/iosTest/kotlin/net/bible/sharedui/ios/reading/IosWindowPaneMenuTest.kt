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

package net.bible.sharedui.ios.reading

import net.bible.android.control.page.window.WindowControl
import net.bible.android.view.activity.page.WindowPaneMenuDispatcher
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import org.koin.mp.KoinPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iOS omit list and implemented list partition the dispatcher's platform rows: nothing shown that does nothing. */
class IosWindowPaneMenuTest {
    @Test
    fun implementedPlusOmittedEqualsPlatformRows() {
        assertTrue((IOS_OMITTED_PANE_MENU_ROWS intersect IOS_IMPLEMENTED_PANE_MENU_ROWS).isEmpty(), "a row is omitted or implemented, not both")
        assertEquals(WindowPaneMenuDispatcher.PLATFORM_ROWS, IOS_OMITTED_PANE_MENU_ROWS + IOS_IMPLEMENTED_PANE_MENU_ROWS)
    }

    @Test
    fun llmRowIsNoLongerOmittedAndIsImplemented() {
        assertFalse(WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU in IOS_OMITTED_PANE_MENU_ROWS)
        assertTrue(WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU in IOS_IMPLEMENTED_PANE_MENU_ROWS)
    }

    private fun paneMenuIds(llmConfigured: Boolean): List<String> {
        var ids = emptyList<String>()
        withIosReadingHost(llmConfigured = { llmConfigured }) { host, _ ->
            val active = KoinPlatform.getKoin().get<WindowControl>().activeWindow.id.toString()
            ids = host.hooks.paneMenuItemsFor(active)!!.map { it.id }
        }
        return ids
    }

    /** Android shows the AI row for a visible window when an LLM is configured. */
    @Test
    fun llmRowShownWhenConfigured() {
        assertTrue(WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU in paneMenuIds(llmConfigured = true))
    }

    @Test
    fun llmRowHiddenWhenNotConfigured() {
        assertFalse(WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU in paneMenuIds(llmConfigured = false))
    }

    /** The row's selection is the window's current page, as Android's `llmActions` builds it; no document or key: no selection. */
    @Test
    fun windowMenuSelectionNamesTheCurrentPage() {
        val selection = windowMenuSelection("KJV", "Gen.1.1")!!
        assertEquals("KJV", selection.bookInitials)
        assertEquals("Gen.1.1", selection.osisRef)
        assertEquals(-1, selection.startOrdinal)
        assertEquals(-1, selection.endOrdinal)
        assertNull(windowMenuSelection(null, "Gen.1.1"))
        assertNull(windowMenuSelection("KJV", null))
    }

    /** The row on a window with a document: WINDOW_MENU, the whole-page selection of that window, and its category. */
    @Test
    fun llmActionsOpensThePromptSelectorForTheWindowsPage() {
        val opened = mutableListOf<Triple<net.bible.android.view.activity.page.Selection, net.bible.service.llm.PromptContext, net.bible.android.control.page.DocumentCategory?>>()
        withIosReadingHost { host, _ ->
            val window = KoinPlatform.getKoin().get<WindowControl>().activeWindow
            val book = org.crosswire.jsword.book.Books.installed().getBook("K4RawText")!!
            window.pageManager.setCurrentDocumentAndKey(book, book.getKey("Gen.1.1"))
            val page = window.pageManager.currentPage
            val platform = IosWindowPaneMenuPlatform(
                shell = net.bible.sharedui.ios.app.IosHostShell(kotlinx.coroutines.MainScope(), NoNavigatorForHostTests),
                windowControl = { KoinPlatform.getKoin().get() },
                slots = { host.slots },
                clipboard = KoinPlatform.getKoin().get(),
                toast = {}, copiedMessage = { "" }, bridgeFor = { null },
                openPromptSelector = { s, c, k -> opened += Triple(s, c, k) },
            )

            platform.llmActions(window)

            assertEquals(1, opened.size)
            val (selection, context, category) = opened.single()
            assertEquals(net.bible.service.llm.PromptContext.WINDOW_MENU, context)
            assertEquals("K4RawText", selection.bookInitials)
            assertEquals(page.key!!.getOsisRef(), selection.osisRef)
            assertEquals(-1, selection.startOrdinal)
            assertEquals(-1, selection.endOrdinal)
            assertEquals(page.documentCategory, category)
        }
    }
}
