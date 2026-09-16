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

import android.content.Context
import androidx.lifecycle.LifecycleOwner

/**
 * What the reading view needs from whichever Activity is hosting it — `MainBibleActivity` today,
 * `NavHostComposeActivity` once the `reading` destination composes it (nav-graph slice 7 Task 8).
 *
 * **It is an interface and not a base class on purpose.** `MainBibleActivity` extends
 * `CustomTitlebarActivityBase` while `NavHostComposeActivity` extends `ActivityBase`, and
 * `CustomTitlebarActivityBase` is also the parent of `StartupActivity`, `ProgressActivityBase` and
 * `AbstractSpeakActivity` — a shared abstract class would drag three unrelated Activities into the
 * reading view's contract.
 *
 * It is deliberately NARROW. The 27-member command surface lives on `ReadingCommands` and the inset
 * ledger on `ReadingInsets` (design spec §3.1/§3.2); `binding` is never part of this contract — the
 * classic `toolbarLayout` an Activity happens to own is not something the reading view may reach
 * through. Everything here is chrome the HOST WINDOW owns and no collaborator can: system bars,
 * fullscreen, the drawer, and the Context/`getString` pair that the reading view's ~20 bare
 * value-passes need (spec §2.1).
 */
interface ReadingHostActivity : LifecycleOwner {
    /** For the 11 bare value-passes that only ever needed a Context (spec §2.1). */
    val hostContext: Context

    /**
     * The process-wide fullscreen bit (`SharedActivityState.instance`), not a per-Activity one:
     * `MainBibleActivity.toggleFullScreen` has always delegated to it, and the reading view's own
     * `FullScreenEvent` subscribers assume every host agrees about it.
     */
    var fullScreen: Boolean

    /** Classic `DrawerListener`'s `STATE_SETTLING`/`STATE_DRAGGING` → `showSystemUI(false)`. */
    fun showSystemUiTransient()

    /** Classic `DrawerListener`'s `STATE_IDLE` at slide offset 0 — fullscreen decides which. */
    fun applyIdleSystemUi()

    /** Classic `onDrawerClosed`'s conditional focus hand-back to the active pane's `BibleView`. */
    fun restorePaneFocus()

    /** Opens the navigation drawer if it is closed, closes it if it is open. */
    fun toggleDrawer()

    /**
     * Declared so `activity.getString(...)` keeps its spelling when the reading view is re-typed
     * onto this interface (~20 call sites). Both implementors satisfy it through the inherited
     * `ContextWrapper.getString`, so neither writes a body for it.
     */
    fun getString(resId: Int): String
}
