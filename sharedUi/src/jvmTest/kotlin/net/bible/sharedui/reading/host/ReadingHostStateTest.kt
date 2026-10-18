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

package net.bible.sharedui.reading.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.DbDate
import net.bible.android.database.IdType
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryPlatform
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.android.view.activity.page.screen.ReadingDialog

class ReadingHostStateTest {
    private class MapSettings : AppSettings {
        val map = mutableMapOf<String, Any?>()
        @Suppress("UNCHECKED_CAST")
        private fun <T> get(key: String, default: T): T = if (map.containsKey(key)) map[key] as T else default
        override fun getString(key: String, default: String?) = get(key, default)
        override fun getLong(key: String, default: Long) = get(key, default)
        override fun getInt(key: String, default: Int) = get(key, default)
        override fun getBoolean(key: String, default: Boolean) = get(key, default)
        override fun getDouble(key: String, default: Double) = get(key, default)
        override fun getFloat(key: String, default: Float) = get(key, default)
        override fun setString(key: String, value: String?) { map[key] = value }
        override fun setLong(key: String, value: Long?) { map[key] = value }
        override fun setInt(key: String, value: Int?) { map[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { map[key] = value }
        override fun setDouble(key: String, value: Double?) { map[key] = value }
        override fun setFloat(key: String, value: Float?) { map[key] = value }
    }

    private class NoPlatform : HistoryPlatform {
        override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
        override fun isOnReadingScreen() = true
        override fun leaveCurrentScreen() = Unit
    }

    /** A history item whose [window] is never read: only the text and revert matter to the host state. */
    private class Item(override val description: CharSequence, val reverted: MutableList<String>) : HistoryItem {
        override val window: Window get() = error("not used")
        override val createdAt = DbDate(1_000L)
        override fun revertTo() { reverted += description.toString() }
    }

    /** [DocumentControl] has nine collaborators and is only read by the document tabs; skip its constructor. */
    private fun <T : Any> allocate(cls: Class<T>): T {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        @Suppress("UNCHECKED_CAST")
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, cls) as T
    }

    private class Fixture(
        val settings: MapSettings = MapSettings(),
        hooks: ReadingHostHooks = ReadingHostHooks(),
        allocate: () -> DocumentControl,
    ) {
        val windowControl = WindowControl()
        val history = HistoryManager(windowControl, NoPlatform())
        val state = ReadingHostState(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            settings = settings,
            windowControl = windowControl,
            historyManager = history,
            documentControl = allocate(),
            hooks = hooks,
        )
    }

    private fun fixture(settings: MapSettings = MapSettings(), hooks: ReadingHostHooks = ReadingHostHooks()) =
        Fixture(settings, hooks) { allocate(DocumentControl::class.java) }

    @Test
    fun dismissedChooserAnswersEmptyOnce() {
        val state = fixture().state
        val first = state.openVerseChooserSheetForResult()
        assertTrue(state.quickSheet.value is ReadingQuickSheet.KeyChooser)
        assertFalse(first.isCompleted)

        state.closeQuickSheet()
        assertTrue(first.isCompleted)
        assertNull(first.getCompleted())

        val second = state.openVerseChooserSheetForResult()
        assertFalse(second.isCompleted)
        state.onGridPassageChosen("Gen.1.1")
        assertEquals("Gen.1.1", second.getCompleted())
        // The first answer is untouched by the second request, and closing again answers nothing new.
        assertNull(first.getCompleted())
        state.closeQuickSheet()
        assertEquals("Gen.1.1", second.getCompleted())
    }

    @Test
    fun supersededChooserIsAbandonedNotAnsweredByTheNextOne() {
        val state = fixture().state
        val first = state.openVerseChooserSheetForResult()
        state.showKeyChooserSheet(KeyChooserKind.Map, emptyList())
        assertTrue(first.isCompleted)
        assertNull(first.getCompleted())
    }

    @Test
    fun verseChosenWithoutARequestNavigates() {
        val applied = mutableListOf<String>()
        val state = fixture(hooks = ReadingHostHooks(applyChosenVerse = { applied += it })).state
        state.showQuickSheet(ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid))
        state.onGridPassageChosen("Gen.2.3")
        assertEquals(listOf("Gen.2.3"), applied)
        assertNull(state.quickSheet.value)
    }

    @Test
    fun shareOptionsRoundTripThroughSettings() {
        val settings = MapSettings()
        val first = fixture(settings).state
        assertEquals(true, first.shareVersesOptions.showVerseNumbers) // default
        assertEquals(false, first.shareVersesOptions.showQuotes) // default
        val changed = first.shareVersesOptions.copy(
            showVerseNumbers = false, showQuotes = true, showNotes = false, separateVersesWithNewlines = true,
        )
        first.updateShareVersesOptions(changed)
        assertEquals(changed, first.shareVersesOptions)

        val reopened = fixture(settings).state
        assertEquals<ShareVersesOptions>(changed, reopened.shareVersesOptions)
        assertEquals(false, settings.map["share_verse_numbers"])
        assertEquals(true, settings.map["share_show_quotes"])
        assertEquals(false, settings.map["show_notes"])
    }

    @Test
    fun historySheetListsOnlyTheActiveWindowsItems() {
        val reverted = mutableListOf<String>()
        val w1 = IdType()
        val w2 = IdType()
        val byWindow = mapOf<IdType, List<HistoryItem>>(
            w1 to listOf(Item("w1-a", reverted)),
            w2 to listOf(Item("w2-a", reverted), Item("w2-b", reverted)),
        )
        var active = w2
        val state = fixture(hooks = ReadingHostHooks(
            activeWindowId = { active },
            formatHistoryTimestamp = { "t" + it.getTime() },
            historyItemsFor = { id -> byWindow[id].orEmpty() },
        )).state
        assertEquals(listOf("w2-a", "w2-b"), state.historyEntriesForActiveWindow().map { it.title })
        assertEquals(listOf("t1000", "t1000"), state.historyEntriesForActiveWindow().map { it.timestamp })
        state.revertToHistoryItem(1)
        assertEquals(listOf("w2-b"), reverted)

        active = w1
        assertEquals(listOf("w1-a"), state.historyEntriesForActiveWindow().map { it.title })
        state.revertToHistoryItem(0)
        assertEquals(listOf("w2-b", "w1-a"), reverted)
    }

    @Test
    fun closeModalOverlaysClosesSheetButKeepsDialogAndDrawer() {
        val closed = mutableListOf<String>()
        val state = fixture(hooks = ReadingHostHooks(
            closeSpeakSheet = { closed += "speak" },
            closeTextSettingsEditor = { closed += "text" },
            dismissLlmSheets = { closed += "llm" },
        )).state
        state.showQuickSheet(ReadingQuickSheet.History)
        state.showHelp("t", "<b>h</b>")
        state.openDrawer()

        state.closeModalOverlays()

        assertNull(state.quickSheet.value)
        assertEquals(setOf("speak", "text", "llm"), closed.toSet())
        // A dialog over a sheet is fine, and the drawer is not a modal overlay: both survive.
        assertTrue(state.readingDialog.value is ReadingDialog.Help)
        assertTrue(state.isDrawerOpen)
    }

    @Test
    fun openingAQuickSheetAppliesTheExclusionRule() {
        val closed = mutableListOf<ReadingOverlay>()
        val state = fixture(hooks = ReadingHostHooks(
            closeSpeakSheet = { closed += ReadingOverlay.SpeakSheet },
            dismissLlmSheets = { closed += ReadingOverlay.Llm },
        )).state
        state.showHistorySheet()
        assertTrue(ReadingOverlay.SpeakSheet in closed && ReadingOverlay.Llm in closed)
    }

    @Test
    fun confirmRunsTheDeletionOnceAndDismissRunsNothing() {
        val state = fixture().state
        var runs = 0
        state.showDeleteDocumentPageConfirm { runs++ }
        state.confirmReadingDialog()
        state.confirmReadingDialog()
        assertEquals(1, runs)
        state.showDeleteDocumentPageConfirm { runs++ }
        state.dismissReadingDialog()
        state.confirmReadingDialog()
        assertEquals(1, runs)
    }

    @Test
    fun paneMenuOpensForAKnownWindowAndForcesButtonsVisible() {
        val state = fixture(hooks = ReadingHostHooks(
            paneMenuItemsFor = { id -> if (id == "w1") emptyList() else null },
        )).state
        val tick = state.windowButtonsVisibility.touchTick.value
        state.openPaneMenu("gone", net.bible.android.view.activity.page.screen.PaneMenuAnchor.Rail)
        assertNull(state.paneMenuWindowId.value)
        state.openPaneMenu("w1", net.bible.android.view.activity.page.screen.PaneMenuAnchor.Rail)
        assertEquals("w1", state.paneMenuWindowId.value)
        assertEquals(net.bible.android.view.activity.page.screen.PaneMenuAnchor.Rail, state.paneMenuAnchor.value)
        assertTrue(state.windowButtonsVisibility.touchTick.value > tick)
        state.closePaneMenu()
        assertNull(state.paneMenuWindowId.value)
    }

    @Test
    fun drawerToggleOpenClose() {
        val state = fixture().state
        state.toggleDrawer(); assertTrue(state.isDrawerOpen)
        state.toggleDrawer(); assertFalse(state.isDrawerOpen)
        state.openDrawer(); state.openDrawer(); assertTrue(state.isDrawerOpen)
        state.closeDrawer(); assertFalse(state.isDrawerOpen)
    }

    @Test
    fun quickDocTabRestoresByIdAndFallsBackToFirstVisible() {
        val settings = MapSettings()
        val state = fixture(settings).state
        val scope = net.bible.sharedcore.navigation.DocumentSheetScope.BIBLE
        val visible = listOf(
            net.bible.sharedcore.navigation.DocumentQuickTab.RECENT,
            net.bible.sharedcore.navigation.DocumentQuickTab.ALL,
        )
        assertEquals("RECENT", state.restoreQuickDocTab(scope, visible))
        state.persistQuickDocTab(scope, "ALL")
        assertEquals("ALL", state.restoreQuickDocTab(scope, visible))
        // The saved tab is hidden now: first visible wins, never an index.
        assertEquals("RECENT", state.restoreQuickDocTab(scope, visible.take(1)))
        assertSame(null, state.restoreQuickDocTab(scope, emptyList()))
    }

    @Test
    fun showRegenerateCarriesTheAskingWindowId() {
        val calls = mutableListOf<Pair<IdType, IdType>>()
        val state = fixture(hooks = ReadingHostHooks(openRegenerate = { page, window -> calls += page to window })).state
        val page = IdType()
        val asking = IdType()
        state.showRegenerate(page, asking)
        assertEquals(listOf(page to asking), calls)
    }

    @Test
    fun refreshPaneMenuItemsRebuildsOrReportsAMissingWindow() {
        var label = "Pin"
        val state = fixture(hooks = ReadingHostHooks(
            paneMenuItemsFor = { id ->
                if (id == "gone") null
                else listOf(net.bible.sharedcore.window.WindowPaneMenuItem(id = "pin", label = label))
            },
        )).state
        assertTrue(state.refreshPaneMenuItems("w1"))
        assertEquals(listOf("Pin"), state.paneMenuItems.value.map { it.label })

        label = "Unpin"
        assertTrue(state.refreshPaneMenuItems("w1"))
        assertEquals(listOf("Unpin"), state.paneMenuItems.value.map { it.label })

        // A window closed under an open menu leaves the items as they were.
        assertFalse(state.refreshPaneMenuItems("gone"))
        assertEquals(listOf("Unpin"), state.paneMenuItems.value.map { it.label })
    }
}
