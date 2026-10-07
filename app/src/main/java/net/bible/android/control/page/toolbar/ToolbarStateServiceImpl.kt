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
package net.bible.android.control.page.toolbar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.passage.PassageChangedEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.window.WorkspaceChange
import net.bible.android.control.page.window.WorkspaceChanges
import net.bible.android.control.page.PageControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.speak.SpeakControl
import net.bible.service.cloudsync.CloudSync
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedui.deriveToolbarFromTheme
import org.crosswire.jsword.book.BookCategory
import org.crosswire.jsword.passage.Verse

/**
 * Android impl of [ToolbarStateService]. Bridges the classic window/passage/speak/cloud-sync
 * [ABEventBus] events into a reactive [ToolbarState] snapshot for the Compose reading toolbar
 * (Batch 12b), mirroring [net.bible.android.control.speak.SpeakSettingsServiceImpl]'s
 * self-registering-Koin-singleton pattern: registers its handlers in `init` via
 * [ABEventBus.register] + [onMain] (not `ABEventBus.register(this)` + `fun onEvent(e: X)` —
 * that greenrobot-style idiom does not exist on this repo's KMP [ABEventBus], whose real
 * subscription surface is the `Subscriptions { onMain<X> { ... } }` DSL). `onMain` keeps every
 * mutation of [_toolbar] on the main looper even though some source events (e.g. [SpeakEvent]
 * from the TTS engine, [CurrentVerseChangedEvent] from the WebView JS bridge thread) may be
 * posted off it.
 *
 * [CurrentWindowChangedEvent]/[PassageChangedEvent]/[CurrentVerseChangedEvent]/[SpeakEvent] each
 * rebuild the full snapshot from the active window's current page; [CloudSync.runningChanged] only flips
 * [ToolbarState.syncRunning], preserving every other field (a sync can run concurrently with the
 * reading view, so it must not clobber title/document/capability state computed from the page).
 *
 * Registered as a Koin single (lives for the process) — no matching `unregister`, same as
 * [net.bible.android.control.speak.SpeakSettingsServiceImpl].
 */
class ToolbarStateServiceImpl(
    private val windowControl: WindowControl,
    private val documentControl: DocumentControl,
    private val pageControl: PageControl,
    private val speakControl: SpeakControl,
) : ToolbarStateService {
    private val _toolbar = MutableStateFlow(ToolbarState.EMPTY)
    override val toolbar: StateFlow<ToolbarState> = _toolbar.asStateFlow()

    init {
        ABEventBus.register(this) {
            onMain<CurrentWindowChangedEvent> { refresh() }
            onMain<PassageChangedEvent> { refresh() }
            onMain<CurrentVerseChangedEvent> { refresh() }
            onMain<SpeakEvent> { refresh() }
        }
        // Process lifetime: never cancelled. A/B batch 4a F1: the workspace colour feeds
        // ToolbarState.workspaceColorArgb, and no other trigger fires when it is written.
        WorkspaceChanges.changes.subscribeOnMain { if (it == WorkspaceChange.ColorEdited) refresh() }
        // Process lifetime: never cancelled.
        CloudSync.runningChanged.subscribeOnMain { running -> _toolbar.value = _toolbar.value.copy(syncRunning = running) }
    }

    /**
     * Rebuilds the snapshot from the active window's current page, preserving [ToolbarState.syncRunning].
     *
     * No-op while the workspace is being (re)loaded. `WindowRepository.loadFromDb` restores each
     * window's page, and that posts `CurrentBibleVerseChanged` — which lands here synchronously,
     * mid-load, when the repository has no active window yet. Reading
     * `windowControl.activeWindowPageManager` at that moment used to re-enter `loadFromDb` through
     * the lazy `activeWindow` getter (see `WindowRepository.loadingFromDb` for the full failure).
     * Before the load ends, `setDefaultActiveWindow()` assigns `activeWindow`, whose setter posts
     * `CurrentWindowChangedEvent` and refreshes us again — NOT `notifyWindowsChanged()`, which posts
     * `NumberOfWindowsChangedEvent`, an event this service does not subscribe to — so nothing is lost
     * by skipping the mid-load refresh.
     */
    override fun refresh() {
        if (!windowControl.windowRepository.initialized) return
        _toolbar.value = buildSnapshot().copy(syncRunning = _toolbar.value.syncRunning)
    }

    private fun buildSnapshot(): ToolbarState {
        val page = windowControl.activeWindowPageManager.currentPage
        val setting = CommonUtils.settings.getString("toolbar_button_actions", "default")
        val swap = setting?.startsWith("swap-") == true
        val showBible = if (swap) documentControl.suggestedBible != null else documentControl.biblesForVerse.isNotEmpty()
        val showCommentary = if (swap) documentControl.suggestedCommentary != null else documentControl.commentariesForVerse.isNotEmpty()
        return ToolbarState(
            pageTitle = pageTitleText(),
            documentTitle = page.currentDocumentName,
            syncRunning = false, // overwritten by refresh()'s copy(); CloudSync.runningChanged sets it directly
            showBible = showBible,
            showCommentary = showCommentary,
            showStrongs = documentControl.isStrongsInBook,
            // Same value `MainBibleActivity.dummyStrongsPrefOption.value` reads (StrongsPreference
            // over a WINDOW-level SettingsBundle): the active window's page-manager setting merged
            // down through workspace/global/default via `TextDisplaySettings.actual` — 0/1/2.
            strongsMode = windowControl.activeWindowPageManager.actualTextDisplaySettings.strongsMode ?: 0,
            searchable = page.isSearchable,
            speakable = page.isSpeakable,
            speakStopped = speakControl.isStopped,
            // A/B batch 3 F3. Read fresh on every snapshot: buildSnapshot() re-runs on
            // CurrentWindowChangedEvent (workspace switch) and on every
            // HostedStateRefresher.refresh() (e.g. returning from colour settings), so the toolbar
            // picks a new colour up with no extra subscription.
            workspaceColorArgb = windowControl.windowRepository.workspaceSettings.workspaceColor,
            // A/B batch 4b feedback (2026-08-01): the rule itself is
            // net.bible.sharedui.deriveToolbarFromTheme, a pure function unit-tested in
            // WorkspaceThemeSeedTest (this class needs Koin + a Context + four collaborators, which
            // is why the rule lives outside it). Re-read fresh on every snapshot for the same reason
            // as workspaceColorArgb above.
            deriveToolbarFromTheme = deriveToolbarFromTheme(CommonUtils.settings.enabledExperimentalFeatures),
        )
    }

    /** Port of `MainBibleActivity.pageTitleText` (MainBibleActivity.kt:1180). */
    private fun pageTitleText(): String {
        val page = pageControl.currentPageManager.currentPage
        val doc = page.currentDocument
        var key = page.displayKey
        if (doc?.bookCategory == BookCategory.BIBLE) key = pageControl.currentBibleVerse
        return if (key is Verse && key.verse == 0) CommonUtils.getWholeChapter(key, false).name
        else key?.name ?: ""
    }
}
