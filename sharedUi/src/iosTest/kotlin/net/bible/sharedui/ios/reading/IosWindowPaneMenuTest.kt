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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.control.page.Document
import net.bible.android.control.page.OrdinalRange
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowView
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedui.app.bookmark.StudyPadExportPlatform
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
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

    // ----- R3 Task 5: export rows -----

    /** A view that only reports [firstDocument], as the one Android's builder and `ReadingCommands` read. */
    private class FakeWindowView(override val firstDocument: Document?) : WindowView {
        override val verseRangeLoaded: VerseRange? = null
        override var lastUpdated: Long = 0L
        override var listenEvents: Boolean = true
        override fun highlightOrdinalRange(bookInitials: String, osisRef: String, range: IntRange) = Unit
        override fun highlightBibleOrdinalRange(range: IntRange) = Unit
        override fun destroy() = Unit
        override suspend fun loadDocument(
            document: Document, updateLocation: Boolean, key: Key?, anchorOrdinal: OrdinalRange?, htmlId: String?,
            windowContentGeneration: Long?,
        ) = Unit
        override fun scrollOrJumpToVerse(key: Key, forceNow: Boolean) = Unit
        override fun scrollOrJumpToOrdinal(ordinal: OrdinalRange?, htmlId: String?, bookInitials: String?, osisRef: String?, forceNow: Boolean) = Unit
        override fun hasChapterLoaded(chapter: Int) = false
        override fun requestFocus() = false
        override fun updateTextDisplaySettings(onAttach: Boolean) = Unit
        override fun adjustLoadingCount(adj: Int) = false
    }

    private fun studyPad(label: BookmarkEntities.Label) =
        StudyPadDocument(label, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

    private class Recorder {
        val events = mutableListOf<Pair<String, String>>()
        val exported = mutableListOf<List<BookmarkEntities.Label>>()
        val destinations = mutableListOf<SaveOrShare?>()
        val askedForBookmarks = mutableListOf<BookmarkEntities.Label>()
        val csv = mutableListOf<List<BookmarkEntities.BibleBookmarkWithNotes>>()
        val bookmarkOfLabel = mutableMapOf<String, List<BookmarkEntities.BibleBookmarkWithNotes>>()
    }

    /** The platform over recording fakes, run synchronously (an unconfined scope), plus the real dispatcher over it. */
    private fun withExportPlatform(
        block: (platform: IosWindowPaneMenuPlatform, dispatcher: WindowPaneMenuDispatcher, window: Window, rec: Recorder) -> Unit,
    ) {
        withIosReadingHost { host, _ ->
            val koin = KoinPlatform.getKoin()
            val control = koin.get<WindowControl>()
            val rec = Recorder()
            val platform = IosWindowPaneMenuPlatform(
                shell = net.bible.sharedui.ios.app.IosHostShell(kotlinx.coroutines.MainScope(), NoNavigatorForHostTests),
                windowControl = { control },
                slots = { host.slots },
                clipboard = koin.get(),
                toast = {}, copiedMessage = { "" }, bridgeFor = { null },
                openPromptSelector = { _, _, _ -> },
                emitEvent = { id, call -> rec.events += id to call.js },
                studyPadExport = object : StudyPadExportPlatform {
                    override suspend fun export(labels: List<BookmarkEntities.Label>, chooseDestination: suspend () -> SaveOrShare?) {
                        rec.exported += labels
                        rec.destinations += chooseDestination()
                    }
                },
                chooseDestination = { SaveOrShare.SHARE },
                bookmarksWithLabel = { label ->
                    rec.askedForBookmarks += label
                    rec.bookmarkOfLabel[label.name].orEmpty()
                },
                exportCsv = { rec.csv += it },
                scope = CoroutineScope(Dispatchers.Unconfined),
            )
            val dispatcher = WindowPaneMenuDispatcher(
                windowControl = control,
                speakControl = koin.get<SpeakControl>(),
                windowRepository = { control.windowRepository },
                controller = { null },
                prefItems = koin.get<TextDisplayPrefItems>(),
                recentDisplaySettings = { emptyList<TextDisplaySettings.Types>() },
                globalTextDisplaySettings = { TextDisplaySettings.default },
                platform = platform,
            )
            block(platform, dispatcher, control.activeWindow, rec)
        }
    }

    @Test
    fun exportHtmlEmitsTheEventForTheWindow() = withExportPlatform { _, dispatcher, window, rec ->
        val id = window.id.toString()
        assertFalse(dispatcher.handle(id, WindowPaneMenuStateBuilder.ID_EXPORT_HTML), "the row closes the menu")
        assertEquals(listOf(id to BibleViewEvents.exportHtml().js), rec.events, "only the asking window's page is told")
    }

    @Test
    fun exportStudyPadExportsTheWindowsLabel() = withExportPlatform { _, dispatcher, window, rec ->
        val label = BookmarkEntities.Label(name = "Pad A")
        window.bibleView = FakeWindowView(studyPad(label))
        try {
            assertFalse(dispatcher.handle(window.id.toString(), WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD))
        } finally {
            window.bibleView = null
        }
        assertEquals(listOf(listOf("Pad A")), rec.exported.map { l -> l.map { it.name } }, "exactly that label")
        assertEquals(listOf<SaveOrShare?>(SaveOrShare.SHARE), rec.destinations, "the export asks through the chooser it was given")
        assertEquals(emptyList(), rec.csv)
    }

    @Test
    fun exportStudyPadCsvExportsItsBookmarks() = withExportPlatform { _, dispatcher, window, rec ->
        val label = BookmarkEntities.Label(name = "Pad B")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1"), textRange = null, wholeVerse = true, book = null,
        )
        rec.bookmarkOfLabel["Pad B"] = listOf(bookmark)
        rec.bookmarkOfLabel["Other"] = emptyList()
        window.bibleView = FakeWindowView(studyPad(label))
        try {
            assertFalse(dispatcher.handle(window.id.toString(), WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV))
        } finally {
            window.bibleView = null
        }
        assertEquals(listOf("Pad B"), rec.askedForBookmarks.map { it.name })
        assertEquals(1, rec.csv.size)
        assertTrue(rec.csv.single().single() === bookmark, "the label's own bookmarks are exported")
        assertEquals(emptyList(), rec.exported, "the CSV row does not export the archive")
    }

    /** Outside a StudyPad window the two label exports do nothing (the builder does not offer them either). */
    @Test
    fun studyPadExportsDoNothingWithoutAStudyPad() = withExportPlatform { _, dispatcher, window, rec ->
        val id = window.id.toString()
        dispatcher.handle(id, WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD)
        dispatcher.handle(id, WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV)
        assertEquals(emptyList(), rec.exported)
        assertEquals(emptyList(), rec.askedForBookmarks)
        assertEquals(emptyList(), rec.csv)
    }

    private fun paneMenuIdsFor(document: Document?): List<String> {
        var ids = emptyList<String>()
        withIosReadingHost { host, _ ->
            val window = KoinPlatform.getKoin().get<WindowControl>().activeWindow
            window.bibleView = document?.let { FakeWindowView(it) }
            try {
                ids = host.hooks.paneMenuItemsFor(window.id.toString())!!.map { it.id }
            } finally {
                window.bibleView = null
            }
        }
        return ids
    }

    /** The builder's own rule, now unfiltered: a StudyPad shows all three rows, a Bible page none. */
    @Test
    fun exportRowsHiddenOutsideStudyPads() {
        val exportRows = setOf(
            WindowPaneMenuStateBuilder.ID_EXPORT_HTML, WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD,
            WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV,
        )
        assertEquals(emptySet(), paneMenuIdsFor(null).toSet() intersect exportRows, "no document: no export rows")
        assertEquals(exportRows, paneMenuIdsFor(studyPad(BookmarkEntities.Label(name = "P"))).toSet() intersect exportRows)
    }

    @Test
    fun nothingIsOmittedAnymore() {
        assertTrue(IOS_OMITTED_PANE_MENU_ROWS.isEmpty())
        assertTrue(WindowPaneMenuStateBuilder.ID_EXPORT_HTML in IOS_IMPLEMENTED_PANE_MENU_ROWS)
        assertTrue(WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD in IOS_IMPLEMENTED_PANE_MENU_ROWS)
        assertTrue(WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV in IOS_IMPLEMENTED_PANE_MENU_ROWS)
    }
}
