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
package net.bible.android.view.activity.page.screen

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedui.progress.ReadHistoryRow

/**
 * Reload-generation counter for [ComposeReadingViewHost]: bumped by `rebuild()` to force a full
 * dispose+recreate of the pane subtree. Needed because each pane is
 * `key(window.id) { AndroidView(factory = { ... }) }` — the factory runs once per window id and
 * is never re-invoked by a plain recomposition. When [MainBibleActivity.currentWorkspaceId]'s
 * setter (or a database restore, `DatabaseContainer.databaseRestored`) reloads the SAME workspace, it does
 * `documentViewManager.removeView()` -> `bibleViewFactory.clear()` (destroys every cached
 * [net.bible.android.view.activity.page.BibleView]) -> `windowRepository.loadFromDb()` ->
 * `documentViewManager.buildView(forceUpdate = true)`; the window ids are unchanged, so without
 * this generation bump Compose would keep the existing `key(window.id)` nodes and never re-run
 * `AndroidView`'s factory, leaving the panes showing destroyed WebViews. Mirrors classic's
 * `SplitBibleArea.update(forceUpdate = true)` recreate.
 *
 * Kept as its own tiny, framework-free (no [KoinComponent]/activity coupling) holder so the
 * "`rebuild()` bumps the counter" behavior is unit-testable without booting a full
 * [MainBibleActivity] or Koin context — see `ComposeReadingViewGenerationTest`.
 */
class ComposeReadingViewGeneration {
    private val mutableState = mutableIntStateOf(0)
    val state: State<Int> get() = mutableState
    fun rebuild() { mutableState.intValue++ }
}

/**
 * Makes the rail's plain (non-observable) per-window labels recompose when they may have changed.
 *
 * `windowLabelFor`/`windowTopLabelFor` read `pageManager` directly, so Compose has nothing to
 * observe and skips the rail while its inputs (layout model + stable lambdas) are unchanged.
 * Routing each label read through [observed] subscribes the calling composition scope to one
 * shared tick; [invalidate] (driven by the host's passage/verse events) bumps it, so exactly the
 * label sites that read it recompose, for every window, synchronised ones included. The window
 * icon (`windowIconFor`) is not wrapped: it is read in the same rail composition scope as the
 * labels, so it is re-evaluated whenever they are.
 *
 * The bump relies on the host's `PageChange.VerseChanged` / `PageChange.BibleVerseChanged` handlers
 * being reached: a document or key change reaches the rail only because `CurrentPageManager` /
 * `PassageChangeMediator` emit one of them. A mutation that emits neither leaves the labels stale;
 * `railLabelsFollowARealDocumentChange` pins the document-swap path.
 *
 * Framework-free like [ComposeReadingViewGeneration], so it is unit-testable without a composition.
 */
class WindowLabelFreshness {
    private val tick = mutableIntStateOf(0)

    /** Reads [read] after subscribing the current composition scope to [invalidate]. */
    fun <T> observed(read: () -> T): T {
        tick.intValue
        return read()
    }

    fun invalidate() { tick.intValue++ }
}

/**
 * Fans a "classic said this may have changed" signal out to the Compose reading view's state
 * (pre-A/B state-freshness spec §1 P3).
 *
 * Every Compose state bridge subscribes to a *guessed set* of owner `Events`
 * streams and rebuilds its snapshot from the domain, so a mutation that posts none of them leaves
 * the UI stale — `ToolbarStateServiceImpl` subscribes to 5 events while classic calls
 * `MainBibleActivity.updateActions()` from 12 places, only 3 of which coincide with one. Rather
 * than guess more events, the classic imperative refresh points push here, mirroring what Batch
 * Z-early A6 already does one line earlier in `updateActions` for the drawer
 * (`composeReadingViewHost?.rebuildDrawer(...)`). The domain-notifier inversion that removes the
 * guessing altogether is Batch Z-late work (spec §5), after the classic files are deleted.
 *
 * [rebuildComposition] additionally bumps [ComposeReadingViewGeneration], which recreates the pane
 * subtree — needed only where the host re-reads values *inside* its composition (the three
 * settings read in `mountComposeView`), i.e. the return-from-Settings path. It is off by default
 * because recreating the subtree re-runs every pane's `AndroidView` factory.
 *
 * Kept as its own framework-free holder (like [ComposeReadingViewGeneration] above) so the fan-out
 * is unit-testable without a [MainBibleActivity] or Koin context — see `HostedStateRefresherTest`.
 */
class HostedStateRefresher(
    private val toolbar: ToolbarStateService,
    private val generation: ComposeReadingViewGeneration,
) {
    fun refresh(rebuildComposition: Boolean = false) {
        toolbar.refresh()
        if (rebuildComposition) generation.rebuild()
    }
}

/**
 * Whether the reading view's Speak transport bar should be shown — the Compose counterpart of
 * classic `MainBibleActivity.updateBottomBars()`'s `if (isFullScreen || !transportBarVisible)`
 * animate-out branch.
 *
 * [transportVisible] arrives through `SpeakTransportServiceImpl.setTransportVisible`, which the
 * `transportBarVisible` **setter** calls with the raw backing field — its getter's
 * `if (isFullScreen) false` mask is NOT applied before the call, and `toggleFullScreen()` emits
 * only `fullScreenChanged`. So without re-applying the fullscreen half here, the bar stayed on
 * screen in fullscreen on the Compose path while classic animated it away (pre-A/B spec §1 P3).
 *
 * A pure function so it is unit-testable — see `SpeakBarVisibilityTest`.
 */
fun speakBarVisible(fullScreen: Boolean, transportVisible: Boolean): Boolean =
    !fullScreen && transportVisible

/**
 * Shadow elevation of the (mounted, hidden) search sheet. The hidden sheet's shadow spills onto the
 * reader's bottom row on paper-white greyscale, so both greyscale-base modes (black-and-white e-ink
 * and pure monochrome) draw none; Normal and Color e-ink keep the Material default.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun searchSheetShadowElevation(mode: DisplayColorMode): Dp =
    if (mode.isGreyBase) 0.dp else BottomSheetDefaults.Elevation

/**
 * The `menuWindowId`/`paneMenuWindowId` a surface should receive: the open window's id only when
 * the open menu is anchored ([openAnchor]) to THAT [surface], else `null` — the pane overlay and the
 * rail (A/B batch 3, F5b) each call this with their own [surface] so exactly one of the two
 * `WindowPaneMenu` instances ever reports itself expanded, keeping "both menus open at once"
 * structurally unrepresentable rather than merely avoided. A pure function, mirroring
 * [speakBarVisible] above, so the gate itself — not just the `openPaneMenu` bookkeeping that feeds
 * it — is unit-testable without a `ComposeTestRule` (this repo's `:app` unit tests have none); see
 * `MenuWindowIdForTest`.
 */
fun menuWindowIdFor(surface: PaneMenuAnchor, openAnchor: PaneMenuAnchor, openWindowId: String?): String? =
    if (openAnchor == surface) openWindowId else null

/**
 * Task 27 (platform-dialog removal, run 3) fix round 1: [ReadHistoryEntry] → [ReadHistoryRow]
 * mapping for the reading view's per-chapter read-history sheet
 * (`QuickSheetContent`'s `is ReadingQuickSheet.ReadHistory ->` branch), extracted as a pure function —
 * mirroring [speakBarVisible]/[menuWindowIdFor] above — so it is unit-testable without
 * Robolectric/Compose; see `ChapterReadHistoryRowTest`. Classic `ReadHistoryDialog.showForChapter`
 * used `showChapterPerRow = false`: `"$date $time"` / version, no chapter reference (the chapter is
 * already named in the sheet's title, unlike the book/day history screens' multi-chapter list,
 * which show a chapter reference per row instead). [date]/[time] arrive ALREADY formatted —
 * `ReadingProgressServiceImpl.formatEntryDate`/`formatEntryTime` are Android `DateFormat` calls and
 * so not themselves portable — this function only assembles the row from them.
 */
fun chapterReadHistoryRow(
    entry: ReadHistoryEntry,
    date: String,
    time: String,
    versionUnknownText: String,
): ReadHistoryRow {
    val version = entry.bookInitials.ifEmpty { versionUnknownText }
    return ReadHistoryRow(id = entry.id, primary = "$date $time", secondary = version)
}

/**
 * Task 27 (platform-dialog removal, run 3) fix round 2: the `onApplyDeletes` callback
 * `QuickSheetContent`'s `is ReadingQuickSheet.ReadHistory ->` branch wires into `AbReadHistorySheet`,
 * extracted as its own function — mirroring [chapterReadHistoryRow] above — so it is unit-testable
 * with a fake [ReadingProgressService] instead of only being exercisable by re-implementing its
 * `ids.isNotEmpty()` guard inside a test harness (review round 1's gap, named again in round 2: the
 * guard AND the `cycle` threading from the `LaunchedEffect`'s load were both unverified against the
 * real production lambda). `ComposeReadingViewHost` now calls this function VERBATIM — see
 * `ReadHistoryApplyDeletesTest`.
 *
 * [service] is typed to the portable [ReadingProgressService] interface, not
 * `ReadingProgressServiceImpl`, specifically so it is fakeable without Room/Robolectric — the same
 * reason `:sharedCore`'s own `ReadingProgressControllerTest.Fake` exists. [cycle] is captured once,
 * at assembly time (the `LaunchedEffect`'s `activeCycle`), not re-read per call — matching classic
 * `ReadHistoryDialog.applyPendingDeletes`, which closed over the SAME `cycle` its `loadAndShow` had
 * already fetched, not a re-queried one.
 */
fun readHistoryApplyDeletes(
    coroutineScope: CoroutineScope,
    service: ReadingProgressService,
    cycle: Int,
): (List<String>) -> Unit = { ids ->
    if (ids.isNotEmpty()) {
        coroutineScope.launch { service.deleteReadHistoryEntries(ids, cycle) }
    }
}

/**
 * Auto-hide state for the pane overlay's floating ☰ button (Batch 12b follow-on Plan B Task 5) —
 * the Compose port of classic `SplitBibleArea.resetTouchTimer`/`toggleWindowButtonVisibility`
 * (`screen/SplitBibleArea.kt:511-575`), hoisted to a host-owned field (design spec §9: "hoist it
 * ... not per-recomposition local state") rather than remembered inside the composable, so the 2s
 * hide countdown survives recomposition. Kept as its own tiny, framework-free holder — mirroring
 * [ComposeReadingViewGeneration] — so `ComposeReadingViewHostTest` can assert it without booting a
 * full [MainBibleActivity]/Koin context.
 *
 * [visible] starts `true` (buttons shown on first render, mirroring classic's initial
 * `buttonsVisible = true`, `SplitBibleArea.kt:149`). [onTouch] — wired to
 * [ComposeReadingViewHost.onBibleViewTouched], and also called by
 * [ComposeReadingViewHost.openPaneMenu] (mirroring classic `showPopupMenu`'s
 * `timerTask?.cancel(); toggleWindowButtonVisibility(true)`, `SplitBibleArea.kt:731-732`, so a menu
 * opened while auto-hidden still renders) — sets [visible] `true` and bumps [touchTick] so a
 * `LaunchedEffect(touchTick)` in the composable can restart its 2s hide countdown. [onHideTimeout]
 * (called by that effect after the delay elapses) sets [visible] `false`. Because Compose
 * cancels/reruns a `LaunchedEffect` whenever its key changes, a touch mid-countdown discards the
 * stale timer automatically — no manual `TimerTask.cancel()` bookkeeping is needed here, unlike
 * classic.
 */
class WindowButtonsVisibility {
    private val mutableVisible = mutableStateOf(true)
    private val mutableTouchTick = mutableIntStateOf(0)
    val visible: State<Boolean> get() = mutableVisible
    val touchTick: State<Int> get() = mutableTouchTick

    fun onTouch() {
        mutableVisible.value = true
        mutableTouchTick.intValue++
    }

    fun onHideTimeout() { mutableVisible.value = false }
}

/**
 * Which surface a per-window (☰) pane menu is anchored to (A/B batch 3, F5). Classic anchors its
 * popup to the view that was pressed; the port originally composed the menu only inside the pane
 * overlay, so a rail long-press rendered it at the pane's floating button instead of at the rail
 * tab under the finger.
 */
enum class PaneMenuAnchor { Pane, Rail }

/**
 * Platform-dialog removal Task 18: the reading view's own dialogs, held as ONE host-owned state
 * (`ComposeReadingViewHost.readingDialog`) and rendered through ONE host-private slot
 * (`ReadingDialogSlot`) -- the same "shape 2" pattern `quickSheet`/`QuickSheetContent` already use
 * (Appendix -- site owners, Q1), rather than the raw-`StateFlow`-plus-many-callbacks shape
 * `readingLlmDialogs` needs (nothing outside this host observes either of these).
 *
 * **Not a [net.bible.sharedcore.reading.ReadingOverlay]**: both arms are plain `AlertDialog`-shaped
 * (via `AbMessageDialog`/`AbConfirmDialog`), and a dialog over a sheet is fine
 * (`ReadingOverlayExclusion`'s kdoc) -- so opening one never needs to close a sheet, and no
 * exclusion wiring/enum member is needed.
 */
sealed interface ReadingDialog {
    /** `BibleJavascriptInterface.helpDialog`/`.helpBookmarks`'s plain-text or HTML help body -- a
     *  single OK button, no negative action. */
    data class Help(val title: String?, val html: String) : ReadingDialog

    /** `BibleJavascriptInterface.deleteMyDocumentPage`'s confirm; [onConfirm] is the caller's
     *  deletion (+ window bookkeeping), captured at open time -- only the QUESTION lives here. */
    data class ConfirmDeleteDocumentPage(val onConfirm: () -> Unit) : ReadingDialog
}
