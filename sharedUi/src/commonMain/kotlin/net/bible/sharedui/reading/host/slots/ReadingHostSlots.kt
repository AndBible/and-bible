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

package net.bible.sharedui.reading.host.slots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import net.bible.android.view.activity.navigation.GridPassageControllerFactory
import net.bible.android.view.activity.page.screen.speakBarVisible
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.reading.speakBarOwnsTopEdge
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakSheetStack
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.strings.Strings

/**
 * The reading view's host-built slots (the `@Composable` lambdas `ReadingViewScreen` takes), in
 * common code. A platform host builds one instance and hands `{ slots.X() }` lambdas to the screen,
 * so Android and iOS share the slot bodies. Every collaborator is a constructor parameter or lambda
 * (no Koin, no platform types); [strings] and [disableAnimations] are lambdas so a late change
 * (locale, the animations preference) is read on each composition. [fullScreen] is read inside
 * composition, so passing a `State`'s getter makes the speak bar recompose when it flips.
 *
 * The text-settings editor takes the platform's [imagePicker] (background import) and
 * [thumbnailFor] (provided-background thumbnails), and a [scope] for the colour controller's work.
 *
 * The Speak sheet takes [speakSettingsService], the [speakPlatform] port (system TTS settings) and
 * the [gridPassages] factory for its verse picker; the host builds the factory once and also uses it
 * for the key-chooser grid.
 *
 * The quick sheets (History, Workspaces, Documents, Share, ReadHistory) take [workspaceService] and
 * [readingProgressService] (providers, resolved when a sheet opens) for their rows, [quickActions] and [navigator] for what a pick does,
 * [textShare], [clipboard] and [toast] for Share and Copy, and [workspaceName],
 * [activeWindowPosition] and [currentDocumentInitials] for the History title and the current-document
 * mark. The key-chooser sheets (Map, GeneralBook, Grid) read the page through [keyChooserPage] and
 * build the grid with [gridPassages].
 *
 * The search sheet and the search settings read [searchState].
 *
 * The agent log's glyph comes from [LocalAgentStatusIcon]; `AgentLogController` is built by the
 * host (its raw-log action starts a platform activity).
 */
@kotlin.native.HiddenFromObjC
class ReadingHostSlots(
    val hostState: ReadingHostState,
    val strings: () -> Strings,
    val externalLinks: ExternalLinks,
    val agentLog: AgentLogController,
    val speakTransport: SpeakTransportController,
    val disableAnimations: () -> Boolean,
    val fullScreen: () -> Boolean,
    val searchState: ReadingSearchState,
    val textDisplaySettingsService: () -> TextDisplaySettingsService,
    val scope: CoroutineScope,
    val imagePicker: ImagePicker,
    val thumbnailFor: (String) -> ImageBitmap?,
    val speakSettingsService: SpeakSettingsService,
    val speakPlatform: SpeakSettingsPlatform,
    val gridPassages: GridPassageControllerFactory,
    val workspaceService: () -> WorkspaceService,
    val readingProgressService: () -> ReadingProgressService,
    val quickActions: ReadingQuickActions,
    val keyChooserPage: ReadingKeyChooserPage,
    val navigator: ReadingNavigator,
    val textShare: TextShare,
    val clipboard: ClipboardText,
    val toast: (String) -> Unit,
    val workspaceName: () -> String,
    val activeWindowPosition: () -> Int,
    val currentDocumentInitials: () -> String?,
) {
    private val textSettings = TextSettingsEditorState(
        hostState, textDisplaySettingsService, scope, strings, imagePicker, thumbnailFor,
    )

    private val speak = SpeakSettingsState(
        hostState, speakSettingsService, strings, externalLinks, speakPlatform, gridPassages,
    )

    /** The Speak sheet's page stack (test-only reads of which page is open). */
    val speakSheet: SpeakSheetStack get() = speak.sheet

    /** The Speak help dialog's HTML while it is open (test-only reads). */
    val speakHelp: MutableState<String?> get() = speak.help

    internal val speakSettingsState: SpeakSettingsState get() = speak

    /** Opens the Speak settings sheet on its Settings page, closing the other modal overlays first. */
    fun showSpeakSettings() = speak.show()

    /** Sets the Speak help dialog's content (title plus the tutorial-video link). */
    fun showSpeakHelp() = speak.showHelp()

    /** Sets the Advanced Speak help dialog's content (auto-bookmarking and playback-settings explanations). */
    fun showAdvancedSpeakHelp() = speak.showAdvancedHelp()

    /** The Speak settings sheet; renders nothing while its page stack is empty. */
    @Composable
    fun SpeakSettings() = speak.Content()

    /** The in-place text-settings editor's page stack (independent of the settings screen's stack). */
    val textSettingsEditor: SettingsEditorStack get() = textSettings.editor

    /**
     * Opens the in-place text-settings editor at [page] for [settingsScope]; [onReady] runs after each
     * change lands. Closes the other modal overlays first.
     */
    fun showTextSettingEditor(settingsScope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit) =
        textSettings.show(settingsScope, page, onReady)

    /** The cached text-settings controller for [settingsScope] (one per visited scope). */
    fun textSettingsControllerFor(settingsScope: SettingsScope): TextDisplaySettingsController =
        textSettings.controllerFor(settingsScope)

    /** A fresh, never-cached colour controller for [settingsScope]. */
    fun colorControllerFor(settingsScope: SettingsScope): ColorSettingsController =
        textSettings.colorControllerFor(settingsScope)

    /** The text-settings editor sheet; renders nothing while its page stack is empty. */
    @Composable
    fun TextSettingsEditor() = textSettings.Content()

    /** The reading view's own dialogs (help text, delete-page confirmation). */
    @Composable
    fun ReadingDialog() = ReadingDialogContent(hostState, strings(), externalLinks)

    /** The agent-log panel; same parameters as `ReadingViewScreen`'s `agentLogSlot`. */
    @Composable
    fun AgentLog(
        applyNavBarInset: Boolean,
        maxHeightDp: Float,
        collapsedDp: Float,
        onCollapsedHeightMeasured: (Float) -> Unit,
    ) = AgentLogSlotContent(
        agentLog, disableAnimations(), applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured,
    )

    /**
     * The modal search-settings sheet. The live target document decides the EPUB word-mode row vs
     * the Bible form; closing re-runs a search in flight.
     */
    @Composable
    fun SearchSettings() = SearchSettingsSlotContent(
        searchState, searchState::searchSettingsForEpub, searchState::currentBookName,
        searchState.controller::settingsClosed,
    )

    /** The search sheet: the index prompt/progress panel or the results, per search phase. */
    @Composable
    fun SearchSheet() = SearchSheetSlotContent(searchState, strings())

    /**
     * The quick sheet [sheet]: History, Workspaces, Documents, Share, ReadHistory or a key chooser
     * (Map, GeneralBook, Grid). The one place a quick sheet is mounted.
     */
    @Composable
    fun QuickSheet(sheet: ReadingQuickSheet) = QuickSheetContent(this, sheet)

    /** The Speak transport bar, hidden in full screen and while the transport is not visible. */
    @Composable
    fun SpeakBar(applyNavBarInset: Boolean) {
        val speakState by speakTransport.state.collectAsState()
        // The agent panel's visibility, narrowed to a boolean through `derivedStateOf`: collecting
        // `agentLog.state` raw here would resubscribe this slot to every log line, status change and
        // cost update of a running agent, when all it needs is the one flip.
        val agentLogState = agentLog.state.collectAsState()
        val agentLogVisible = remember { derivedStateOf { agentLogState.value.visible } }
        if (speakBarVisible(fullScreen = fullScreen(), transportVisible = speakState.visible)) {
            SpeakTransportBar(
                speakState,
                onPlayPause = { speakTransport.togglePlayPause() },
                onStop = { speakTransport.stop() },
                onRewind = { speakTransport.rewind() },
                onForward = { speakTransport.forward() },
                onPrev = { speakTransport.prevVerse() },
                onNext = { speakTransport.nextVerse() },
                onBookmark = { speakTransport.onBookmarkButton() },
                onConfig = { speakTransport.onConfig() },
                applyNavBarInset = applyNavBarInset,
                ownsTopEdge = speakBarOwnsTopEdge(agentLogVisible.value),
            )
        }
    }
}
