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
 * The window-inset ledger lifted out of [MainBibleActivity] -- reading-host re-typing Task R2 (design
 * spec §3.3, `2026-09-16-compose-reading-host-retyping`). `MainBibleActivity` still owns the window
 * and keeps the `setOnApplyWindowInsetsListener` *registration* itself; this collaborator owns the
 * offsets the listener feeds and the one predicate three call sites must agree on -- see
 * `ImePaddingPredicateDriftTest`, which scans this file and [MainBibleActivity] together for exactly
 * that agreement.
 *
 * `BibleView`'s three reads (`imeHeight`, `topOffset2`, `bottomOffsetForWebView`) go through
 * `mainBibleActivity.readingInsets` directly; `MainBibleActivity` keeps thin delegating members of the
 * same names for `ComposeReadingViewHost` and the Robolectric net (`ReadingSearchEntryPointsTest`),
 * which call them on the Activity and are not edited in this batch.
 */
class ReadingInsets(private val activity: MainBibleActivity) {

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
        (if (activity.transportBarVisible) activity.transportBarHeight else 0) +
        (if (activity.agentLogVisible) activity.agentLogHeight else 0)

    // WebView bottom offset: navigation bar + transport + buttons + agent log + search sheet.
    // Same three cases as above: padding applied -> 0; no keyboard -> system bars; keyboard up with
    // the padding suppressed -> system bars, because the keyboard overlays and nothing should move.
    // `bottomOffset1WithoutIme` equals `bottomOffset1` whenever the keyboard is hidden, so the two
    // pre-existing cases are byte-identical.
    val bottomOffsetForWebView get() =
        (if (imePaddingApplied) 0 else bottomOffset1WithoutIme) +
            (if (activity.transportBarVisible) activity.transportBarHeight else 0) +
            (if (activity.restoreButtonsVisible) activity.windowButtonHeight else 0) +
            (if (activity.agentLogVisible) activity.agentLogHeight else 0) +
            (if (searchSheetVisible) searchSheetHeight else 0)

    // IME keyboard height in pixels (0 when keyboard hidden)
    val imeHeight get() = bottomOffset1 - bottomOffset1WithoutIme

    /**
     * Whether the Compose reading-view search field currently holds focus. Delegates to
     * [MainBibleActivity.composeSearchFieldFocused], kept as its own property here (rather than
     * inlining `activity.composeSearchFieldFocused` into [imePaddingApplied]) so the predicate's
     * source text still contains the literal `!composeSearchFieldFocused` `ImePaddingPredicateDriftTest`
     * scans for.
     */
    private val composeSearchFieldFocused: Boolean get() = activity.composeSearchFieldFocused

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
     * [MainBibleActivity.SearchSheetOffsetsUpdated] (the same "recompute and push to the WebView" idiom
     * as `MainBibleActivity.AgentLogOffsetsUpdated`) so [net.bible.android.view.activity.page.BibleView.updateOffsets]
     * picks up the new value; a no-op when nothing actually changed, so a benign recomposition doesn't
     * spam `set_offsets` calls.
     */
    fun updateSearchSheetOffsets(visible: Boolean, heightPx: Int) {
        if (searchSheetVisible == visible && searchSheetHeight == heightPx) return
        searchSheetVisible = visible
        searchSheetHeight = heightPx
        ABEventBus.post(MainBibleActivity.SearchSheetOffsetsUpdated())
    }

    /**
     * Applies (or removes) the IME-height padding on the Compose/WebView container.
     *
     * Reads live state, so it needs no snapshot of the insets: the listener's original condition
     * `imeInsets.bottom > systemBarInsets.bottom` is algebraically identical to `imeHeight > 0`
     * (`imeHeight` is `maxOf(sb, ime) - sb`), and both offsets it derives from are fields
     * [onWindowInsetsApplied] keeps up to date.
     */
    private fun applyImePadding() {
        activity.binding.mainBibleView.setPadding(0, 0, 0, if (imePaddingApplied) bottomOffset1 else 0)
    }

    /**
     * The Compose search field gained or lost focus, which changes [imePaddingApplied] without
     * changing any inset — so the insets listener never fires and both the padding and the WebView's
     * offsets would go stale. Same "recompute and push to the WebView" idiom as
     * [updateSearchSheetOffsets] and the agent log's.
     */
    fun onComposeSearchFieldFocusChanged() {
        applyImePadding()
        ABEventBus.post(MainBibleActivity.ImePaddingChanged())
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
