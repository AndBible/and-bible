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
package net.bible.android.view.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.service.history.HistoryPlatform
import net.bible.sharedcore.ai.reading.ReadingLlmDialogState
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.host.ReadingHostDeps
import net.bible.sharedui.reading.host.ReadingHostHooks
import net.bible.sharedui.reading.host.ReadingHostState

/**
 * Builds the common reading host's deps for the host-mount tests (`ComposeReadingViewHost
 * .mountComposeView`), which only prove that a wiring mounts and that its state objects stay
 * caller-owned. Nothing here is composed: composition needs a real window.
 *
 * The state is real but inert: collaborators it reaches only on a user action are a fresh
 * [WindowControl], a platform-less [HistoryManager] and a [DocumentControl] whose constructor is
 * skipped (no mocking framework is used in this module).
 */
internal object ReadingHostTestDeps {
    private class InertSettings : AppSettings {
        override fun getString(key: String, default: String?) = default
        override fun getLong(key: String, default: Long) = default
        override fun getInt(key: String, default: Int) = default
        override fun getBoolean(key: String, default: Boolean) = default
        override fun getDouble(key: String, default: Double) = default
        override fun getFloat(key: String, default: Float) = default
        override fun setString(key: String, value: String?) = Unit
        override fun setLong(key: String, value: Long?) = Unit
        override fun setInt(key: String, value: Int?) = Unit
        override fun setBoolean(key: String, value: Boolean?) = Unit
        override fun setDouble(key: String, value: Double?) = Unit
        override fun setFloat(key: String, value: Float?) = Unit
    }

    private object NoHistoryPlatform : HistoryPlatform {
        override fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem? = null
        override fun isOnReadingScreen() = true
        override fun leaveCurrentScreen() = Unit
    }

    private fun allocateDocumentControl(): DocumentControl {
        val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, DocumentControl::class.java) as DocumentControl
    }

    fun state(hooks: ReadingHostHooks = ReadingHostHooks()): ReadingHostState {
        val windowControl = WindowControl()
        return ReadingHostState(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            settings = InertSettings(),
            windowControl = windowControl,
            historyManager = HistoryManager(windowControl, NoHistoryPlatform),
            documentControl = allocateDocumentControl(),
            hooks = hooks,
        )
    }

    private val noopToolbarCallbacks = ReadingToolbarCallbacks(
        onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
        onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
        onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
        onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
    )

    /** A controller over [windowState]'s layout and [commands]. */
    fun controller(windowState: WindowStateService, commands: WindowCommands) =
        ReadingViewController(windowState, commands)

    /**
     * Deps with every required member inert, and the optional members a host test wires passed through.
     * (A test needing one this does not name adds it here: [ReadingHostDeps] has no `copy`.)
     */
    fun deps(
        state: ReadingHostState,
        controller: ReadingViewController,
        nightMode: State<Boolean> = mutableStateOf(false),
        fullScreen: State<Boolean> = mutableStateOf(false),
        llmDialog: StateFlow<ReadingLlmDialogState> = MutableStateFlow(ReadingLlmDialogState()).asStateFlow(),
        onLlmPromptChosen: (String) -> Unit = {},
        onLlmToggleFavorite: (String) -> Unit = {},
        onLlmCategoryExpandedChanged: (String?, Boolean) -> Unit = { _, _ -> },
        onLlmSpecifySubmitted: (String) -> Unit = {},
        onLlmModelChosen: (String, Boolean) -> Unit = { _, _ -> },
        onLlmRegenerateConfirmed: (String?, Boolean, Boolean) -> Unit = { _, _, _ -> },
        onLlmDismiss: () -> Unit = {},
        agentLogSlot: (@Composable (Boolean, Float, Float, (Float) -> Unit) -> Unit)? = null,
        searchBar: StateFlow<ReadingSearchBarState?> = MutableStateFlow<ReadingSearchBarState?>(null).asStateFlow(),
        searchBarCallbacks: ReadingSearchBarCallbacks? = null,
        searchSheetVisible: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow(),
        onSearchSheetDismissed: () -> Unit = {},
        searchSheetSlot: @Composable () -> Unit = {},
        searchSettingsSlot: @Composable () -> Unit = {},
    ): ReadingHostDeps = ReadingHostDeps(
        state = state,
        controller = controller,
        toolbarCallbacks = noopToolbarCallbacks,
        toolbarIcons = {
            val icon = ColorPainter(Color.Gray)
            ReadingToolbarIcons(icon, icon, icon, icon, icon, icon, icon, icon, icon)
        },
        nightMode = nightMode,
        fullScreen = fullScreen,
        llmDialog = llmDialog,
        onLlmPromptChosen = onLlmPromptChosen,
        onLlmToggleFavorite = onLlmToggleFavorite,
        onLlmCategoryExpandedChanged = onLlmCategoryExpandedChanged,
        onLlmSpecifySubmitted = onLlmSpecifySubmitted,
        onLlmModelChosen = onLlmModelChosen,
        onLlmRegenerateConfirmed = onLlmRegenerateConfirmed,
        onLlmDismiss = onLlmDismiss,
        agentLogSlot = agentLogSlot,
        searchBar = searchBar,
        searchBarCallbacks = searchBarCallbacks,
        searchSheetVisible = searchSheetVisible,
        onSearchSheetDismissed = onSearchSheetDismissed,
        searchSheetSlot = searchSheetSlot,
        searchSettingsSlot = searchSettingsSlot,
    )
}
