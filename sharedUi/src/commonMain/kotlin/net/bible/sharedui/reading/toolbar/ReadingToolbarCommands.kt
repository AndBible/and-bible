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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.toolbar

import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.reading.QuickDocAction
import net.bible.sharedcore.reading.QuickDocPicker
import net.bible.sharedcore.reading.QuickDocRow
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedui.reading.ReadingToolbarCallbacks

/**
 * What the reading toolbar's commands need from the platform: the sheets and routes they open, the documents they
 * switch between, the Strong's setting, the speak transport bar, workspaces and the toolbar state. Documents travel as
 * [QuickDocRow]s (the `Book.initials` is the id), so the decision logic in [ReadingToolbarCommands] is testable
 * without jsword. The iOS implementation is `IosReadingToolbarHost`.
 */
@kotlin.native.HiddenFromObjC
interface ReadingToolbarHost {
    // --- sheets and screens ---
    /** Android's `toggleDrawer`: the home button closes an open drawer. */
    fun toggleDrawer()
    fun showDocumentSheet()
    fun showWorkspaceSheet()
    fun showScopedDocumentSheet(scope: DocumentSheetScope)
    fun showSpeakSettings()
    fun showTextSettingEditor(scope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit)
    fun openSearch()
    fun openChooseDocument(type: String?)
    fun startKeyChooser()
    fun openOverflowMenu()

    // --- documents ---
    /** The `toolbar_button_actions` setting ("default", "swap-menu", "swap-..."). */
    val toolbarButtonSetting: String?
    fun currentDocumentInitials(): String?
    fun biblesForVerse(): List<QuickDocRow>
    fun commentariesForVerse(): List<QuickDocRow>
    fun generalBooksAndDictionaries(): List<QuickDocRow>
    fun suggestedBibleInitials(): String?
    fun suggestedCommentaryInitials(): String?

    /** Shows the document in the active window and remembers it as its category's default. */
    fun switchDocument(initials: String)

    // --- Strong's ---
    /** The active window's effective Strong's mode (0 hidden, 1 links, 2 text and links). */
    fun strongsMode(): Int
    fun setStrongsMode(mode: Int)

    /** The window-level settings scope of the active window (the Strong's editor sheet edits it). */
    fun activeWindowScope(): SettingsScope?

    // --- speak ---
    fun transportBarVisible(): Boolean
    fun setTransportBarVisible(visible: Boolean)
    fun speakStopped(): Boolean

    // --- workspaces and toolbar ---
    fun cycleWorkspace(forward: Boolean)

    /** Rebuilds the toolbar state from the active window now (Android's `onToolbarStateMayHaveChanged`). */
    fun refreshToolbar()
}

/**
 * The reading toolbar's callbacks, as Android's `ReadingCommands.compose*` and `ComposeReadingViewHost` do them.
 * Pure decision logic over [ReadingToolbarHost].
 */
@kotlin.native.HiddenFromObjC
class ReadingToolbarCommands(private val host: ReadingToolbarHost) {
    private val swap get() = host.toolbarButtonSetting?.startsWith("swap-") == true
    private val swapMenu get() = host.toolbarButtonSetting == "swap-menu"

    fun callbacks() = ReadingToolbarCallbacks(
        onHome = host::toggleDrawer,
        onTitleTap = host::startKeyChooser,
        onTitleLongPress = host::showDocumentSheet,
        onTitleFlingVertical = host::showWorkspaceSheet,
        onTitleFlingHorizontal = host::cycleWorkspace,
        onBible = ::bibleClick,
        onBibleLong = ::bibleLongClick,
        onCommentary = ::commentaryClick,
        onCommentaryLong = ::commentaryLongClick,
        onStrongs = ::cycleStrongs,
        onStrongsLong = ::strongsLong,
        onSearch = host::openSearch,
        onSpeak = ::toggleSpeak,
        onSpeakLong = host::showSpeakSettings,
        onWorkspace = host::showWorkspaceSheet,
        onOverflow = host::openOverflowMenu,
    )

    /** Bible button, short press: swap modes switch to the suggested Bible, else the scoped document sheet. */
    fun bibleClick() {
        if (swap) switchToSuggested(host.suggestedBibleInitials())
        else openScopedDocumentSheet(DocumentSheetScope.BIBLE, host.biblesForVerse())
    }

    fun bibleLongClick() {
        if (swapMenu) openScopedDocumentSheet(DocumentSheetScope.BIBLE, host.biblesForVerse())
        else host.openChooseDocument(DocumentSheetScope.BIBLE.chooserType)
    }

    /** Commentary button, short press; the picker also lists general books and dictionaries (classic parity). */
    fun commentaryClick() {
        if (swap) {
            switchToSuggested(host.suggestedCommentaryInitials())
        } else {
            openScopedDocumentSheet(
                DocumentSheetScope.COMMENTARY,
                host.commentariesForVerse() + host.generalBooksAndDictionaries(),
            )
        }
    }

    /** Commentary long press: unlike [commentaryClick] the 2-document check does NOT include general books and dictionaries. */
    fun commentaryLongClick() {
        if (swapMenu) openScopedDocumentSheet(DocumentSheetScope.COMMENTARY, host.commentariesForVerse())
        else host.openChooseDocument(DocumentSheetScope.COMMENTARY.chooserType)
    }

    private fun switchToSuggested(initials: String?) {
        if (initials != null) switchDocument(initials)
    }

    private fun switchDocument(initials: String) {
        host.switchDocument(initials)
        host.refreshToolbar()
    }

    /**
     * Exactly two candidates switch straight to the one that is not shown; none opens the full chooser typed to the
     * [scope]; otherwise the scoped sheet (see [QuickDocPicker]).
     */
    fun openScopedDocumentSheet(scope: DocumentSheetScope, rows: List<QuickDocRow>) {
        when (val action = QuickDocPicker.action(rows, host.currentDocumentInitials() ?: "")) {
            is QuickDocAction.None -> host.openChooseDocument(scope.chooserType)
            is QuickDocAction.SwitchDirectly -> switchDocument(action.id)
            is QuickDocAction.ShowPicker -> host.showScopedDocumentSheet(scope)
        }
    }

    /** Strong's button: hidden, links, text and links, round again. */
    fun cycleStrongs() {
        host.setStrongsMode((host.strongsMode() + 1) % 3)
        host.refreshToolbar()
    }

    /** Strong's long press: the in-place editor sheet of the setting at the active window's level. */
    fun strongsLong() {
        val scope = host.activeWindowScope() ?: return
        val page = textSettingEditorPageFor("STRONGS") ?: return
        host.showTextSettingEditor(scope, page) { host.refreshToolbar() }
    }

    /** Speak button: shows the transport bar; a second press hides it, but only when speech is stopped. */
    fun toggleSpeak() {
        if (host.transportBarVisible()) {
            if (host.speakStopped()) host.setTransportBarVisible(false)
        } else {
            host.setTransportBarVisible(true)
        }
    }
}
