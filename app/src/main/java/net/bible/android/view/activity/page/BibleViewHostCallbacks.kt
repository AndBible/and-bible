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

import net.bible.android.database.IdType
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.screen.ComposeReadingViewHost
import net.bible.service.llm.PromptContext
import net.bible.sharedcore.event.Events

/**
 * What [BibleView] and its [BibleJavascriptInterface] need from their host that R4's
 * [ReadingHostActivity] deliberately does not carry.
 *
 * **Why a bundle and not more interface members.** Reading-host re-typing Ruling A keeps
 * `ReadingHostActivity` narrow, and the addendum's R6 measurement is the reason: routing a
 * collaborator's whole command surface through the interface needed ~19 new members on a 7-member
 * interface, three of which `NavHostComposeActivity` has no honest body for. A bundle moves that
 * question to whoever BUILDS it — today `ReadingCommands`, which still holds a `MainBibleActivity`
 * — instead of forcing every host to answer it before the ladder's next rung is even reached.
 *
 * **Why [hostActivity] is `ActivityBase` and is not a re-label.** R5's first pass was rejected for
 * recovering the old surface with an `as`-downcast back to the Activity
 * behind an interface-shaped parameter: a cast that keeps
 * the whole coupling and is a `ClassCastException` waiting for Task 8. This is the opposite. Both
 * reading hosts really ARE an `ActivityBase` (`MainBibleActivity : CustomTitlebarActivityBase :
 * ActivityBase`, `NavHostComposeActivity : ActivityBase`), so no cast happens and no cast can fail,
 * and the surface it exposes is the plain ANDROID Activity API that four library helpers demand by
 * signature and no host-shaped interface should be pretending to hide:
 * `startActivityForResult`/[ActivityBase.awaitIntent], `CommonUtils.showHelpDialog(activity: Activity, …)`,
 * `BackupControl.saveOrShare(activity: ActivityBase, …)`, `SearchControl.getSearchIntent(…, activity: Activity)`
 * and `CurrentPage.startKeyChooser(context: ActivityBase)`.
 * Wrapping those in six more lambdas would decouple nothing — the callee still needs an Activity —
 * and R7 set the precedent explicitly when it gave `ReadingAppBootstrap` a `ComponentActivity`
 * rather than the reading-host interface.
 *
 * Everything ELSE here is a lambda, because everything else is `MainBibleActivity`-only behaviour:
 * the host that eventually builds this bundle has to supply a real body for it, and a missing body
 * shows up here as an unfilled parameter rather than as a silent no-op override (Ruling D).
 *
 * Every member is read at CALL time, never captured: `currentNightMode` and the three inset
 * suppliers below are all mutable state on the host.
 */
class BibleViewHostCallbacks(
    /** The host as a plain Android Activity — see the class kdoc for why this is not a cast. */
    val hostActivity: ActivityBase,

    /** The gesture listener's swipe-left/right; `MainBibleActivity.next`/`previous`. */
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,

    /** The selection action mode's "AI" item and three of the JS bridge's entry points. */
    val showLlmPromptSelector: (selection: Selection, context: PromptContext) -> Unit,

    /**
     * Retarget a search into the reading view's own search when a Compose host is mounted; `false`
     * means "not hosted, use the classic Intent". `preDecorated` is load-bearing — see
     * `ComposeReadingViewHost.openSearch`'s kdoc.
     */
    val composeSearchIfHosted: (seedQuery: String?, preDecorated: Boolean) -> Boolean,

    /** Alt+M's drawer shortcut: `true` when the Compose drawer took it. */
    val composeOpenDrawerIfHosted: () -> Boolean,

    /** Alt+M's classic fallback, for the window before the Compose host is installed. */
    val openDrawerAndFocusIt: () -> Unit,

    /** The mounted reading-view host, or `null` before `setupUi` installs one. */
    val composeReadingViewHost: () -> ComposeReadingViewHost?,

    /** The JS bridge's "regenerate this AI page" action. */
    val showRegenerate: (pageId: IdType, bibleView: BibleView) -> Unit,

    /** The JS bridge's `crash()` debug hook — `BibleViewFactory.crashAll` on the host's factory. */
    val crashAllBibleViews: () -> Unit,

    /** `MainBibleActivity.currentNightMode`, re-read on every access (it flips at runtime). */
    val currentNightMode: () -> Boolean,

    /**
     * The three `ReadingInsets` values [BibleView] reads, as suppliers rather than the ledger
     * itself: R6b re-types `ReadingInsets` next and a supplier leaves it free to change shape.
     *
     * **These feed `set_offsets`' JS payload, so a changed value is visible to the web view.** They
     * are passed through verbatim — no arithmetic of any kind happens on this side of the boundary;
     * the division by `displayMetrics.density` stays exactly where R2 left it, in [BibleView].
     */
    val imeHeight: () -> Int,
    val topOffset2: () -> Int,
    val bottomOffsetForWebView: () -> Int,

    /** The host's [ReadingInsets.offsetsChanged], read when BibleView starts listening. */
    val insetsChanges: () -> Events<OffsetsChange>,
)
