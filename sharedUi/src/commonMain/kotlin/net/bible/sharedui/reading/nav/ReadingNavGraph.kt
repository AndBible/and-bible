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

package net.bible.sharedui.reading.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedcore.reading.ReadingViewHostCallbacks
import net.bible.sharedcore.reading.ReadingViewHostHandlers
import net.bible.sharedcore.reading.ReadingViewKey
import net.bible.sharedcore.reading.ReadingViewVisibility

/**
 * What the `reading` destination needs from its platform — the same top-level shape as every other
 * cluster's deps holder ([net.bible.sharedui.navigation.nav.ChooserNavDeps],
 * [net.bible.sharedui.workspaces.nav.WorkspaceNavDeps]): the window title first, then the slots.
 *
 * **Why there is a [content] slot instead of the reading view's own ~70 parameters.** Every other
 * destination in these graphs calls a `*Screen` composable that lives in `:sharedUi` and takes
 * host-supplied lambdas. The reading view cannot: the composition it needs is
 * `ComposeReadingViewHost.ReadingViewContent` (`:app`, extracted from `mountComposeView` by this
 * same task), and that body reads `CommonUtils.settings` directly, hosts each pane's `BibleView`
 * WebView through `AndroidView`, and resolves `R.drawable` painters. None of that can cross into
 * `commonMain`, and moving it would mean restructuring `mountComposeView`, which the plan's Global
 * Constraints and design §10 put out of scope for this batch. A pre-built `@Composable` slot is the
 * established shape for exactly this situation in this codebase — `mountComposeView`'s own
 * `agentLogSlot`/`speakBarSlot`/`quickSheetSlot`, and `ChooseDocumentDeps.topBarActions` — so this
 * destination is one slot rather than seventy.
 *
 * **What this destination is, at HEAD.** Its [content] renders the real reading view on BOTH
 * hosts: `MainBibleActivity` mounts it into a `ViewGroup` through `ComposeReadingViewHost.install`,
 * and `NavHostComposeActivity.readingNavDeps` composes the very same `ComposeReadingViewHost
 * .ReadingView` -- the one argument block, bound once -- since reading-host re-typing R8. That
 * slot was an `error(...)` from the commit that introduced this file until R8, because
 * `ComposeReadingViewHost` was TYPED ON `MainBibleActivity` and no second host could construct one.
 * R6a--R6d re-typed it: it is `ComposeReadingViewHost(activity: ReadingHostActivity)` now, holds no
 * `MainBibleActivity` type position at all, and every host reference it makes resolves through the
 * twelve members of `ReadingHostActivity` that both Activities answer honestly. (Any count of
 * "`activity.` references to `MainBibleActivity`" quoted here before R8 was a grep artefact --
 * the pattern also matched the package path `net.bible.android.view.activity.page` -- and is
 * deliberately not replaced with another number: the fact that matters is the TYPE, not a tally.)
 *
 * What is still NOT true: nothing ROUTES here in the shipped app. `ScreenLauncher.MIGRATED` is
 * slice 7 Task 8's and `MainBibleActivity` is still the launcher.
 */
class ReadingNavDeps(
    /**
     * The Activity that hosts this graph, as an opaque identity token (reading-host re-typing R7b).
     *
     * The destination registers itself with [ReadingViewVisibility] and [ReadingViewHostCallbacks]
     * under this token, and both resolve it through
     * [net.bible.sharedcore.reading.ReadingHostPresence] — so a destination that is still composed
     * under a BACKGROUNDED host counts as neither visible nor current. Without it, "registered"
     * meant "on screen", which is a dead back key on a classic secondary Activity and volume keys
     * dispatched into a reading view the user cannot see; that object's kdoc has the argument.
     *
     * **A deps field rather than a `CompositionLocal`**, because `rememberUpdatedState(deps)` below
     * already exists for exactly this class of staleness: a local would be a second way for the
     * destination to learn the same fact, readable from places the deps are not, and the two could
     * then disagree about which host is registering. `Any` rather than an Activity type because
     * `:sharedCore`/`:sharedUi` `commonMain` is Android-free; it is only ever compared by identity.
     */
    val host: Any,
    /**
     * The HOST WINDOW's title (Recents, TalkBack), classic `MainBibleActivity`'s
     * `android:label="@string/app_name_short"` (`AndroidManifest.xml:103`). Applied from a
     * `LaunchedEffect` keyed on the VALUE, the shape every other destination uses; the host
     * Activity's own manifest block carries no label at all, so without this the reading view's
     * window title would silently become the application label.
     */
    val windowTitle: String,
    /**
     * The reading view itself — see this class's kdoc for why it is one slot. Composed directly in
     * the destination arm, with no wrapper of any kind, so that what the destination renders is
     * byte-for-byte what `ComposeReadingViewHost.install` renders into the classic Activity.
     *
     * **Work this slot owed, recorded here because this is where the tasks that pay it look.** All
     * four items are paid as of reading-host re-typing T8a -- the three this list was opened with
     * and the fourth R8's own review found. Kept rather than deleted: each entry names the task that
     * paid it and what it did, which is what a later reader needs when the same seam misbehaves.
     *
     *  1. ~~`ExternalKeyboardBack` does not close the drawers.~~ **PAID by reading-host re-typing
     *     R8**, the task that made this slot real. `NavHostComposeActivity.readingViewKeyPressed`
     *     now calls `readingCommands.composeCloseDrawerIfOpen()` before returning classic's
     *     unconditional `true`. Only the Compose half exists on that host: classic also closed
     *     `binding.drawerLayout`, and that XML `DrawerLayout` is `MainBibleActivity`'s alone.
     *  2. ~~`ReadingViewVisibility` keeps `HistoryManager.goBack()` from finishing the screen on
     *     top once a composed destination's depth is live under a backgrounded host -- a dead back
     *     key.~~ **PAID by R7b**, which made both seams resolve through
     *     [net.bible.sharedcore.reading.ReadingHostPresence] and this class carry [host].
     *  3. ~~`ReadingViewHostCallbacks.current` is "last published", not "the foreground host".~~
     *     **PAID by R7b**, the same change: it was the same divergence and it wanted the same answer.
     *
     *  4. ~~classic's `onResume` document refresh, and the reconciliation block around it.~~
     *     **PAID by reading-host re-typing T8a**, the task this item named. `NavHostComposeActivity`
     *     now subscribes to `DocumentChanges.installedChanged` (it notifies it from six of its own
     *     destinations and listened to none), and its `onResume` runs the WHOLE of classic's block
     *     (`MainBibleActivity.kt:1964-1989`), not the one line R8 refused to port on its own: the
     *     `windowControl.windowRepository` reclaim and its `currentWorkspaceId = currentWorkspaceId`
     *     reload, which still takes precedence over the document refresh; the pending-flag arm
     *     (`reloadAllWindows(true)` + the host's own half of `updateActions()`); the tilt-scroll
     *     focus hand-back, gated on the destination having composed because this host's reading view
     *     is composed rather than built in `onCreate`; and `handlePendingAgentResult()`. See
     *     `NavHostComposeActivity.reconcileReadingStateOnResume`. The ENTRY-time half was R8's and is
     *     unchanged -- `NavHostComposeActivity.readingViewHost` still carries `setupUi`'s own
     *     `reloadAllWindows(true)`, and T8a did not port it a second time.
     *
     *  Items 2 and 3 were PRECONDITIONS, not follow-ups -- both would have become user-visible the
     *  moment this slot rendered the real reading view, which is why R7b paid them BEFORE R8 made
     *  it render. Item 4 was the opposite: it only becomes visible once something ROUTES here, so it
     *  was paid by T8a, the task that lands everything that must be true before the launcher flips,
     *  and not by R8.
     */
    val content: @Composable () -> Unit,
    /**
     * Classic `MainBibleActivity.onKeyDown`'s body, minus the decoding: the volume-key gates
     * (`volume_keys_scroll`, `speakControl.isSpeaking`, `AudioManager.isMusicActive`) and the
     * `BibleView.volumeUpPressed()`/`volumeDownPressed()` calls, plus the external-keyboard BACK
     * branch that closes the drawer. Built host-side because every one of those is Android or
     * `:app`; published from this destination's effect because only the destination knows when it
     * is on screen. Returns whether the key was CONSUMED — see [ReadingViewHostHandlers.onKey].
     */
    val onKey: (ReadingViewKey) -> Boolean,
    /** Classic `MainBibleActivity.onScreenTurnedOn`, host-side. */
    val onScreenTurnedOn: () -> Unit,
    /** Classic `MainBibleActivity.onScreenTurnedOff`, host-side. */
    val onScreenTurnedOff: () -> Unit,
    /** Sets the host window's title — graph-wide, exactly as in the other clusters' deps. */
    val setWindowTitle: (String) -> Unit,
    /**
     * **reading-host re-typing T8c: the answers this destination's OWN launches produce.**
     *
     * The reading view opens seven screens that hand something back — the label manager (five entry
     * points), the my-document page chooser, the reading-progress screen, the bookmark list and the
     * my-documents list. Every one of them is a destination of the SAME graph this destination is
     * registered into, and the host is `android:launchMode="singleTop"`, so a
     * `startActivityForResult`/`awaitIntent` aimed at it is answered by `onNewIntent` on the live
     * instance: no Activity result is ever produced, the child publishes into its
     * [NavResultChannel]'s pending slot and pops back HERE, and before T8c nothing here collected
     * it. The user's answer went into a void — the batch's Ruling D failure, and worse than a dead
     * button because it looks like it worked.
     *
     * One collector per channel, built by the host (which owns both the channels and the record of
     * what the reading view asked for); the arm's only job is to compose them, which is what puts
     * the collection INSIDE this destination's composition. That matters twice over: a collector can
     * only fire while the reading view is the destination on screen — so it cannot take an answer
     * the bookmark list or the text-settings editor asked for, since those arms are composed only
     * while THEY are on top — and by the time one fires this destination's `DisposableEffect` has
     * already run, so `ReadingViewVisibility` reports the reading view visible for the
     * `HistoryManager.recordIfCreated` an applied result calls synchronously. The consumer can never run before the destination
     * has composed, which is the invariant `ReadingViewVisibility`'s kdoc names.
     *
     * Empty is legal and means "this host answers nothing in-graph" — the classic Activity, whose
     * launches really do reach a second Activity and come back through `onActivityResult`.
     *
     * **No default value, deliberately** (T8c fix round, M3). An `= emptyList()` default was the
     * first shape, and it is the one that quietly rebuilds the very defect this slot exists to fix:
     * a future host that simply forgot the parameter would compile, run, and drop every answer in
     * silence again — today's host guarded by `ReadingInGraphResultGuardTest`, tomorrow's not.
     * Making it required costs a caller one explicit `results = emptyList()` and turns "you have to
     * remember" into "the compiler remembers".
     */
    val results: List<ReadingResultCollector<*>>,
)

/**
 * One [NavResultChannel] the reading destination collects on the reading view's behalf, its gate,
 * and what to do with the answer.
 *
 * Modelled on the "Hide-labels round trip" nav-graph slice 7 Task 5 established (see
 * `WorkspaceNavGraph.textDisplaySettingsNavState` and its `awaitingHideLabels` gate): an in-graph
 * push plus a consumer that is gated on having asked. The gate and the apply are the HOST's
 * lambdas, not this class's state, for the reason that precedent's `navStateMemo` exists —
 * navigation-compose disposes this arm's composition while a child sits on top of it, so anything
 * remembered here would be gone by the time the answer came back. The host's record survives
 * because the host does.
 *
 * **Why it cannot consume twice.** [NavResultChannel.consume] clears the channel in the same breath
 * as it reads it, so the `LaunchedEffect` re-running (which it does, immediately, keyed on the now
 * `null` pending value) hits the early return; and [apply] is handed a result only after the host
 * has removed its record, so a second delivery of the same value would find no request and be
 * dropped rather than applied.
 *
 * **Why it cannot never consume.** Every launch that can produce one of these results records a
 * request first — the host's `startActivityForResult` override is the single funnel every one of
 * them passes through, `awaitIntent` included — and a child that publishes to `pending` pops to
 * THIS destination, whose composition then runs this effect.
 *
 * **Why an unclaimed result is consumed rather than left.** A result nobody asked for is a bug, and
 * Ruling D says it must be loud, not silent — [dropUnclaimed] logs it. It is also CLEARED: a
 * pending value left lying in the channel would be applied by the next request that did arrive,
 * which turns a logged anomaly into a wrong answer.
 */
class ReadingResultCollector<T>(
    private val resultChannel: NavResultChannel<T>,
    /** Whether the reading view is waiting for this channel's answer. */
    private val awaiting: () -> Boolean,
    /** Applies a claimed answer; the host clears its record before applying. */
    private val apply: (T) -> Unit,
    /** Ruling D: an answer nobody asked for is logged, not swallowed. */
    private val dropUnclaimed: (T) -> Unit,
) {
    @Composable
    fun Collect() {
        val pending by resultChannel.pending.collectAsState()
        LaunchedEffect(pending) {
            if (pending == null) return@LaunchedEffect
            // Read BEFORE consuming: `apply` clears the host's record, so asking afterwards would
            // always answer "no".
            val claimed = awaiting()
            val result = resultChannel.consume() ?: return@LaunchedEffect
            if (claimed) apply(result) else dropUnclaimed(result)
        }
    }
}

/**
 * The `reading` destination: what used to be `MainBibleActivity`'s composition, registered into the
 * app's single `NavHost` by the host Activity.
 *
 * The arm is deliberately tiny. Three things belong to the DESTINATION rather than to the content
 * it renders, and all three are the same fact stated once — "this reading view is on screen":
 *
 *  1. [ReadingViewVisibility], the predicate `HistoryManager.createHistoryItem` asks instead of
 *     `CurrentActivityHolder.currentActivity is MainBibleActivity` (design §5.1). It is the only
 *     producer of `KeyHistoryItem`, i.e. of the verse back-stack and of history persistence, so it
 *     has to be entered before anything the content hosts can call `HistoryManager.recordIfCreated` — which a
 *     `DisposableEffect` in the arm is, since effects run with the first composition. Both
 *     registrations below are made under [ReadingNavDeps.host], so neither of them says "on screen"
 *     while this destination's host is in the background (R7b).
 *  2. The two per-destination `ActivityBase` callback families ([ReadingViewHostCallbacks]): volume
 *     keys (and, by inversion, `enableGenericVolumeScroll`) and screen on/off.
 *  3. The host window's title.
 *
 * Both (1) and (2) are entered and left by ONE `DisposableEffect`, not two: they answer the same
 * question, and two effects could in principle be disposed in either order and leave the host
 * consulting a handler for a reading view History has already forgotten.
 *
 * [navController] is unused today and kept because every `*NavGraph` in this module takes it: the
 * reading view's outbound navigation is still built as Intents aimed at the host (which, being
 * `singleTop`, answers them with `onNewIntent` and navigates this same graph), and the RESULTS those
 * launches produce are collected by [ReadingNavDeps.results] rather than by anything this arm
 * navigates itself. Retyping those launches into in-graph `navigate` calls is a later task, and
 * adding the parameter then would change a signature the host already calls.
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.readingNavGraph(navController: NavHostController, deps: ReadingNavDeps) {
    composable(route = NavRoutes.READING) {
        // The effect below is keyed on Unit and so captures whatever it captures ONCE. Publishing
        // `deps.onKey` and friends straight into it would therefore pin the lambdas of the deps
        // instance that happened to be current at the first composition: safe only as long as the
        // host `remember`s its deps, which today's does but which nothing forces it to — a host
        // that writes `ReadingNavDeps(...)` inline, the obvious thing and what several other
        // clusters' call sites look like, would publish permanently stale handlers with no compile
        // error. rememberUpdatedState + delegating handlers make that impossible without giving up
        // the Unit key.
        val currentDeps = rememberUpdatedState(deps)
        // One effect for both seams — see this function's kdoc. Keyed on Unit: the destination is
        // argument-free, so there is nothing that could legitimately re-key it, and a re-key would
        // mean an exit/enter pair that History would see as the reading view briefly leaving.
        DisposableEffect(Unit) {
            // The HOST is read ONCE and the same token is used to register, to publish and to undo
            // both -- unlike the handler lambdas above, which delegate through `currentDeps` on
            // every call. It is an identity, and an enter/publish that was undone with a different
            // token than it was made with would leave this destination registered forever (and
            // un-register some other host's): exactly the unbalanced state `exit`'s kdoc refuses to
            // paper over. A host cannot change identity under a live composition anyway; if one
            // ever could, re-keying this effect on it -- not reading it twice -- is the fix.
            val host = currentDeps.value.host
            ReadingViewVisibility.enter(host)
            val unpublish = ReadingViewHostCallbacks.publish(
                ReadingViewHostHandlers(
                    onKey = { key -> currentDeps.value.onKey(key) },
                    onScreenTurnedOn = { currentDeps.value.onScreenTurnedOn() },
                    onScreenTurnedOff = { currentDeps.value.onScreenTurnedOff() },
                ),
                host = host,
            )
            onDispose {
                unpublish()
                ReadingViewVisibility.exit(host)
            }
        }

        LaunchedEffect(deps.windowTitle) { deps.setWindowTitle(deps.windowTitle) }

        // --- the answers this destination's own launches produce, collected (T8c) ---
        // See ReadingNavDeps.results. Keyed by collector identity rather than by list position so
        // that a host which ever rebuilt the list in a different order could not hand one channel's
        // LaunchedEffect the state of another's.
        for (collector in deps.results) {
            key(collector) { collector.Collect() }
        }

        deps.content()
    }
}
