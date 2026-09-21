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
import androidx.compose.runtime.State
import androidx.lifecycle.LifecycleOwner
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.ActivityBase

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
 *
 * **R6d widened it, once, from seven members to twelve, and that is the whole widening the batch
 * allows.** Ruling A held it at seven through R6a--R6c2 because nothing but `MainBibleActivity`
 * could honestly satisfy a wider one; R6a--R6c2 are precisely what made a second host able to. The
 * five additions are [readingCommands], [readingInsets], [hostActivity], [hostWindowRepository]
 * and [hideClassicToolbarRow], and each is here because `ComposeReadingViewHost` reaches it and no
 * collaborator can supply it:
 *
 * | Added | Covers | What `NavHostComposeActivity` supplies |
 * |---|---|---|
 * | [readingCommands] | 33 of the host's 72 references, across 29 members | its own [ReadingCommands], built lazily over its own callback bundle |
 * | [readingInsets] | 2 references | its own [ReadingInsets], per Ruling C (the padding sink is the documented no-op) |
 * | [hostActivity] | 4 references (`startActivity`, `startActivityForResult`) | `this` — both hosts really ARE an `ActivityBase` |
 * | [hostWindowRepository] | the 7 `windowControl.windowRepository` sites R6c1's identity finding condemns | `readingAppBootstrap.windowRepository`, its own |
 * | [hideClassicToolbarRow] | 2 references (`binding.toolbarLayout`/`.toolbarDivider`) | nothing — a default no-op it does not override, because it has no classic toolbar row |
 *
 * Note what is NOT here. `binding` itself never enters this contract (spec §3.1) — the two
 * references to it became [hideClassicToolbarRow], an INTENTION the host may answer however its own
 * window is built. `drawerRateVisible` is not here either: its body is a pure `BuildVariant`
 * expression, host-independent, so R6d moved it to `DrawerMenuStateBuilder` rather than asking an
 * Activity for a value reachable without one (R1's rule, pinned by `ReadingHostDelegationGuardTest`).
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

    /**
     * The keyboard shrink this host wants applied to the reading content, in px, as observable state
     * (F59).
     *
     * `MainBibleActivity` answers with a permanent `0` — honestly, not as a stub: it applies the same
     * value to `binding.mainBibleView` itself (`ReadingInsetsHostCallbacks.applyImeBottomPadding`), so
     * the Compose tree inside that padded ViewGroup must add nothing on top. The nav host has no such
     * ViewGroup and answers with the live value.
     */
    val imeBottomPaddingPx: State<Int>

    /**
     * The SDK level at and above which THIS host owns the IME inset itself -- `ADJUST_NOTHING` plus
     * [imeBottomPaddingPx]'s sink -- rather than leaving it to the framework's window resize (F59 fix
     * round 1).
     *
     * **Per-host, not a single global threshold.** `ReadingAppBootstrap.setSoftKeyboardMode()` is the
     * ONE method shared by both hosts, and the T9 walk's API 30+ narrowing (spec §3.1.1) is a nav-host
     * finding: it measured that `NavHostComposeActivity`'s Compose tree can see `WindowInsets.ime`
     * once `ADJUST_NOTHING` is set from API 30, and its own insets listener + sink exist to feed it.
     * `MainBibleActivity` was never measured and has neither of those -- its compensating listener
     * (`MainBibleActivity.kt` ~:677) and `ActivityBase`'s inset setup (~:130/:141) both stay gated at
     * `>= VANILLA_ICE_CREAM` (35), unchanged by this batch. Moving `setSoftKeyboardMode()`'s gate to a
     * single `>= R` constant would have put `MainBibleActivity`'s window into `ADJUST_NOTHING` on API
     * 30-34 with nothing on either side compensating -- neither the framework's resize (suppressed)
     * nor this host's own padding (it only ever answers a permanent 0, per [imeBottomPaddingPx]'s
     * kdoc) -- silently hiding the reading content behind the keyboard on real 30-34 devices. Classic
     * is not launched in production today, but the regression must not be introduced.
     *
     * `MainBibleActivity` answers `Build.VERSION_CODES.VANILLA_ICE_CREAM` (today's threshold,
     * unchanged); `NavHostComposeActivity` answers `Build.VERSION_CODES.R` and reads this SAME member
     * for its own `onCreate`'s `ADJUST_NOTHING` call and its insets-listener gate, so the three sites
     * (bootstrap, onCreate's soft-input call, onCreate's listener registration) cannot drift apart.
     */
    val appOwnsImeInsetFromSdk: Int

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

    /**
     * The host as a plain Android Activity.
     *
     * **Not a cast in disguise, and not a widening of what the reading view may reach.** Both
     * reading hosts really ARE an `ActivityBase` (`MainBibleActivity : CustomTitlebarActivityBase`,
     * which is an `ActivityBase`; `NavHostComposeActivity` extends `ActivityBase` directly), so
     * both answer it with `this` and nothing can fail. What it exposes is the plain ANDROID
     * Activity API that four of `ComposeReadingViewHost`'s call sites demand BY SIGNATURE and that
     * no host-shaped interface should pretend to hide: `startActivity` twice (the raw-log route and
     * the system TTS settings) and `startActivityForResult` twice (ChooseDocument and the workspace
     * selector, whose results come back to the host's own `onActivityResult`). Wrapping four
     * `Intent`-shaped lambdas around them would decouple nothing — the callee still needs an
     * Activity. R6c2's [ReadingCommandsHostCallbacks.hostActivity] carries the same member for the
     * same reason, and R7 set the precedent when it gave `ReadingAppBootstrap` a `ComponentActivity`
     * rather than this interface.
     *
     * Pure `Context` passes do NOT come through here — they use [hostContext], which R4 declared
     * for exactly that.
     */
    val hostActivity: ActivityBase

    /**
     * THIS host's own [WindowRepository] — **not** `WindowControl`'s.
     *
     * `windowControl.windowRepository` is whichever reading host most recently RESUMED
     * (`MainBibleActivity.onResume`/`unFreeze()` exist only to reconcile the two, which is why they
     * exist at all), so for a frozen or not-yet-resumed second host it is the OTHER host's
     * repository. R1 substituted seven `activity.windowRepository` reads in
     * `ComposeReadingViewHost` for it and its reviewer verified that safe at the time; R7 then
     * turned `MainBibleActivity.windowRepository` from a `lateinit var` kept in sync at every
     * assignment into a view onto `ReadingAppBootstrap`, which made the verification stale. R6c1's
     * fix round found the same defect one rung down and fixed it with a supplier bound to the
     * owning host; R6d discharges it here, at the top of the chain.
     *
     * Declared as a property and read at CALL time by every one of those seven sites — never
     * captured into a field of the reading view, which would freeze one host's repository into the
     * other's view.
     */
    val hostWindowRepository: WindowRepository

    /**
     * The reading view's COMMAND SURFACE (design spec §3.2), owned by this host.
     *
     * One member rather than the ~29 the surface exposes: R6's measurement is why it was BLOCKED —
     * routing those references through this interface one-by-one needed ~19 new members on it, and
     * three had no honest `NavHostComposeActivity` body. R6c1/R6c2 re-typed the collaborator itself
     * off `MainBibleActivity`, so a second host can now OWN one, and the contract shrinks to
     * "every reading host has a command surface".
     */
    val readingCommands: ReadingCommands

    /**
     * The window-inset ledger (design spec §3.3), owned by this host. R6b re-typed it off
     * `MainBibleActivity` per Ruling C — the arithmetic is host-agnostic and the one host-specific
     * act, applying the IME padding, is an injected sink a Compose host answers with a documented
     * no-op. Same one-member-instead-of-many argument as [readingCommands].
     */
    val readingInsets: ReadingInsets

    /**
     * Hide whatever classic toolbar row this host draws above the reading view, if it draws one.
     *
     * The ONE place `ComposeReadingViewHost.install()` touched `activity.binding`
     * (`toolbarLayout`/`toolbarDivider` → `View.GONE`), expressed as the intention rather than as
     * the two view writes, because spec §3.1 says `binding` never enters this contract.
     *
     * **A default no-op, deliberately, and this is not Ruling D's hazard.** Ruling D forbids a host
     * answering a real command with silence; hiding a row a host does not have IS silence, and
     * `NavHostComposeActivity` has no classic toolbar row — it composes its own chrome. A host that
     * grows one overrides this. `MainBibleActivity` does.
     */
    fun hideClassicToolbarRow() {}
}
