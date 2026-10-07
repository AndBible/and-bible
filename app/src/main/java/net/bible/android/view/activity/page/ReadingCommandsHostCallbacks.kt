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

import net.bible.android.control.page.window.WindowRepository
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.android.view.activity.page.screen.DocumentViewManager

/**
 * What [ReadingCommands] (and the [MenuCommandHandler] it owns) need from their host beyond R4's
 * deliberately narrow [ReadingHostActivity] — reading-host re-typing R6c2.
 *
 * **Why a bundle and not more interface members.** Ruling A keeps `ReadingHostActivity` at exactly
 * seven members, and the addendum's R6 measurement is why: routing this command surface through the
 * interface needed ~19 new members on it, three of which `NavHostComposeActivity` had no honest
 * body for. A bundle moves that question to whoever BUILDS it — today `MainBibleActivity`, which
 * still holds every one of these — instead of forcing a host to answer it up front. Exactly the
 * shape R6a ([BibleViewHostCallbacks]) and R6b (`ReadingInsetsHostCallbacks`) already established.
 *
 * **Why [hostActivity] is `ActivityBase` and is not a cast in disguise.** Both reading hosts really
 * ARE an `ActivityBase` (`MainBibleActivity : CustomTitlebarActivityBase : ActivityBase`,
 * `NavHostComposeActivity : ActivityBase`), so nothing is cast and nothing can fail. What it
 * exposes is the plain ANDROID Activity API that this command surface's callees demand BY
 * SIGNATURE and that no host-shaped interface should pretend to hide: `startActivity`,
 * `startActivityForResult`, `registerForActivityResult`, [ActivityBase.awaitIntent],
 * `applyTheme()`, the vararg `getString(resId, args…)` the single-arg
 * [ReadingHostActivity.getString] cannot carry, and the bare value-passes of
 * `SearchControl.getSearchIntent(…, activity: Activity)`,
 * `CurrentPage.startKeyChooser(context: ActivityBase)`,
 * `OptionsMenuItemInterface.openDialog(activity: ActivityBase, …)`,
 * `exportStudyPads(activity: ActivityBase, …)`,
 * `BookmarkControl.exportBookmarksToCSV(context: ActivityBase, …)`,
 * `BackupControl.backupPopup(activity: ActivityBase)`, `BugReport.reportBug(context_: ActivityBase?, …)` and
 * `CommonUtils.showHelp(callingActivity: ActivityBase, …)`. Wrapping those in a dozen more lambdas would
 * decouple nothing — the callee still needs an Activity. R6a's bundle carries the same member for
 * the same reason, and R7 set the precedent when it gave `ReadingAppBootstrap` a
 * `ComponentActivity` rather than the reading-host interface.
 *
 * Pure `Context` passes do NOT come through here: they use [ReadingHostActivity.hostContext], which
 * R4 declared for exactly that (`ScreenLauncher.intentFor(context: Context, …)`,
 * `textDisplaySettingsRoute(…)`, `ContextCompat.getDrawable`,
 * `SplitModePreference(mainBibleActivity: Context)`, `AlertDialog.Builder(context)`).
 *
 * **Every other member is a lambda, and every one is read at CALL TIME, never captured.** Not
 * style: [windowRepository] is the reason R6c1's fix round exists — `windowControl.windowRepository`
 * is whichever host most RESUMED last, which is a different object from the owning host's own
 * repository for a second, not-yet-resumed reading host (`MainBibleActivity.onResume` and
 * `unFreeze()` exist only to reconcile the two). [composeReadingViewHost], [readingInsets],
 * [documentViewManager], [currentNightMode] and [transportBarVisible] are all
 * either late-bound or mutable on the host for the same class of reason. A supplier invoked once and stored is that bug in new clothes.
 *
 * **A missing body shows up here as an unfilled constructor parameter, not as a silent no-op
 * override** — addendum Ruling D. Five members are nonetheless honest no-ops for a Compose host and
 * say so on their own kdoc; the workspace-switching pair is deliberately NOT among them, and is not
 * in this bundle at all: [ReadingCommands] owns those bodies now precisely so no host can answer
 * them with nothing.
 */
class ReadingCommandsHostCallbacks(
    /** The host as a plain Android Activity — see the class kdoc for why this is not a cast. */
    val hostActivity: ActivityBase,

    /**
     * The mounted reading-view host, or `null` before the host installs one. 24 of this task's 93
     * references, and a genuine construction cycle ([ReadingCommands] needs it, and the host will
     * need `ReadingCommands`), which is why it is late-bound rather than a value. Already nullable
     * and already assigned after construction on `MainBibleActivity`, so this changes no null
     * behaviour whatsoever.
     */
    val composeReadingViewHost: () -> ComposeReadingViewHost?,

    /**
     * THIS host's own window repository — not `WindowControl`'s. See the class kdoc; R6c1's fix
     * round 1 (review Important 2) is the whole argument.
     */
    val windowRepository: () -> WindowRepository,

    /**
     * R6b's inset ledger, reached as the already-extracted collaborator it is rather than through
     * the Activity. A supplier and not a value because `MainBibleActivity` constructs its
     * `readingInsets` AFTER its `readingCommands` (property initialisation order), so a value would
     * be null here.
     */
    val readingInsets: () -> ReadingInsets,

    /**
     * The host's view manager. Ruling D names it as the one thing the workspace-switch body needs
     * that is neither a Koin singleton nor owned by [ReadingCommands]. `lateinit` on
     * `MainBibleActivity` (assigned in `onCreate`), so it MUST be read at call time — which is
     * exactly what every caller of it did before.
     */
    val documentViewManager: () -> DocumentViewManager,

    // Platform-dialog removal Task 10: `llmDialogHelper` (the `?:` fallback of the pane menu's AI
    // row) is gone -- `LlmDialogHelper` itself is deleted, since the pane menu only exists on the
    // Compose path and slice 8 made NavHost the only reading host, so the fallback was unreachable.

    /** `MainBibleActivity.currentNightMode`, re-read on every access (it flips at runtime). */
    val currentNightMode: () -> Boolean,

    // R6d fix round 1 (review Important): `pageTitleText` used to be a supplier here, on the
    // grounds that it throws `KeyIsNull`. That was the wrong trade — the body is
    // host-independent `pageControl` arithmetic, Ruling E allow-lists the nested class anyway, and
    // keeping it on the host is what let R6d copy all eleven of its lines into
    // `NavHostComposeActivity`. It lives on `ReadingCommands.pageTitleText` now, ONE copy for both
    // hosts, so this bundle member is gone rather than merely unused.

    /**
     * Speak transport bar visibility. **Deliberately NOT one of the honest no-ops below**, even
     * though the setter once also updated classic chrome: the reading host now calls
     * `SpeakTransportServiceImpl.setTransportVisible`,
     * which updates the single source of truth the COMPOSE side observes, and the main menu's Speak row
     * and the Compose toolbar's Speak button both drive it. A host that answered this with a no-op
     * would leave the Speak transport bar unreachable with nothing to report it — Ruling D's hazard,
     * in a different member.
     */
    val transportBarVisible: () -> Boolean,
    val setTransportBarVisible: (Boolean) -> Unit,

    /**
     * Classic's `updateBottomBars` only posted an event nothing subscribed to; the restore rail reads
     * `WindowStateService.layout`, refreshed by `WindowRepository`'s notifiers. A host that later needs
     * a bottom-bar refresh supplies it here; the workspace switch still makes the call.
     */
    val updateBottomBars: () -> Unit,

    /** Classic toolbar: the page/document title `TextView`s. A Compose host draws its title from
     *  state and honestly has nothing to do here. */
    val updateTitle: () -> Unit,

    /**
     * "Something changed that the reading toolbar may be showing" -- the signal
     * [ReadingCommands.applyChosenDocument] raises after the user picks a document, and its only
     * caller here.
     *
     * **Not an honest no-op, and named for what it MEANS rather than for classic's member.** Review
     * fix round 1, Important 1: this used to be called `updateActions` and its kdoc claimed the
     * classic-toolbar no-op, which is only half of what `MainBibleActivity.updateActions()` does.
     * Its last two statements are `composeReadingViewHost?.rebuildDrawer(showSearch, showSpeak)`
     * and `composeReadingViewHost?.refreshHostedState()`, both COMPOSE-observable, and
     * `showSearch`/`showSpeak` (`documentControl.currentPage.currentPage.isSearchable`/
     * `.isSpeakable`) are locals of that function reachable nowhere else. A host that read the old
     * kdoc and supplied `{}` would silently lose the Compose toolbar refresh and the drawer's
     * Search/Speak enablement after every document choice -- Ruling D's hazard, in the member the
     * first pass of this bundle missed. It meets exactly the test that promoted
     * [transportBarVisible] and [updateBottomBars] out of the no-op group.
     *
     * `MainBibleActivity` answers it with its whole `updateActions()` (the classic button row AND
     * those two calls); a Compose host answers it with its own `rebuildDrawer`/`refreshHostedState`
     * pair. It is deliberately NOT split into a classic-chrome half plus a real half: the sole
     * caller needs both, so a split would either fire the Compose refresh twice on the classic host
     * or force a change to `updateActions()`' own twelve classic call sites, neither of which
     * belongs in a re-typing commit.
     */
    val onToolbarStateMayHaveChanged: () -> Unit,

    /**
     * The owning host's `ReadingAppBootstrap.requestSdcardPermission()` — reading-host re-typing
     * T8d, one of the five steps of [ReadingCommands.preferenceSettingsChanged].
     *
     * **Not an honest no-op**, and a supplier rather than a value for the same reason the rest are:
     * each reading host builds its OWN `ReadingAppBootstrap` (that object holds the host's window
     * repository and its paused flag), so this is the one step of that body which cannot be reached
     * from the collaborator without asking the host which bootstrap is its own. A host that answered
     * it with `{}` would leave the "manual install folder" preference unable to ever ask for the
     * permission it needs, since the way back from Settings is the only place it is requested.
     */
    val requestSdcardPermission: () -> Unit,

    /** Classic toolbar: `binding.strongsButton`'s icon and alpha. Honest no-op, same reason. */
    val updateStrongsButton: () -> Unit,

    /**
     * Classic `binding.drawerLayout` toggle — the defensive fallback of [ReadingCommands]'s
     * `composeToggleDrawer`, for the window before a Compose host is installed. Honest no-op for a
     * host with no `DrawerLayout`.
     *
     * **`MainBibleActivity` must bind the `binding.drawerLayout` lines themselves here, never
     * `{ toggleDrawer() }`:** its own `toggleDrawer()` override delegates BACK to
     * `readingCommands.composeToggleDrawer()`, so that would be unbounded recursion through the
     * Activity's own delegating stub.
     */
    val toggleNativeDrawer: () -> Unit,

    /** Classic `binding.drawerLayout.open()` + `requestFocus()`, the Alt+M shortcut's pre-Compose
     *  fallback (R6a's `openDrawerAndFocusIt`, which this now supplies). Honest no-op for a host
     *  with no `DrawerLayout`; the Compose drawer is a modal sheet that takes input while open. */
    val openNativeDrawerAndFocusIt: () -> Unit,
)
