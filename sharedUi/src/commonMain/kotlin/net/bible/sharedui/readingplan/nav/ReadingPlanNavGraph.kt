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

package net.bible.sharedui.readingplan.nav

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.readingplan.DailyReadingController
import net.bible.sharedcore.readingplan.DailyReadingListController
import net.bible.sharedcore.readingplan.ReadingPlanSelectorController
import net.bible.sharedui.readingplan.DailyReadingListScreen
import net.bible.sharedui.readingplan.DailyReadingScreen
import net.bible.sharedui.readingplan.ReadingPlanSelectorScreen

/**
 * What a child destination picked before it popped, carried back to `DAILY_READING_PATTERN`.
 *
 * Replaces the two classic `setResult(Activity.RESULT_OK, Intent(<value as action>))` round trips
 * (`DailyReadingListComposeActivity.kt:51-54` and `ReadingPlanSelectorComposeActivity.kt:31-39`,
 * both read back by a `registerForActivityResult` launcher on `DailyReadingComposeActivity`): a
 * nav destination has no `setResult`, and smuggling a value through an `Intent`'s ACTION field has
 * no equivalent at all here.
 *
 * A [MutableStateFlow] on [ReadingPlanNavDeps] rather than the nav library's saved-state handle
 * because it is plain `commonMain` Kotlin, unit-testable without a `NavHostController`, and does
 * not depend on an API surface this migration has not verified for the multiplatform fork. The
 * host creates the flow, so it also survives the parent destination's composition being disposed
 * while the child sits on top of it (see [ReadingPlanNavDeps.pendingSelection]).
 */
sealed interface ReadingPlanSelection {
    data class Plan(val planCode: String) : ReadingPlanSelection
    data class Day(val day: Int) : ReadingPlanSelection
}

/** The concrete plan + day the host currently has loaded. See [DailyReadingDeps.loaded]. */
data class LoadedReadingDay(val planCode: String, val day: Int)

/**
 * The outcome of one [DailyReadingDeps.loadDay] call. [LOADED] and [FAILED] are both "nothing for
 * the graph to do" (on failure the host has already pushed the error state into the controller, so
 * the screen shows classic's error dialog) — they are still distinct values rather than a boolean
 * so the graph's `when` states, rather than implies, that a failure is deliberately not routed
 * anywhere.
 */
enum class DailyReadingLoad {
    LOADED,

    /** No plan is selected, or the selected plan's file is gone — classic's `onCreate` gate. */
    NO_PLAN,

    /** The load threw; the host logged it and pushed `showError()` into the controller. */
    FAILED,
}

/**
 * [DailyReadingScreen]'s platform-supplied slots — slice 3's only destination with real platform
 * weight, ported from classic `DailyReadingComposeActivity`.
 *
 * - [controllerFor] builds the (per-back-stack-entry) [DailyReadingController] around the two
 *   navigation edges only this graph can supply — same shape as
 *   [net.bible.sharedui.ai.nav.AiPromptsDeps.controllerFor]. It matters that this is a factory and
 *   not a host-held instance: navigation-compose composes only the VISIBLE back-stack entries, so
 *   this destination's composition is disposed while the selector or the day list sits on top of
 *   it and re-enters — with a fresh `remember`ed controller — when that child pops. [loadDay] below
 *   is what re-seeds that fresh controller.
 * - [loadDay] is classic's `loadDailyReading(plan, day)` plus its `onCreate` "is a plan selected?"
 *   gate, in one host call: it mutates `ReadingPlanControl`, reads the day's readings and pushes
 *   the snapshot into the controller. All of that needs `ReadingPlanControl`/JSword, which have no
 *   `commonMain` equivalent.
 * - [loaded] is the host's own record of what [loadDay] last landed on. The graph turns it into
 *   [ReadingPlanNavDeps.setHistoryRoute]'s value, and re-reads it when this destination re-enters
 *   composition so a day chosen through the day list is not silently reverted to the route's
 *   original (usually argument-free) day. Host-owned, because it must outlive this destination's
 *   composition for exactly the reason given under [controllerFor].
 * - [subscribeEvents] wires classic's `ABEventBus.register(this) { onMain<ReadingPlansUpdatedViaSyncEvent>
 *   { recreate() }; onMain<SpeakEvent> { pushSpeakState() } }` and returns the unsubscribe.
 *   Route-scoped (a `DisposableEffect` in this destination's arm), not host-wide, following
 *   [net.bible.sharedui.ai.nav.AiModelsDeps.onResume]'s established convention: one host now serves
 *   several clusters, and a plan sync must not `recreate()` the host while an unrelated cluster's
 *   destination is the one showing.
 * - [onShowStartDatePicker] and [onImportPlan] are the two hard platform dependencies: an Android
 *   `DatePickerDialog` (`DailyReadingComposeActivity.kt:245-256`) and a SAF `GetContent()` launcher
 *   chained into `Screen.InstallZip` (`:103`, `:268-271`). Both stay host-side as plain lambdas,
 *   exactly like slice 1's `AiPromptsDeps.onImportCsv`/`onExportCsv`, and this graph binds the
 *   screen straight to them so the platform seam is visible here rather than hidden behind a
 *   controller pass-through.
 * - [onPlanMissing] is classic's two-step "no plan" behaviour, which cannot be a plain `() -> Unit`
 *   here. Classic showed the selector from `onCreate` and, when the selector returned without a
 *   pick, ran `if (!readingPlanControl.isReadingPlanSelected) finish()`. In the graph BOTH steps
 *   run through this destination's [loadDay], because its composition re-enters (and so re-loads)
 *   every time the selector pops — so something must remember that the selector has already been
 *   offered once, or Up out of the selector would bounce the user straight back into it forever.
 *   That memory cannot be a `remember` in this arm (disposed while the child is on top) and
 *   `rememberSaveable` is not on `:sharedUi`'s `commonMain` classpath, so it lives on the host,
 *   whose lifetime spans the whole cluster: this lambda returns true when the caller should offer
 *   the selector and false when the host has already offered it once and is now leaving.
 * - [title] is the WINDOW title (classic's `android:label="@string/rdg_plan_title"`), not an
 *   on-screen one — [DailyReadingScreen] draws the plan name and day in its own top bar.
 */
class DailyReadingDeps(
    val controllerFor: (onChangePlan: () -> Unit, onChangeDay: () -> Unit) -> DailyReadingController,
    val loadDay: (plan: String?, day: Int?) -> DailyReadingLoad,
    val loaded: StateFlow<LoadedReadingDay?>,
    val subscribeEvents: () -> () -> Unit,
    val onShowStartDatePicker: () -> Unit,
    val onImportPlan: () -> Unit,
    val onPlanMissing: () -> Boolean,
    val title: String,
)

/**
 * [DailyReadingListScreen]'s platform-supplied slots. [controllerFor] takes this graph's own
 * "day picked" lambda (which publishes to [ReadingPlanNavDeps.pendingSelection] and pops) and
 * builds the [DailyReadingListController] around it — same shape as
 * [net.bible.sharedui.ai.nav.RawLogHistoryDeps.controllerFor]. [subscribeEvents] is classic's
 * `onMain<ReadingPlansUpdatedViaSyncEvent> { controller.load() }`: it takes the reload to run and
 * returns the unsubscribe, so the host never needs a reference to the controller the graph owns.
 * [title] is both the screen's own top-bar title and its window title (classic's
 * `getString(R.string.rdg_plan_title)` plus the identical `android:label`).
 */
class DayListDeps(
    val controllerFor: (onSelect: (day: Int) -> Unit) -> DailyReadingListController,
    val subscribeEvents: (onReadingPlansChanged: () -> Unit) -> () -> Unit,
    val title: String,
)

/**
 * [ReadingPlanSelectorScreen]'s platform-supplied slots. Same shape as [DayListDeps]; the host's
 * `onSelect` wrapper keeps classic's "the plan vanished (sync) — ignore the tap rather than crash"
 * guard and its `readingPlanControl.startReadingPlan(dto)` call, and only then calls back into the
 * graph's lambda with the code. [title] is `R.string.rdg_plan_selector_title`, screen and window.
 */
class SelectorDeps(
    val controllerFor: (onSelect: (planCode: String) -> Unit) -> ReadingPlanSelectorController,
    val subscribeEvents: (onReadingPlansChanged: () -> Unit) -> () -> Unit,
    val title: String,
)

/**
 * Platform-supplied slots the reading-plan destinations need but `commonMain` cannot provide.
 * Mirrors [net.bible.sharedui.ai.nav.AiNavDeps]: [exitHost] and [setWindowTitle] are graph-wide and
 * sit at the top level, one nested holder per destination below.
 */
class ReadingPlanNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack), which is not the same thing as the on-screen
     * top-bar title a screen draws for itself. One host now serves four clusters, so a static
     * `android:label` in the manifest cannot be right for every destination — see plan D2. Called
     * from each destination's `LaunchedEffect(title)` — keyed on the VALUE, so a state-derived
     * title cannot go stale — and never from a screen composable: screen signatures are frozen for
     * this migration.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * Reports the route that `HistoryManager` should re-launch for the CURRENT reading-plan day, or
     * null when this cluster is not the visible one. The host turns it into the intent it stores —
     * see `NavHostComposeActivity.intentForHistoryList`. Without this, a history entry re-opens the
     * cluster's argument-free route and lands on the wrong day.
     *
     * Slices 5 and 6 reuse this seam unchanged: four search destinations declare
     * `integrateWithHistoryManager = true` and mutate their own intent the same way classic
     * `DailyReadingComposeActivity` did. Pair every call with [clearHistoryRoute] on the way out —
     * see that field for the ordering hazard a plain `setHistoryRoute(null)` walks into.
     */
    val setHistoryRoute: (String?) -> Unit,
    /**
     * Clears the history route, but ONLY if it is still the one this destination published —
     * a compare-and-clear, not a plain clear.
     *
     * navigation-compose composes the ENTERING destination (and runs its `LaunchedEffect`s) before
     * it disposes the exiting one. So on any A -> B where both write a history route, a plain
     * `onDispose { setHistoryRoute(null) }` on A runs AFTER B has already published its own route,
     * and wipes it: B ends up showing with `isIntegrateWithHistoryManager == false` and no gate
     * anywhere sees it. Slice 3 never hits it (neither child destination writes a route), but
     * slice 5's `Search -> SearchResults` and `EpubSearch -> EpubSearchResults` are exactly that
     * shape, so the seam is built correctly here rather than four copies later.
     */
    val clearHistoryRoute: (expected: String) -> Unit,
    /**
     * Child -> parent channel replacing two `setResult(Intent(<value as action>))` round trips —
     * see [ReadingPlanSelection]. Created by the HOST, not `remember`ed in a destination's arm:
     * the parent destination's composition is disposed while the child that writes to it is on top,
     * so anything scoped to that composition would be gone before the value could be read.
     */
    val pendingSelection: MutableStateFlow<ReadingPlanSelection?>,
    // — DAILY READING —
    val dailyReading: DailyReadingDeps,
    // — DAY LIST —
    val dayList: DayListDeps,
    // — READING PLAN SELECTOR —
    val selector: SelectorDeps,
)

/**
 * Whether an up-navigation attempt that just tried to pop the back stack should fall through to
 * exiting the host outright, given [popped] (`navController.popBackStack()`'s result). Split out
 * from [popOrExit] as a plain boolean-in function — rather than folded into it — so this branch is
 * unit-testable without a real `NavHostController`: that class requires an Android `Context` to
 * construct and has no lightweight fake, while `:sharedUi` has no Robolectric-style test runner,
 * only plain JUnit via `kotlin("test")`. `internal` rather than `private` keeps that door open
 * here too — but be honest about the state of it: the AI cluster's identical copy is the one
 * `AiNavGraphPopOrExitTest` covers, and THIS copy has no test of its own yet (a three-line mirror
 * test is all it needs; it was not added in Task 3 because `:sharedUi`'s test task was not one of
 * that task's two sanctioned gates).
 *
 * Duplicated from `AiNavGraph.kt` rather than shared: each cluster's graph file is self-contained,
 * and widening the AI cluster's copy into a cross-package utility would make an implementation
 * detail of that file part of `:sharedUi`'s surface.
 */
internal fun popOrExitOnFailedPop(popped: Boolean, exitHost: () -> Unit) {
    if (!popped) exitHost()
}

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly. All three
 * reading-plan screens can be entered directly (`Screen.ReadingPlan` from the main menu,
 * `Screen.ReadingPlanSelector`/`Screen.DailyReadingList` from `ScreenLauncher`), and all three are
 * also reachable as a child of `DAILY_READING_PATTERN` within this graph — so both branches are
 * live here.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

/**
 * The reading-plan cluster's three destinations. Registered into the app's single `NavHost` by the
 * host Activity.
 *
 * All inter-screen navigation lives HERE, not in the host — the host owns platform plumbing only.
 * The one cluster-specific rule to keep in mind when editing this file: the parent
 * `DAILY_READING_PATTERN` destination's composition does NOT survive a child being pushed on top
 * of it, so every piece of state that must cross that boundary ([ReadingPlanNavDeps.pendingSelection],
 * [DailyReadingDeps.loaded], [DailyReadingDeps.onPlanMissing]'s once-only flag) is owned by the
 * host, never by a `remember` in an arm.
 */
fun NavGraphBuilder.readingPlanNavGraph(navController: NavHostController, deps: ReadingPlanNavDeps) {
    composable(
        route = NavRoutes.DAILY_READING_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PLAN) { type = NavType.StringType; nullable = true; defaultValue = null },
            // A String, not NavType.IntType: an ABSENT optional Int has no representation this
            // pattern can express without inventing a sentinel, and classic's `extras.containsKey`
            // branch depends on absence staying distinguishable from any particular day number.
            navArgument(NavRoutes.ARG_DAY) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.dailyReading
        // Plan codes and day numbers are unreserved-character tokens (never free text), so — like
        // ARG_PROMPT_ID and the raw-log ids in AiNavGraph — neither is run through decodeArg.
        val plan = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_PLAN) }
        val day = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_DAY) }?.toIntOrNull()

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val controller = remember {
            d.controllerFor(
                { navController.navigate(NavRoutes.READING_PLAN_SELECTOR) },
                { navController.navigate(NavRoutes.READING_PLAN_DAY_LIST) },
            )
        }

        // Classic's ABEventBus.register/unregister pair, route-scoped — see DailyReadingDeps'
        // subscribeEvents kdoc for why this is not a host-wide registration.
        DisposableEffect(Unit) {
            val unsubscribe = d.subscribeEvents()
            onDispose { unsubscribe() }
        }

        val loaded by d.loaded.collectAsState()

        // THE HISTORY SEAM. HistoryManager stores `intentForHistoryList` at the moment the user
        // navigates away (HistoryManager.kt:172-175) and later re-launches it, so the stored route
        // must name the day actually on screen. Classic mutated its own intent with
        // ReadingPlanKeys.PLAN/DAY; there is no intent to mutate here, so the host builds one from
        // this route instead. Keyed on `loaded`, which the host updates on EVERY load path (route
        // arguments, a day/plan picked below, "Done" moving to the next day, the date picker) —
        // this is why the graph, not the host, is the single writer of the route.
        // `publishedRoute` is what THIS destination last put there, remembered so the clear below
        // can compare rather than wipe — see ReadingPlanNavDeps.clearHistoryRoute.
        val publishedRoute = remember { mutableStateOf<String?>(null) }
        LaunchedEffect(loaded) {
            val route = loaded?.let { NavRoutes.dailyReading(it.planCode, it.day) }
            val previous = publishedRoute.value
            publishedRoute.value = route
            if (route != null) deps.setHistoryRoute(route) else previous?.let(deps.clearHistoryRoute)
        }
        // Leaving this destination (Up, or another cluster's route in a recreated host) must clear
        // it: a null route is what tells the host to stop integrating with HistoryManager at all.
        // Compare-and-clear, never a bare setHistoryRoute(null): this runs AFTER the entering
        // destination's LaunchedEffect has already published its own route.
        DisposableEffect(Unit) {
            onDispose { publishedRoute.value?.let(deps.clearHistoryRoute) }
        }

        /** NO_PLAN -> classic's selector-then-finish pair; see DailyReadingDeps.onPlanMissing. */
        fun applyLoad(result: DailyReadingLoad) {
            if (result == DailyReadingLoad.NO_PLAN && d.onPlanMissing()) {
                navController.navigate(NavRoutes.READING_PLAN_SELECTOR)
            }
        }

        // The initial load, and the RE-load this destination needs every time a child pops (its
        // composition — and with it the controller above — was disposed meanwhile). Re-loading the
        // last loaded day rather than the route's own arguments is deliberate: a day picked from
        // the day list is not in this entry's arguments, so reloading the arguments would silently
        // revert the screen to the plan's current day.
        LaunchedEffect(plan, day) {
            if (deps.pendingSelection.value != null) return@LaunchedEffect // handled below instead
            val last = d.loaded.value
            applyLoad(if (last == null) d.loadDay(plan, day) else d.loadDay(last.planCode, last.day))
        }

        // Consume a selection the selector / day-list destination made before it popped.
        val pending by deps.pendingSelection.collectAsState()
        LaunchedEffect(pending) {
            when (val selection = pending) {
                is ReadingPlanSelection.Plan -> applyLoad(d.loadDay(selection.planCode, null))
                is ReadingPlanSelection.Day -> applyLoad(d.loadDay(d.loaded.value?.planCode, selection.day))
                null -> {}
            }
            if (pending != null) deps.pendingSelection.value = null
        }

        val ui by controller.ui.collectAsState()
        val speakState by controller.speakState.collectAsState()
        val error by controller.error.collectAsState()
        val confirm by controller.confirm.collectAsState()

        DailyReadingScreen(
            ui = ui,
            speakState = speakState,
            error = error,
            confirm = confirm,
            onToggleRead = controller::toggleRead,
            onRead = controller::read,
            onSpeak = controller::speak,
            onSpeakAll = controller::speakAll,
            onDone = controller::done,
            onPauseSpeak = controller::pauseSpeak,
            onStopSpeak = controller::stopSpeak,
            onChangePlan = controller::changePlan,
            onChangeDay = controller::changeDay,
            onSetCurrentDay = controller::requestSetCurrentDay,
            // Straight to the platform seams rather than through the controller's pass-throughs —
            // see DailyReadingDeps' kdoc: both need an Activity (a DatePickerDialog and a SAF
            // launcher), same shape as slice 1's onImportCsv/onExportCsv.
            onSetStartDate = d.onShowStartDatePicker,
            onReset = controller::requestReset,
            onImportPlan = d.onImportPlan,
            onConfirm = controller::confirm,
            onDismissConfirm = controller::dismissConfirm,
            onDismissError = controller::dismissError,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }
    composable(NavRoutes.READING_PLAN_DAY_LIST) {
        val d = deps.dayList
        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val controller = remember {
            d.controllerFor { day ->
                // Classic set the day as the result Intent's ACTION and finished; the parent
                // destination reads this instead — see ReadingPlanSelection.
                deps.pendingSelection.value = ReadingPlanSelection.Day(day)
                navController.popOrExit(deps.exitHost)
            }
        }
        DisposableEffect(controller) {
            val unsubscribe = d.subscribeEvents { controller.load() }
            onDispose { unsubscribe() }
        }

        val days by controller.days.collectAsState()
        val error by controller.error.collectAsState()

        DailyReadingListScreen(
            title = d.title,
            days = days,
            error = error,
            onSelect = controller::select,
            onDismissError = controller::dismissError,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }
    composable(NavRoutes.READING_PLAN_SELECTOR) {
        val d = deps.selector
        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val controller = remember {
            d.controllerFor { planCode ->
                // The host has already started the plan by the time this runs (classic's
                // startReadingPlan, kept there because it needs ReadingPlanControl) — see
                // SelectorDeps' kdoc.
                deps.pendingSelection.value = ReadingPlanSelection.Plan(planCode)
                navController.popOrExit(deps.exitHost)
            }
        }
        DisposableEffect(controller) {
            val unsubscribe = d.subscribeEvents { controller.load() }
            onDispose { unsubscribe() }
        }

        val plans by controller.plans.collectAsState()
        val duplicateWarning by controller.duplicateWarning.collectAsState()
        val error by controller.error.collectAsState()

        ReadingPlanSelectorScreen(
            title = d.title,
            plans = plans,
            duplicateWarning = duplicateWarning,
            error = error,
            onSelect = controller::select,
            onReset = controller::reset,
            onDismissError = controller::dismissError,
            onDismissDuplicate = controller::dismissDuplicateWarning,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }
}
