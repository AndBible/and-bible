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
package net.bible.sharedui.desktop.reading

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.sharedui.desktop.webengine.DesktopWebPane
import net.bible.sharedui.desktop.webengine.kcef.SuppressChromiumMenuHandler
import net.bible.sharedui.host.reading.HostSelectionMenu
import net.bible.sharedui.host.reading.SelectionMenuEnvironment
import net.bible.sharedui.host.reading.SelectionMenuWindow
import net.bible.sharedui.strings.keyed.KeyedSelectionMenuStrings
import net.bible.sharedui.strings.keyed.StringTable
import org.cef.callback.CefMenuModel
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSelectionMenuTest {
    private class FakeWindow(override val isBible: Boolean = true) : SelectionMenuWindow {
        val calls = mutableListOf<String>()
        override fun makeBookmark(selection: Selection?, wholeVerse: Boolean) { calls += "bookmark wholeVerse=$wholeVerse" }
        override fun addParagraphBreak(selection: Selection?) { calls += "paragraph" }
        override fun compare(selection: Selection?) { calls += "compare" }
        override fun memorize(selection: Selection?) { calls += "memorize" }
        override fun share(selection: Selection) { calls += "share" }
        override fun clearSelection() { calls += "clear" }
    }

    private class FakeEnvironment : SelectionMenuEnvironment {
        override val twoStepDisabled = false
        override val paragraphBreakEnabled = false
        override val hasLookupDictionaries = false
        override val llmConfigured = false
        val calls = mutableListOf<String>()
        override fun resolveRef(text: String): String? = null
        override fun openRef(text: String) { calls += "openRef $text" }
        override fun lookupInDictionaries(text: String) { calls += "lookup $text" }
        override fun search(text: String) { calls += "search $text" }
        override fun webSearch(text: String) { calls += "web $text" }
        override fun copy(text: String) { calls += "copy $text" }
    }

    private class FakePane : DesktopWebPane {
        val scripts = mutableListOf<String>()
        override val id = "p"
        override val isMainThread = true
        override val isAttached = true
        override val mainDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
        override fun post(block: () -> Unit) = block()
        override fun evaluate(js: String, callback: ((String) -> Unit)?) { scripts += js }

        @Composable
        override fun Content(modifier: Modifier) {}
        override fun dispose() {}
    }

    private val table = StringTable.of("en", { if (it == "search_what") "Find \"%s\"" else it }, { n, _ -> n }, { emptyList() })
    private val window = FakeWindow()
    private val env = FakeEnvironment()
    private val logic = HostSelectionMenu({ if (it == "w1") window else null }, env, { KeyedSelectionMenuStrings(table) })
    private val pane = FakePane()
    private var night = false
    private val menu = DesktopSelectionMenu({ logic }, { night })
    private val input = DesktopReadingInputImpl(
        DesktopShortcuts(object : DesktopShortcutActions by NoActions {}) { false }, DesktopReadingGestures({ null }), {}, menu,
    ).also { it.onPaneCreated("w1", pane) }

    private fun verse(text: String = "In the beginning") =
        """{"bookInitials":"KJV","startOrdinal":1,"startOffset":0,"endOrdinal":1,"endOffset":5,"bookmarks":[],"text":"$text"}"""

    private fun quoted(json: String) = "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun contextMenu(selectionJson: String?, x: Int = 120, y: Int = 340) = input.handleMessage(
        "w1",
        """{"desktopInput":{"type":"contextmenu","x":$x,"y":$y,"selection":${selectionJson?.let(::quoted) ?: "null"}}}""",
    )

    private fun menuItem(item: String) = input.handleMessage("w1", """{"desktopInput":{"type":"menuItem","item":"$item"}}""")

    @Test fun chromiumMenuSuppressed() {
        val calls = mutableListOf<String>()
        val model = Proxy.newProxyInstance(CefMenuModel::class.java.classLoader, arrayOf(CefMenuModel::class.java)) { _, method, _ ->
            calls += method.name
            when (method.returnType) { java.lang.Boolean.TYPE -> true; Integer.TYPE -> 0; else -> null }
        } as CefMenuModel
        SuppressChromiumMenuHandler().onBeforeContextMenu(null, null, null, model)
        assertEquals(listOf("clear"), calls)
        SuppressChromiumMenuHandler().onBeforeContextMenu(null, null, null, null) // no model: no crash
    }

    @Test fun rightClickOpensSelectionMenu() {
        assertTrue(contextMenu(verse()))
        val script = pane.scripts.single()
        assertTrue(script.startsWith("window.__abMenu&&window.__abMenu.show("), script)
        for (label in listOf("add_bookmark1", "compare_abbreviation", "web_search")) assertTrue("\"$label\"" in script, "$label in $script")
        assertTrue(script.endsWith(",120.0,340.0,false);"), script)
        // the two-step parent carries its choices as a submenu
        assertTrue("\"sub\":[{\"item\":\"AddBookmarkSelection\"" in script, script)

        night = true
        contextMenu(verse(), 5, 6)
        assertTrue(pane.scripts.last().endsWith(",5.0,6.0,true);"))
    }

    @Test fun noSelectionOrUnknownWindowEvaluatesNothing() {
        contextMenu(null)
        contextMenu("null")
        input.handleMessage("other", """{"desktopInput":{"type":"contextmenu","x":1,"y":1,"selection":${quoted(verse())}}}""")
        assertEquals(emptyList(), pane.scripts)
    }

    @Test fun labelsAreJsonEncodedIntoTheScript() {
        val text = "a\"</script>\u2028b" // quotes, a script end tag and a line separator, short enough for the Find label
        val json = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer<String>(), text)
        contextMenu(json)
        val script = pane.scripts.single()
        assertTrue('\u2028' !in script, "the line separator is escaped")
        val rows = kotlinx.serialization.json.Json.parseToJsonElement(
            script.removePrefix("window.__abMenu&&window.__abMenu.show(").substringBeforeLast(",120.0"),
        ) as kotlinx.serialization.json.JsonArray
        val search = rows.map { it as kotlinx.serialization.json.JsonObject }.first { (it["item"] as kotlinx.serialization.json.JsonPrimitive).content == "Search" }
        assertEquals("Find \"$text\"", (search["label"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test fun menuActionsMatchIos() {
        contextMenu(verse())
        val expectedRows = logic.rows("w1", logic.snapshot("w1"))
        val expectedSub = logic.stepTwoRows("w1", logic.snapshot("w1"))
        val shown = kotlinx.serialization.json.Json.parseToJsonElement(
            pane.scripts.single().removePrefix("window.__abMenu&&window.__abMenu.show(").substringBeforeLast(",120.0"),
        ) as kotlinx.serialization.json.JsonArray
        fun f(o: kotlinx.serialization.json.JsonElement, k: String) =
            (o as kotlinx.serialization.json.JsonObject)[k]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content }
        assertEquals(expectedRows.map { it.item.name to it.title }, shown.map { f(it, "item") to f(it, "label") })
        val sub = (shown.first { f(it, "item") == "AddBookmark" } as kotlinx.serialization.json.JsonObject)["sub"] as kotlinx.serialization.json.JsonArray
        assertEquals(expectedSub.map { it.item.name to it.title }, sub.map { f(it, "item") to f(it, "label") })
        assertTrue(shown.filter { f(it, "item") != "AddBookmark" }.all { (it as kotlinx.serialization.json.JsonObject)["sub"] == null })

        // a parent row does nothing; a leaf runs exactly what iOS's run does, then the selection is consumed
        menuItem("AddBookmark")
        assertEquals(emptyList(), window.calls)
        menuItem("AddBookmarkWholeVerse")
        assertEquals(listOf("bookmark wholeVerse=true", "clear"), window.calls)
        assertNull(logic.snapshot("w1"))
        menuItem("Copy") // the snapshot was consumed: nothing more runs
        assertEquals(emptyList(), env.calls)
    }

    @Test fun textActionRunsOnTheEnvironmentAndClosingForgetsTheSnapshot() {
        contextMenu(""""some words"""")
        menuItem("Copy")
        assertEquals(listOf("copy some words"), env.calls)

        contextMenu(""""other"""")
        input.handleMessage("w1", """{"desktopInput":{"type":"menuClosed"}}""")
        menuItem("Copy")
        assertEquals(listOf("copy some words"), env.calls)
    }

    @Test fun pageScriptHonoursThePagesOwnHandlerAndEmptySelections() {
        val part = DesktopSelectionMenu.PART
        assertTrue("e.defaultPrevented" in part && "sel.isCollapsed" in part)
        assertTrue("stopImmediatePropagation" in part && "'Escape'" in part)
        assertTrue(DesktopSelectionMenu.PART in input.scriptParts)
        assertTrue("textContent" in part && "innerHTML" !in part)
    }

    private object NoActions : DesktopShortcutActions {
        override fun isOnReadingScreen() = false
        override fun activeWindowId(): String? = null
        override fun search(windowId: String) {}
        override fun goToReference(windowId: String) {}
        override fun back(historyFallback: Boolean) {}
        override fun navigateBack(windowId: String?) {}
        override fun bookmarkSelection(windowId: String, selectionJson: String) {}
        override fun changeFontSize(change: FontSizeChange, steps: Int) {}
        override fun focusNextWindow() {}
        override fun focusPreviousWindow() {}
        override fun toggleFullScreen() {}
        override fun openSettings() {}
        override fun escapeAllowed(windowId: String) = true
    }
}
