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

package net.bible.android.view.activity.page

import androidx.core.graphics.Insets
import net.bible.android.control.event.ABEventBus

/**
 * What [ReadingInsets] needs from whichever Activity is hosting the reading view: the seven pieces
 * of classic-toolbar chrome state its two offset getters read, and the sink that APPLIES the IME
 * padding it computes.
 *
 * **Why a bundle of suppliers rather than members on [ReadingHostActivity]** (reading-host
 * re-typing Ruling A, addendum `2026-09-17`). The interface is deliberately narrow — seven members
 * for chrome the host WINDOW owns — and none of these seven is that. They are the classic toolbar's
 * own furniture: the speak transport bar, the agent log, the window restore buttons, and the
 * Compose search field's focus. A host that has none of them should not have to declare seven
 * members it answers with constants, and the R6 measurement is the precedent: widening the
 * interface to carry a collaborator's surface is what made R6 undeliverable.
 *
 * **Why the padding sink is injected** (addendum Ruling C). The ledger's arithmetic is
 * host-agnostic and stays here; only the *application* of it is host-shaped.
 * `MainBibleActivity` supplies the `binding.mainBibleView.setPadding(0, 0, 0, px)` call it has
 * always made. A Compose host supplies a documented no-op, because it applies its own IME insets at
 * the content (`WindowInsets.ime`) rather than by padding a ViewGroup — see
 * [applyImeBottomPadding].
 *
 * **What `NavHostComposeActivity` will honestly supply** when Task 8 composes the `reading`
 * destination — honestly, meaning each answer is TRUE of that host, not a stub that hides a missing
 * feature (Ruling D):
 *
 * | member | nav host's answer | why it is honest |
 * |---|---|---|
 * | [transportBarVisible] / [transportBarHeight] | `false` / `0` | the speak transport bar is a Compose bar below the panes, so the WebView already ends above it; `bottomOffsetForWebView` therefore drops both it and the nav-bar term while it is up |
 * | [agentLogVisible] / [agentLogHeight] | `false` / `0` | likewise — the agent log strip is `MainBibleActivity`'s `binding`, mirrored into the ledger by an event |
 * | [restoreButtonsVisible] / [windowButtonHeight] | the same workspace setting / the same theme dimension | both are host-independent facts (`windowRepository.workspaceSettings`, an `R.attr` on the theme); the nav host can read both, and once it draws restore buttons the answer stays correct without a change here |
 * | [composeSearchFieldFocused] | its own reading-view host's `searchFieldFocused` | the Compose search field is the *reading view's*, not the Activity's — `MainBibleActivity` already answers it by asking `composeReadingViewHost?.searchFieldFocused`, so the nav host answers it the same way |
 * | [applyImeBottomPadding] | a documented no-op | Ruling C |
 *
 * **Every member is read at CALL time, never captured.** All seven reads are mutable state that
 * changes while the reading view is up (the transport bar appears when speak starts, the agent log
 * is pushed in by an event, the search field's focus flips on every tap), and the ledger is
 * recomputed on every inset change. A captured value would freeze the offsets the WebView is told
 * about. This mirrors [BibleViewHostCallbacks]' same rule one rung down the chain.
 */
class ReadingInsetsHostCallbacks(
    /** `true` while the speak transport bar occupies the bottom of the classic toolbar. */
    val transportBarVisible: () -> Boolean,

    /** The transport bar's height in px, resolved from the theme's `R.attr.transportBarHeight`. */
    val transportBarHeight: () -> Int,

    /** `true` while the agent log strip is showing (pushed in by `AgentLogOffsetsUpdated`). */
    val agentLogVisible: () -> Boolean,

    /** The agent log strip's measured height in px. */
    val agentLogHeight: () -> Int,

    /** `workspaceSettings.restoreButtonsVisible` — the minimised-window restore row. */
    val restoreButtonsVisible: () -> Boolean,

    /** A restore button's height in px, from the theme's `R.attr.windowButtonHeight`. */
    val windowButtonHeight: () -> Int,

    /**
     * Whether the Compose reading-view search field currently holds focus — the one input to
     * [ReadingInsets]' IME-padding predicate that is not an inset. See that predicate's kdoc for
     * why it is keyed on FOCUS and not on search mode.
     */
    val composeSearchFieldFocused: () -> Boolean,

    /**
     * Apply `bottomPaddingPx` of bottom padding to whatever view the host lifts above the keyboard,
     * or remove it when the value is `0`. Ruling C's sink: `MainBibleActivity` passes it to
     * `binding.mainBibleView.setPadding(0, 0, 0, …)`; a Compose host passes it nowhere, because
     * `WindowInsets.ime` at the content does the same job without a ViewGroup to pad.
     *
     * The ledger decides the VALUE (see [ReadingInsets.applyImePadding]); the host decides only
     * where it lands. No arithmetic happens on this side of the boundary.
     */
    val applyImeBottomPadding: (bottomPaddingPx: Int) -> Unit,
)

/**
 * The window-inset ledger lifted out of [MainBibleActivity] -- reading-host re-typing Task R2 (design
 * spec §3.3, `2026-09-16-compose-reading-host-retyping`), and taken off that Activity's TYPE by R6b
 * (addendum Ruling C): it now reaches its host through [ReadingInsetsHostCallbacks], so a second
 * reading host can own one. The Activity that owns the window still keeps the
 * `setOnApplyWindowInsetsListener` *registration* itself and forwards into [onWindowInsetsApplied];
 * this collaborator owns the offsets that listener feeds and the one predicate three call sites must
 * agree on -- see `ImePaddingPredicateDriftTest`, which scans this file and [MainBibleActivity]
 * together for exactly that agreement.
 *
 * `BibleView`'s three reads (`imeHeight`, `topOffset2`, `bottomOffsetForWebView`) arrive as R6a's
 * three `BibleViewHostCallbacks` suppliers, built over this ledger; `MainBibleActivity` keeps thin
 * delegating members of the same names for `ComposeReadingViewHost` and the Robolectric net
 * (`ReadingSearchEntryPointsTest`), which call them on the Activity and are not edited in this batch.
 */
class ReadingInsets(private val host: ReadingInsetsHostCallbacks) {

    // Top offset with only statusbar and toolbar
    val topOffset2 = 0

    // Offsets with system insets only - will be updated by setupEdgeToEdge()
    private var bottomOffset1 = 0
    private var bottomOffset1WithoutIme = 0  // Always excludes IME (keyboard) height

    // F6 Task 8b Step 3: the Compose reading-view search sheet's (visible, measured-height-in-px)
    // pair, fed by ComposeReadingViewHost.install() — see updateSearchSheetOffsets. Mirrors
    // MainBibleActivity's agentLogVisible/agentLogHeight (Compose-only; always false/0 before the
    // host is installed).
    private var searchSheetVisible = false
    private var searchSheetHeight = 0

    // Bottom offset with navigation bar, transport bar and agent log.
    // The term is dropped when the mainBibleView padding is handling the keyboard, and is the
    // IME-FREE offset otherwise — `bottomOffset1` includes the keyboard height while it is up, so
    // using it here would reserve the keyboard's space twice (see `imePaddingApplied`).
    val bottomOffset2 get() = (if (imePaddingApplied) 0 else bottomOffset1WithoutIme) +
        (if (host.transportBarVisible()) host.transportBarHeight() else 0) +
        (if (host.agentLogVisible()) host.agentLogHeight() else 0)

    // WebView bottom offset: navigation bar (unless transport bar up) + buttons + agent log + search sheet.
    // Same three cases as above: padding applied -> 0; no keyboard -> system bars; keyboard up with
    // the padding suppressed -> system bars, because the keyboard overlays and nothing should move.
    // `bottomOffset1WithoutIme` equals `bottomOffset1` whenever the keyboard is hidden, so the two
    // pre-existing cases are byte-identical.
    // Fix batch 5 F111: the speak bar sits in the column BELOW the panes and pads the nav bar itself,
    // so while it is up the WebView already ends above both -- neither may be counted again.
    val bottomOffsetForWebView get() =
        (if (host.transportBarVisible()) 0 else (if (imePaddingApplied) 0 else bottomOffset1WithoutIme)) +
            (if (host.restoreButtonsVisible()) host.windowButtonHeight() else 0) +
            (if (host.agentLogVisible()) host.agentLogHeight() else 0) +
            (if (searchSheetVisible) searchSheetHeight else 0)

    // IME keyboard height in pixels (0 when keyboard hidden)
    val imeHeight get() = bottomOffset1 - bottomOffset1WithoutIme

    /**
     * Whether the Compose reading-view search field currently holds focus. Delegates to
     * [ReadingInsetsHostCallbacks.composeSearchFieldFocused] (on `MainBibleActivity`, that is still
     * its own `composeSearchFieldFocused`), kept as its own property here -- rather than inlining
     * the supplier call into [imePaddingApplied] -- so the predicate's source text still contains
     * the literal `!composeSearchFieldFocused` `ImePaddingPredicateDriftTest` scans for.
     */
    private val composeSearchFieldFocused: Boolean get() = host.composeSearchFieldFocused()

    /**
     * Whether the IME-height padding on `binding.mainBibleView` is in effect.
     *
     * The Compose search field lives in the TOOLBAR, at the top of the very container this padding
     * shrinks — so while THAT field owns the keyboard the padding buys nothing and costs the whole
     * layout: it shrinks the Compose tree, which flipped `SplitContent`'s orientation from stacked to
     * side-by-side mid-typing (A/B F6-B1).
     *
     * Keyed on the field's FOCUS, never on search mode being active. Search mode outlives the results
     * sheet by design (tap a result, Back closes the sheet, the toolbar stays in search mode until a
     * second Back), so a WebView note editor can be opened while search mode is still on — and that
     * editor must still be lifted above the keyboard, which is the whole reason this padding exists.
     *
     * [bottomOffset2] and [bottomOffsetForWebView] MUST read this same predicate: both zero their
     * navigation-bar term on the grounds that the padding is covering it.
     */
    private val imePaddingApplied: Boolean get() = imeHeight > 0 && !composeSearchFieldFocused

    /**
     * F6 Task 8b Step 3: [net.bible.android.view.activity.page.screen.ComposeReadingViewHost.install]'s
     * report of the search sheet's live (visible, measured-height-in-px) state — the fourth term in
     * [bottomOffsetForWebView], mirroring `MainBibleActivity`'s agentLogVisible/agentLogHeight. Posts
     * [SearchSheetOffsetsUpdated] (the same "recompute and push to the WebView" idiom
     * as `AgentLogOffsetsUpdated`) so [net.bible.android.view.activity.page.BibleView.updateOffsets]
     * picks up the new value; a no-op when nothing actually changed, so a benign recomposition doesn't
     * spam `set_offsets` calls.
     */
    fun updateSearchSheetOffsets(visible: Boolean, heightPx: Int) {
        if (searchSheetVisible == visible && searchSheetHeight == heightPx) return
        searchSheetVisible = visible
        searchSheetHeight = heightPx
        ABEventBus.post(SearchSheetOffsetsUpdated())
    }

    /**
     * Applies (or removes) the IME-height padding on the Compose/WebView container.
     *
     * Reads live state, so it needs no snapshot of the insets: the listener's original condition
     * `imeInsets.bottom > systemBarInsets.bottom` is algebraically identical to `imeHeight > 0`
     * (`imeHeight` is `maxOf(sb, ime) - sb`), and both offsets it derives from are fields
     * [onWindowInsetsApplied] keeps up to date.
     *
     * R6b (Ruling C) split this in two along the only host-shaped seam: the VALUE is decided here,
     * with the same expression the `setPadding` call has always carried, and
     * [ReadingInsetsHostCallbacks.applyImeBottomPadding] is where it lands. `MainBibleActivity`'s
     * sink still calls `binding.mainBibleView.setPadding(0, 0, 0, …)`; nothing about when or how
     * often this runs changed.
     */
    private fun applyImePadding() {
        host.applyImeBottomPadding(if (imePaddingApplied) bottomOffset1 else 0)
    }

    /**
     * The Compose search field gained or lost focus, which changes [imePaddingApplied] without
     * changing any inset — so the insets listener never fires and both the padding and the WebView's
     * offsets would go stale. Same "recompute and push to the WebView" idiom as
     * [updateSearchSheetOffsets] and the agent log's.
     */
    fun onComposeSearchFieldFocusChanged() {
        applyImePadding()
        ABEventBus.post(ImePaddingChanged())
    }

    /**
     * Called from `MainBibleActivity`'s `setOnApplyWindowInsetsListener` registration on every window
     * inset change. The Activity keeps the listener registration itself (it owns the window) and
     * forwards here; this is the body that used to live inside that listener, moved verbatim.
     */
    fun onWindowInsetsApplied(systemBarInsets: Insets, imeInsets: Insets) {
        // Store base system bar offsets (without IME)
        bottomOffset1WithoutIme = systemBarInsets.bottom  // Always system bars only, never includes IME

        // bottomOffset1 includes IME when keyboard is visible (for Android UI positioning)
        if (imeInsets.bottom > 0) {
            // Keyboard is visible - adjust the bottom offset to account for it
            bottomOffset1 = maxOf(systemBarInsets.bottom, imeInsets.bottom)
        } else {
            bottomOffset1 = systemBarInsets.bottom
        }

        // Resize the WebView area when the keyboard is visible, to fix position:fixed drift.
        // This restores the pre-Android 15 ADJUST_RESIZE behaviour manually.
        applyImePadding()
    }
}
