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
package net.bible.sharedui.reading.toolbar

import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.reading.QuickDocRow
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The decisions behind the reading toolbar's sixteen callbacks, over a recording [ReadingToolbarHost]. */
class ReadingToolbarCommandsTest {
    private class FakeHost : ReadingToolbarHost {
        val calls = mutableListOf<String>()
        override var toolbarButtonSetting: String? = "default"
        var current: String? = "KJV"
        var bibles = emptyList<QuickDocRow>()
        var commentaries = emptyList<QuickDocRow>()
        var others = emptyList<QuickDocRow>()
        var suggestedBible: String? = null
        var suggestedCommentary: String? = null
        var strongs = 0
        var scope: SettingsScope? = SettingsScope.Window("w1", "ws1")
        var transportVisible = false
        var stopped = true
        var editorOnReady: (() -> Unit)? = null

        override fun openDrawer() { calls += "drawer" }
        override fun showDocumentSheet() { calls += "documentSheet" }
        override fun showWorkspaceSheet() { calls += "workspaceSheet" }
        override fun showScopedDocumentSheet(scope: DocumentSheetScope) { calls += "scopedSheet:$scope" }
        override fun showSpeakSettings() { calls += "speakSettings" }
        override fun showTextSettingEditor(scope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit) {
            calls += "editor:$scope:$page"
            editorOnReady = onReady
        }
        override fun openSearch() { calls += "search" }
        override fun openChooseDocument(type: String?) { calls += "chooser:$type" }
        override fun startKeyChooser() { calls += "keyChooser" }
        override fun openOverflowMenu() { calls += "overflow" }
        override fun currentDocumentInitials() = current
        override fun biblesForVerse() = bibles
        override fun commentariesForVerse() = commentaries
        override fun generalBooksAndDictionaries() = others
        override fun suggestedBibleInitials() = suggestedBible
        override fun suggestedCommentaryInitials() = suggestedCommentary
        override fun switchDocument(initials: String) { calls += "switch:$initials" }
        override fun strongsMode() = strongs
        override fun setStrongsMode(mode: Int) { strongs = mode; calls += "strongs:$mode" }
        override fun activeWindowScope() = scope
        override fun transportBarVisible() = transportVisible
        override fun setTransportBarVisible(visible: Boolean) { transportVisible = visible; calls += "transport:$visible" }
        override fun speakStopped() = stopped
        override fun cycleWorkspace(forward: Boolean) { calls += "cycle:$forward" }
        override fun refreshToolbar() { calls += "refresh" }
    }

    private val host = FakeHost()
    private val commands = ReadingToolbarCommands(host)
    private val callbacks = commands.callbacks()

    private fun row(id: String, lang: String = "en", abbr: String = id) = QuickDocRow(id, lang, abbr)

    @Test fun simpleCallbacksReachTheirSheetsAndScreens() {
        callbacks.onHome(); callbacks.onTitleTap(); callbacks.onTitleLongPress(); callbacks.onTitleFlingVertical()
        callbacks.onTitleFlingHorizontal(true); callbacks.onTitleFlingHorizontal(false); callbacks.onSearch()
        callbacks.onSpeakLong(); callbacks.onWorkspace(); callbacks.onOverflow()
        assertEquals(
            listOf(
                "drawer", "keyChooser", "documentSheet", "workspaceSheet", "cycle:true", "cycle:false", "search",
                "speakSettings", "workspaceSheet", "overflow",
            ),
            host.calls,
        )
    }

    @Test fun bibleButtonWithTwoBiblesSwitchesToTheOneNotShownAndRefreshesTheToolbar() {
        host.bibles = listOf(row("KJV"), row("ESV"))
        callbacks.onBible()
        assertEquals(listOf("switch:ESV", "refresh"), host.calls)
    }

    @Test fun bibleButtonWithThreeBiblesOpensTheScopedSheetAndWithNoneTheTypedChooser() {
        host.bibles = listOf(row("KJV"), row("ESV"), row("NIV"))
        callbacks.onBible()
        assertEquals(listOf("scopedSheet:BIBLE"), host.calls)
        host.calls.clear(); host.bibles = emptyList()
        callbacks.onBible()
        assertEquals(listOf("chooser:BIBLE"), host.calls)
    }

    @Test fun swapModesSwitchToTheSuggestedDocumentAndDoNothingWithoutOne() {
        host.toolbarButtonSetting = "swap-menu"
        host.suggestedBible = "ESV"; host.suggestedCommentary = null
        callbacks.onBible(); callbacks.onCommentary()
        assertEquals(listOf("switch:ESV", "refresh"), host.calls)
    }

    @Test fun swapMenuLongPressShowsThePickerAndOtherSettingsOpenTheChooser() {
        host.bibles = listOf(row("KJV"), row("ESV"), row("NIV"))
        host.toolbarButtonSetting = "swap-menu"
        callbacks.onBibleLong()
        assertEquals(listOf("scopedSheet:BIBLE"), host.calls)
        host.calls.clear(); host.toolbarButtonSetting = "swap-fast"
        callbacks.onBibleLong(); callbacks.onCommentaryLong()
        assertEquals(listOf("chooser:BIBLE", "chooser:COMMENTARY"), host.calls)
    }

    @Test fun commentaryShortPressCountsGeneralBooksAndDictionariesButLongPressDoesNot() {
        host.commentaries = listOf(row("MHC"))
        host.others = listOf(row("ISBE"), row("Josephus"))
        callbacks.onCommentary() // 3 candidates -> the picker
        assertEquals(listOf("scopedSheet:COMMENTARY"), host.calls)
        host.calls.clear(); host.toolbarButtonSetting = "swap-menu"
        callbacks.onCommentaryLong() // 1 candidate -> the picker too, but only counting commentaries
        assertEquals(listOf("scopedSheet:COMMENTARY"), host.calls)
        host.calls.clear(); host.commentaries = listOf(row("MHC"), row("JFB")); host.current = "MHC"
        callbacks.onCommentaryLong() // exactly two commentaries -> direct switch, general books ignored
        assertEquals(listOf("switch:JFB", "refresh"), host.calls)
    }

    @Test fun strongsCyclesThroughThreeModesAndWrapsAndRefreshes() {
        host.strongs = 2
        callbacks.onStrongs()
        assertEquals(0, host.strongs)
        callbacks.onStrongs(); callbacks.onStrongs()
        assertEquals(2, host.strongs)
        assertEquals(listOf("strongs:0", "refresh", "strongs:1", "refresh", "strongs:2", "refresh"), host.calls)
    }

    @Test fun strongsLongPressOpensTheEditorAtTheWindowScopeAndRefreshesWhenReady() {
        callbacks.onStrongsLong()
        assertEquals(listOf("editor:${SettingsScope.Window("w1", "ws1")}:${SettingsEditorPage.Row("STRONGS")}"), host.calls)
        host.editorOnReady!!.invoke()
        assertEquals("refresh", host.calls.last())
    }

    @Test fun strongsLongPressWithoutAWindowDoesNothing() {
        host.scope = null
        callbacks.onStrongsLong()
        assertTrue(host.calls.isEmpty())
        assertNull(host.editorOnReady)
    }

    @Test fun speakShowsTheBarThenHidesItOnlyWhenSpeechIsStopped() {
        callbacks.onSpeak()
        assertTrue(host.transportVisible)
        host.stopped = false
        callbacks.onSpeak() // speaking: the bar stays
        assertTrue(host.transportVisible)
        host.stopped = true
        callbacks.onSpeak()
        assertEquals(false, host.transportVisible)
        assertEquals(listOf("transport:true", "transport:false"), host.calls)
    }
}
