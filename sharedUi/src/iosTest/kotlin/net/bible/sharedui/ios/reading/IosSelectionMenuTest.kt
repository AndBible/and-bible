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

import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.SelectionMenuItem
import net.bible.sharedui.strings.keyed.KeyedSelectionMenuStrings
import net.bible.sharedui.strings.keyed.StringTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.sharedui.host.reading.IosSelectionMenu
import net.bible.sharedui.host.reading.SelectionMenuEnvironment
import net.bible.sharedui.host.reading.SelectionMenuWindow

/** The selection menu's cache, policy wiring and dispatch, over fake windows (the production wiring is Koin and WebKit bound). */
class IosSelectionMenuTest {
    private class FakeWindow(override val isBible: Boolean = true) : SelectionMenuWindow {
        val calls = mutableListOf<String>()
        var lastSelection: Selection? = null
        override fun makeBookmark(selection: Selection?, wholeVerse: Boolean) { lastSelection = selection; calls += "bookmark wholeVerse=$wholeVerse" }
        override fun addParagraphBreak(selection: Selection?) { calls += "paragraph" }
        override fun compare(selection: Selection?) { calls += "compare" }
        override fun memorize(selection: Selection?) { calls += "memorize" }
        override fun share(selection: Selection) { calls += "share" }
        override fun clearSelection() { calls += "clear" }
    }

    private class FakeEnvironment : SelectionMenuEnvironment {
        override var twoStepDisabled = false
        override var paragraphBreakEnabled = false
        override var hasLookupDictionaries = false
        override var llmConfigured = false
        val calls = mutableListOf<String>()
        override fun resolveRef(text: String): String? = if (text.startsWith("John")) text else null
        override fun openRef(text: String) { calls += "openRef $text" }
        override fun lookupInDictionaries(text: String) { calls += "lookup $text" }
        override fun search(text: String) { calls += "search $text" }
        override fun webSearch(text: String) { calls += "web $text" }
        override fun copy(text: String) { calls += "copy $text" }
    }

    // The real table construction: lookups answer a template (the key itself, except the one format string the test reads
    // through its placeholder) and `fmt` fills it, so the Search row's label goes through the production formatting path.
    private val table = StringTable.of("en", { if (it == "search_what") "Find \"%s\"" else it }, { n, _ -> n }, { emptyList() })

    private fun verseJson(text: String, ordinal: Int = 1) =
        """{"bookInitials":"KJV","startOrdinal":$ordinal,"startOffset":0,"endOrdinal":$ordinal,"endOffset":5,"bookmarks":[],"text":"$text"}"""

    private class Fixture(val windows: Map<String, FakeWindow>, val env: FakeEnvironment, val menu: IosSelectionMenu, val llm: MutableList<String>)

    private fun fixture(vararg ids: String, bible: Boolean = true): Fixture {
        val windows = ids.associateWith { FakeWindow(bible) }
        val env = FakeEnvironment()
        val llm = mutableListOf<String>()
        val menu = IosSelectionMenu(
            windows = { windows[it] }, environment = env, strings = { KeyedSelectionMenuStrings(table) },
            onLlmAction = { id, sel -> llm += "$id:${sel.text}" },
        )
        return Fixture(windows, env, menu, llm)
    }

    /** The bookmark row creates the bookmark in the window the menu was shown for, with that window's cached selection. */
    @Test
    fun bookmarkRowCreatesABookmarkInTheSelectedWindow() {
        val f = fixture("w1", "w2")
        f.env.twoStepDisabled = true
        f.menu.cacheSelection("w1", verseJson("first"))
        f.menu.cacheSelection("w2", verseJson("second", ordinal = 2))
        assertTrue(f.menu.items("w2").containsAll(listOf(SelectionMenuItem.AddBookmarkSelection, SelectionMenuItem.AddBookmarkWholeVerse)))

        f.menu.run("w2", SelectionMenuItem.AddBookmarkWholeVerse)

        assertEquals(listOf("bookmark wholeVerse=true", "clear"), f.windows.getValue("w2").calls)
        assertEquals("second", f.windows.getValue("w2").lastSelection?.text)
        assertEquals(2, f.windows.getValue("w2").lastSelection?.startOrdinal)
        assertEquals(emptyList(), f.windows.getValue("w1").calls, "the other window is untouched")
        assertEquals("first", f.menu.snapshot("w1")?.text, "the other window's selection survives")
        assertNull(f.menu.snapshot("w2"), "the used selection is consumed")
    }

    /** Two-step on a Bible: the "Bookmark" row only opens step two; a non-Bible bookmarks straight away. */
    @Test
    fun twoStepRowOpensChoicesAndANonBibleBookmarksAtOnce() {
        val bible = fixture("w1")
        bible.menu.cacheSelection("w1", verseJson("x"))
        assertTrue(bible.menu.opensStepTwo("w1", SelectionMenuItem.AddBookmark))
        assertEquals(
            listOf(SelectionMenuItem.AddBookmarkSelection, SelectionMenuItem.AddBookmarkWholeVerse),
            bible.menu.stepTwoRows("w1").map { it.item },
        )
        bible.menu.run("w1", SelectionMenuItem.AddBookmark)
        assertEquals(emptyList(), bible.windows.getValue("w1").calls, "nothing is bookmarked by the step-one row")
        assertEquals("x", bible.menu.snapshot("w1")?.text, "and the selection is kept for step two")

        val other = fixture("w1", bible = false)
        other.menu.cacheSelection("w1", verseJson("x"))
        assertFalse(other.menu.opensStepTwo("w1", SelectionMenuItem.AddBookmark))
        other.menu.run("w1", SelectionMenuItem.AddBookmark)
        assertEquals(listOf("bookmark wholeVerse=false", "clear"), other.windows.getValue("w1").calls)
    }

    /** The menu answers from what was cached before it was built: a later page report replaces it, nothing is queried. */
    @Test
    fun menuUsesTheCachedSelection() {
        val f = fixture("w1")
        assertEquals(emptyList(), f.menu.items("w1"), "no selection cached: no AndBible rows")

        assertTrue(f.menu.cacheSelection("w1", "\"John 3:16\""), "a plain-text selection (a string) is cached")
        val textOnly = f.menu.snapshot("w1")!!
        assertNull(textOnly.selection)
        assertEquals("John 3:16", textOnly.text)
        assertEquals("John 3:16", textOnly.refName)
        val items = f.menu.items("w1")
        assertTrue(SelectionMenuItem.OpenRef in items && SelectionMenuItem.Search !in items)
        assertTrue(SelectionMenuItem.AddBookmark !in items, "no verse selection: no bookmark rows")

        assertTrue(f.menu.cacheSelection("w1", verseJson("grace")), "a changed selection is reported as changed")
        assertFalse(f.menu.cacheSelection("w1", verseJson("grace")), "the same selection is not")
        val rows = f.menu.rows("w1")
        assertTrue(SelectionMenuItem.AddBookmark in rows.map { it.item })
        assertTrue(rows.single { it.item == SelectionMenuItem.Search }.title.contains("grace"))

        // A snapshot taken at build time still drives run after the page reports "no selection".
        val snapshot = f.menu.snapshot("w1")
        assertTrue(f.menu.cacheSelection("w1", "null"))
        assertEquals(emptyList(), f.menu.items("w1"))
        f.menu.run("w1", SelectionMenuItem.Compare, snapshot)
        assertEquals(listOf("compare", "clear"), f.windows.getValue("w1").calls)
        f.menu.run("w1", SelectionMenuItem.Compare)
        assertEquals(2, f.windows.getValue("w1").calls.size, "without a selection nothing runs")
    }

    @Test
    fun actionsGoToTheirPorts() {
        val f = fixture("w1")
        f.env.hasLookupDictionaries = true
        f.menu.cacheSelection("w1", verseJson("word"))
        val snapshot = f.menu.snapshot("w1")
        for (item in listOf(SelectionMenuItem.Search, SelectionMenuItem.WebSearch, SelectionMenuItem.LookupDictionary, SelectionMenuItem.Copy)) {
            f.menu.run("w1", item, snapshot)
        }
        assertEquals(listOf("search word", "web word", "lookup word", "copy word"), f.env.calls)
        f.menu.run("w1", SelectionMenuItem.ShareVerses, snapshot)
        f.menu.run("w1", SelectionMenuItem.Memorize, snapshot)
        // Every run ends with a "clear" (the selection is consumed), so compare the actions alone.
        assertEquals(listOf("share", "memorize"), f.windows.getValue("w1").calls.filter { it != "clear" })
        assertEquals(6, f.windows.getValue("w1").calls.count { it == "clear" }, "one clear per run")
    }

    /** The AI row stays hidden until the AI is configured (Task 4 wires it); once shown it calls the hook with the verse selection. */
    @Test
    fun llmRowIsHiddenUntilConfiguredAndThenCallsTheHook() {
        val f = fixture("w1")
        f.menu.cacheSelection("w1", verseJson("amen"))
        assertFalse(SelectionMenuItem.LlmAction in f.menu.items("w1"))
        f.env.llmConfigured = true
        assertTrue(SelectionMenuItem.LlmAction in f.menu.items("w1"))
        f.menu.run("w1", SelectionMenuItem.LlmAction)
        assertEquals(listOf("w1:amen"), f.llm)
    }

    @Test
    fun releasedWindowRunsNothing() {
        val f = fixture("w1")
        f.menu.cacheSelection("gone", verseJson("x"))
        f.menu.run("gone", SelectionMenuItem.Compare)
        assertEquals(emptyList(), f.menu.items("gone"), "an unknown window shows no rows")
        assertEquals("x", f.menu.snapshot("gone")?.text, "an unknown window runs nothing and keeps its cache")
        f.menu.clear("gone")
        assertNull(f.menu.snapshot("gone"))
    }
}
