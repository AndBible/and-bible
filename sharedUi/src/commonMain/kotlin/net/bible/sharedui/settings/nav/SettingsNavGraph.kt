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

package net.bible.sharedui.settings.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedcore.progress.PassageRow
import net.bible.sharedcore.progress.ReadingProgressController
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedcore.progress.TargetRow
import net.bible.sharedcore.search.SearchModeController
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsNav
import net.bible.sharedcore.settings.ReadingProgressSettingsController
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop
import net.bible.sharedui.progress.AbReadHistorySheet
import net.bible.sharedui.progress.MemorizeTabBody
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.progress.ReadingProgressScreen
import net.bible.sharedui.settings.AbSettingsScreen
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.SyncSettingsScreen
import net.bible.sharedui.strings.LocalStrings

/**
 * [AppSettingsScreen]'s platform-supplied slots, ported from classic `SettingsComposeActivity`.
 *
 * - [controller] is built by the HOST (it needs the host's `lifecycleScope` and an
 *   `AppSettingsLabels` assembled from `strings.xml`) but reached through a GETTER, not handed over
 *   as an instance — and that distinction is the expensive one on this screen. Building this
 *   controller forces `AppSettingsServiceImpl`, whose constructor does four JSword
 *   `Books.installed()` dictionary scans and ~51 `CommonUtils.settings` reads (each a Room query,
 *   on the main thread), then builds the whole `SettingsScreenState` item tree and launches a
 *   permanent snapshot collector. The host now serves four clusters, so an eagerly constructed
 *   controller would charge all of that to someone opening a SEARCH or reading-plan screen who
 *   never touches settings — a cost classic paid only when Settings itself opened. The host backs
 *   this getter with a `by lazy`, so it is still built at most once per host (NOT once per
 *   back-stack entry, which is what the reading-plan/search clusters' `controllerFor` factories
 *   deliberately do): the arm below `remember`s the result, and a destination that re-enters
 *   composition after a child pops gets the same instance, with its state intact.
 *
 *   Its own `onNavigate` constructor lambda is left unused: two of this screen's seven navigation
 *   rows are destinations in the host's ONE graph, so the branching belongs in this file's
 *   `composable` arm, where a `navController` exists — see the `onNavigate` binding below.
 * - [maybeRecreate] is classic's `maybeRecreate(key)` (`SettingsComposeActivity.kt:148-150`): a
 *   write to `locale_pref` / `night_mode_pref3` / `display_color_mode` / `discrete_mode` forces an
 *   Activity `recreate()`, because none of those is observed reactively — the locale is attached in
 *   `attachBaseContext` and the three theme values are read once per composition. It stays a host
 *   lambda both because `recreate()` is an Android call and because plan D5 keeps the behaviour
 *   deliberately: on the nav host it now recreates EVERY destination and rebuilds the back stack,
 *   not just the screen that asked for it. Task 10 records that as an allowed behaviour change.
 *   Called AFTER the controller's write, exactly as classic ordered it, and from the two row kinds
 *   classic wired it to (switch and list choice) and no others.
 * - [onConfirmReset] and [onShowDiscreteHelp] are the screen's two platform `AlertDialog`s
 *   (`:245-254` reset confirmation, `:189-216` the persecution-help HTML dialog whose link needs a
 *   `LinkMovementMethod`). They stay PLATFORM dialogs on purpose: a separately specified
 *   platform-dialog-removal phase owns them, and converting either here would move a Roborazzi
 *   golden, which this migration is not allowed to do. [onConfirmReset] is the whole classic
 *   chain — confirm, `SettingsReset.performReset()`, `service.refresh()`, `recreate()`.
 * - [onOpenTextDisplaySettings], [onOpenLinksSettings] and [onCrashApp] are the three navigation
 *   rows with no destination in this graph: an Activity that is still an Activity (it is launched
 *   with a `settingsBundle` extra), the Android app-links system screen
 *   (`Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`) and a deliberate delayed crash. The
 *   reading-progress-settings row used to be a fourth; Task 8 made it a destination in THIS graph,
 *   so it navigates directly and its host slot is gone.
 * - [resetContentDescription] is `R.string.reset_settings`, a frozen parameter of the screen.
 * - [onResume] is classic's `onResume { service.refresh() }` (`:139-144`), driven from a
 *   lifecycle-aware effect scoped to THIS destination (see the arm). Never host-wide: one host now
 *   serves four clusters, and a host-wide `onResume` would refresh settings while a search result
 *   is the thing on screen.
 */
class AppSettingsDeps(
    val controller: () -> AppSettingsController,
    val maybeRecreate: (key: String) -> Unit,
    val onConfirmReset: () -> Unit,
    val onShowDiscreteHelp: () -> Unit,
    val onOpenTextDisplaySettings: () -> Unit,
    val onOpenLinksSettings: () -> Unit,
    val onCrashApp: () -> Unit,
    val resetContentDescription: String,
    val onResume: () -> Unit,
)

/**
 * [SyncSettingsScreen]'s platform-supplied slots, ported from classic `SyncSettingsComposeActivity`
 * — the batch's thinnest holder, because everything this screen does beyond rendering is already
 * inside [SyncSettingsController].
 *
 * - [controller] is host-built for a reason sharper than the usual `lifecycleScope` one: its
 *   service is `SyncSettingsServiceImpl(scope, activityProvider = { this })`, and that provider
 *   must return an `ActivityBase` (`CloudSync.signIn` requires one, not a `Context`). The nav host
 *   IS an `ActivityBase`, so the classic construction carries over verbatim. It is a GETTER backed
 *   by a host `by lazy` for [AppSettingsDeps.controller]'s reason — building it runs
 *   `SyncSettingsServiceImpl`'s eager `build()` (`CloudSync.signedIn`, the adapter summaries) and
 *   ~35 `getString` calls for the labels, none of which another cluster's destination should pay
 *   for. The controller's own `onOpenCloudDocuments` stays where classic put it —
 *   `Screen.CloudDocuments` has no destination in any graph, so there is nothing for this file to
 *   route to and no reason to lift the branch out of [SyncSettingsController.onNavigate].
 * - [onResume] is classic's `onResume { service.refresh() }` (`:77-81`) — a sign-in/out or a
 *   `DocumentSyncSettings` change may have happened while this destination was not the visible
 *   one. Per-destination and lifecycle-aware, for the reason given on [AppSettingsDeps.onResume].
 */
class SyncSettingsDeps(
    val controller: () -> SyncSettingsController,
    val onResume: () -> Unit,
)

/**
 * One read-history dialog request: the sheet's title and the rows to show. Classic
 * `ReadingProgressComposeActivity` held the identical pair as a private `HistoryReq` data class
 * plus a `mutableStateOf` Activity FIELD (`:56-57`, `:84`), because its three `showXHistory`
 * functions are suspending (each hits the read-history DAO) and had nowhere else to put the answer.
 * The shape survives the move; only the owner changes — see [ReadingProgressDeps.controllerFor].
 *
 * [ReadHistoryRow] is already a `commonMain` type, so nothing Android crosses this boundary.
 */
class ReadHistoryRequest(val title: String, val rows: List<ReadHistoryRow>)

/**
 * [ReadingProgressScreen]'s platform-supplied slots, ported from classic
 * `ReadingProgressComposeActivity`.
 *
 * **This is the batch's one destination whose result leaves the batch**, and the whole edge lives
 * inside [controllerFor]'s `onResult` rather than in a slot of its own. Classic's `navigateToChapter`
 * (`:190-196`) and `navigateToMemorize` (`:200-208`) each built an `Intent`, `setResult(RESULT_OK, it)`
 * and `finish()` directly; `MainBibleActivity.kt:2930-2958` still consumes them by reading
 * `extras.getString(ActivityResultKind.EXTRA)` — NOT the result Intent's component class — which is
 * exactly what lets the edge survive the migration: the nav host sets a byte-identical result and
 * the consumer cannot tell which host produced it. Both are already constructor lambdas of
 * [ReadingProgressController] (`onNavigateToChapter`, `onNavigateToMemorize`), which the host now
 * wires to `onResult` rather than to the two `finish()`-ing functions directly — `onResult` hands a
 * [net.bible.sharedcore.nav.ReadingProgressResult] to this arm's [NavResultChannel], which is what
 * decides whether that means popping back to a parent entry or exiting the host with the packed
 * `Intent`; see [NavResultChannel]'s own kdoc. `onResult` is deliberately NOT a `(Intent) -> Unit`
 * deps slot: `android.content.Intent` is an Android type and this file is `commonMain`.
 *
 * - [controllerFor] is a lambda, so the deps literal the host assembles on EVERY launch, for every
 *   cluster, constructs nothing and opening a search screen never touches the read-history DAO —
 *   the laziness rule Task 7's fix round established. It is NOT a per-entry factory despite the
 *   name it shares with the reading-plan/search clusters': **the host must return the SAME
 *   controller when this arm re-composes** (Task 8 fix round 1). This destination covers itself —
 *   its overflow opens [NavRoutes.READING_PROGRESS_SETTINGS], a destination in this same graph —
 *   and navigation-compose DISPOSES a covered entry's composition, so a fresh controller on the way
 *   back would drop `model.chapterDetail`, the memorize tab's chapter detail and the "show more"
 *   paging counts. Classic's Activity merely PAUSED behind the settings Activity and kept all
 *   three. The arm's `remember` is therefore a cache of the host's cache, not the thing that owns
 *   the controller's lifetime. `tabArg` is still honoured on such a re-entry when it is non-null.
 *
 *   `tabArg` is the route's optional [NavRoutes.ARG_TAB], null when absent — and absent is a REAL
 *   state, not a missing argument: classic (`:76-82`) defaulted an absent
 *   `ReadingProgressKeys.EXTRA_TAB` to the persisted `reading_progress_last_tab` setting, so the
 *   host resolves null the same way. That resolution stays host-side because the persisted value is
 *   an Android `CommonUtils.settings` read.
 *
 *   `onShowHistory` is how the host's three suspending history loaders (classic `:212-239`) hand
 *   their answer back to the arm, which owns the dialog state — the inverse of classic, where the
 *   Activity owned the state and the composition merely read it.
 * - [persistTab] is classic's `persistTab` (`:264-266`), called from `onSelectTab` AFTER the
 *   controller's `selectTab`, exactly as classic ordered it.
 * - [onApplyHistoryDeletes] is classic's sheet callback (`:173-178`): a suspending
 *   `deleteReadHistoryEntries` followed by a controller refresh. Both the `cycle` and the refresh
 *   come from the arm's controller (`controller.model.value.cycle`, `controller::refresh` — classic
 *   read exactly those off its own field), so the host needs no reference to a per-entry object it
 *   does not own; `onDeleted` runs AFTER the suspending delete, as classic sequenced it.
 * - [onShowHelp] is `CommonUtils.showHelpDialog` (`:255-262`) — a platform dialog, left platform.
 * - [unmarkConfirmMessage] / [removeTargetConfirmMessage] are the two `AbConfirmDialog` bodies,
 *   each a `getString(..., row.rangeName)` format. Lambdas, not strings: the argument is only known
 *   when a row is tapped. [confirmText] / [dismissText] are `android.R.string.ok` / `cancel` —
 *   classic's own choice of the SYSTEM strings, kept rather than swapped for `LocalStrings`.
 */
class ReadingProgressDeps(
    val controllerFor: (
        tabArg: Int?,
        onShowHistory: (ReadHistoryRequest) -> Unit,
        onResult: (ReadingProgressResult) -> Unit,
    ) -> ReadingProgressController,
    val persistTab: (ReadingTab) -> Unit,
    val onApplyHistoryDeletes: (ids: List<String>, cycle: Int, onDeleted: () -> Unit) -> Unit,
    val onShowHelp: () -> Unit,
    val unmarkConfirmMessage: (rangeName: String) -> String,
    val removeTargetConfirmMessage: (rangeName: String) -> String,
    val confirmText: String,
    val dismissText: String,
)

/**
 * [AbSettingsScreen]'s platform-supplied slots for the reading-progress/memorization settings
 * screen — the batch's thinnest destination, and the batch's thinnest holder with it. Classic
 * `ReadingProgressSettingsComposeActivity` is a controller, an [AbSettingsScreen] call and nothing
 * else: no navigation rows, no reset action, no recreate parity, no `onResume` refresh.
 *
 * [controller] is a GETTER over a host `by lazy`, matching [AppSettingsDeps.controller] rather than
 * [ReadingProgressDeps.controllerFor]. The cost it defers is smaller (one
 * `ReadingProgressSettingsService` snapshot plus ~13 `getString`s) but the reason is the same one
 * Task 7's fix round established: the deps literal is assembled on every launch of a host that now
 * serves four clusters, so nothing settings-specific may be CONSTRUCTED there. `by lazy` rather
 * than a factory for [AppSettingsDeps.controller]'s reason — its snapshot collector should outlive
 * one back-stack entry's composition.
 */
class ReadingProgressSettingsDeps(
    val controller: () -> ReadingProgressSettingsController,
)

/**
 * Platform-supplied slots the settings destinations need but `commonMain` cannot provide.
 * Mirrors [net.bible.sharedui.search.nav.SearchNavDeps]: [exitHost] and [setWindowTitle] are
 * graph-wide and sit at the top level, one nested holder per destination below.
 *
 * **Four holders, one per destination, in destination order.** Task 7 declared [appSettings] and
 * [syncSettings]; Task 8 appended [readingProgress] and [readingProgressSettings] to the END of
 * this constructor (a holder cannot exist before the destination it supplies — the host would have
 * nothing to construct it from). The two top-level slots stay first; a fifth destination appends
 * likewise.
 *
 * There is deliberately NO history seam here (no `setHistoryRoute`/`clearHistoryRoute` pair, unlike
 * the reading-plan and search clusters): none of classic's four settings hosts declares
 * `integrateWithHistoryManager`, so none of these destinations may publish a history route.
 */
class SettingsNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) — not the on-screen top-bar title a screen
     * draws for itself. Both destinations here happen to want the same string for both, but it is
     * still read from the SCREEN STATE and pushed from the arm's `LaunchedEffect(title)`, keyed on
     * the VALUE so it cannot go stale — never from inside a screen composable, whose signature is
     * frozen for this migration.
     */
    val setWindowTitle: (String) -> Unit,
    // — APP SETTINGS —
    val appSettings: AppSettingsDeps,
    // — SYNC SETTINGS —
    val syncSettings: SyncSettingsDeps,
    // — READING PROGRESS —
    val readingProgress: ReadingProgressDeps,
    /**
     * [NavResultChannel] for [ReadingProgressResult] -- declared here, on the GRAPH-wide deps, per
     * [NavResultChannel]'s own kdoc: it is created by the HOST (`NavHostComposeActivity`) and
     * handed down, never `remember`ed by an arm, because an arm's composition is disposed before a
     * parent could read what it published.
     */
    val readingProgressResults: NavResultChannel<ReadingProgressResult>,
    // — READING PROGRESS SETTINGS —
    val readingProgressSettings: ReadingProgressSettingsDeps,
)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// Up-navigation
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly. Both branches are
 * live here: app settings is entered directly from the main menu (`MenuCommandHandler.kt:182-186`)
 * and sync settings both directly (`MainBibleActivity.kt:796`, `MenuCommandHandler.kt:325-327`) and
 * as a child of app settings' `sync_settings_shortcut` row.
 *
 * The boolean branch itself is [net.bible.sharedui.nav.popOrExitOnFailedPop] — ONE `internal`
 * helper shared by all four cluster graphs and tested there (whole-branch review M1, which
 * retired four byte-identical copies covered by a single test). What stays here is the part that
 * is genuinely cluster-specific: which of its two branches is live for THIS cluster.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The settings cluster's four destinations — [NavRoutes.SETTINGS], [NavRoutes.SYNC_SETTINGS],
 * [NavRoutes.READING_PROGRESS_PATTERN] and [NavRoutes.READING_PROGRESS_SETTINGS]. Registered into
 * the app's single `NavHost` by the host Activity.
 *
 * Exactly one of them takes a navigation argument ([NavRoutes.ARG_TAB]), and it obeys the rule the
 * search cluster documents at length: **the navigation library has ALREADY percent-decoded a query
 * value by the time it reaches `arguments`**, so `NavRoutes.decodeArg` must NOT be applied to one a
 * second time.
 *
 * All inter-screen navigation lives HERE rather than in the host: two of app settings' seven
 * navigation rows (`sync_settings_shortcut`, `ai_settings_shortcut`) address destinations in this
 * same `NavHost` — the AI cluster's routes are registered into it alongside these — so they
 * navigate directly instead of round-tripping through `ScreenLauncher` and the host's
 * `onNewIntent`. The remaining rows have no destination at all and stay host lambdas; see
 * [AppSettingsDeps].
 */
fun NavGraphBuilder.settingsNavGraph(navController: NavHostController, deps: SettingsNavDeps) {
    // ——— APP SETTINGS ———
    composable(NavRoutes.SETTINGS) {
        val d = deps.appSettings
        // The host's lazy getter: one instance per HOST, resolved here rather than when the deps
        // were assembled, so another cluster's destination never pays for building it. See
        // AppSettingsDeps.controller.
        val controller = remember { d.controller() }
        val state by controller.state.collectAsState()

        // The screen's own top bar renders `state.title` (AbSettingsScreen's AbScaffold), so that
        // is the value the window title follows too — and it equals the manifest label
        // (`android:label="@string/settings"`) the classic host carried, which one static label on
        // a host serving four clusters could no longer be right for.
        LaunchedEffect(state.title) { deps.setWindowTitle(state.title) }

        // Classic's onResume -> service.refresh(): a pull-based refresh for changes made elsewhere
        // (installed dictionaries, say) while this screen was not the visible one. A lifecycle-aware
        // effect keyed to THIS destination — see AppSettingsDeps.onResume.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume() }

        // Classic hoisted these two onto the ACTIVITY (`SettingsComposeActivity.kt:99-103`) purely
        // so its onBackPressed override could reach them, with a comment that :sharedUi is
        // commonMain and cannot use BackHandler. That is no longer true — slice 1 added
        // PlatformBackHandler — so the state belongs where it is used: in this arm.
        //
        // rememberSaveable, NOT remember, and that is not a process-death nicety: this arm's
        // composition is DISPOSED while a child destination (sync settings, the AI cluster) sits on
        // top of it, so a plain `remember` would drop the user's filter every time they tapped a
        // navigation row and came back — something classic, whose Activity was merely paused, never
        // did. NavHost wraps each entry in a SaveableStateHolder, which is exactly what makes
        // rememberSaveable survive that disposal. SearchModeController keeps its `active` flag in a
        // StateFlow (it is shared :sharedCore policy, not a Compose type), so the saveable half is a
        // plain Boolean mirror: read back when the controller is rebuilt, and kept in step below.
        var searchQuery by rememberSaveable { mutableStateOf("") }
        var searchWasActive by rememberSaveable { mutableStateOf(false) }
        val searchMode = remember {
            SearchModeController(onClearQuery = { searchQuery = "" })
                .also { if (searchWasActive) it.open() }
        }
        val searchModeActive by searchMode.active.collectAsState()
        LaunchedEffect(searchModeActive) { searchWasActive = searchModeActive }

        // Classic's `onBackPressed` override (`:133-137`): Back closes the top bar's inline search
        // field instead of leaving the screen. Enabled ONLY while search mode is active, so every
        // other Back press falls through to the NavHost's own handler exactly as before.
        PlatformBackHandler(enabled = searchModeActive) { searchMode.close() }

        AppSettingsScreen(
            state = state,
            onUp = { navController.popOrExit(deps.exitHost) },
            // The controller write FIRST, then the recreate check — classic's order. See
            // AppSettingsDeps.maybeRecreate.
            onSwitch = { key, checked -> controller.onSwitch(key, checked); d.maybeRecreate(key) },
            onListChoice = { key, value -> controller.onListChoice(key, value); d.maybeRecreate(key) },
            onTextInput = controller::onTextInput,
            onSliderChange = controller::onSliderChange,
            onMultiSelectChange = controller::onMultiSelectChange,
            // Bound to this `when` rather than to controller::onNavigate (whose host lambda is left
            // unused), for AiNavGraph's AI_CONNECTION_SETTINGS reason: SYNC and AI are routes in
            // this host's graph and cannot be decided from :sharedCore. The other five rows have no
            // destination in any graph — ONE still-classic Activity launch (text display), a
            // platform dialog, an Android system screen and a deliberate crash — and stay host
            // callbacks.
            onNavigate = { key ->
                when (key) {
                    AppSettingsNav.SYNC -> navController.navigate(NavRoutes.SYNC_SETTINGS)
                    AppSettingsNav.AI -> navController.navigate(NavRoutes.AI_PROMPTS)
                    AppSettingsNav.READING_PROGRESS ->
                        navController.navigate(NavRoutes.READING_PROGRESS_SETTINGS)
                    AppSettingsNav.TEXT_DISPLAY -> d.onOpenTextDisplaySettings()
                    AppSettingsNav.DISCRETE_HELP -> d.onShowDiscreteHelp()
                    AppSettingsNav.OPEN_LINKS -> d.onOpenLinksSettings()
                    AppSettingsNav.CRASH_APP -> d.onCrashApp()
                }
            },
            onReset = d.onConfirmReset,
            resetContentDescription = d.resetContentDescription,
            searchQuery = searchQuery,
            searchModeActive = searchModeActive,
            onSearchQueryChange = { searchQuery = it },
            onOpenSearch = searchMode::open,
            onCloseSearch = searchMode::close,
        )
    }

    // ——— SYNC SETTINGS ———
    composable(NavRoutes.SYNC_SETTINGS) {
        val d = deps.syncSettings
        val controller = remember { d.controller() }
        val uiState by controller.state.collectAsState()

        // As above: the screen draws uiState.screen.title, and the classic host's manifest label
        // (`android:label="@string/cloud_sync_title"`) was the same string.
        LaunchedEffect(uiState.screen.title) { deps.setWindowTitle(uiState.screen.title) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume() }

        SyncSettingsScreen(
            uiState = uiState,
            onUp = { navController.popOrExit(deps.exitHost) },
            onSwitch = controller::onSwitch,
            onListChoice = controller::onListChoice,
            onTextInput = controller::onTextInput,
            // Straight to the controller, unlike the app-settings arm: this screen's only
            // navigation row (`document_sync_manage`) opens Screen.CloudDocuments, which has no
            // destination in any graph — see SyncSettingsDeps.controller.
            onNavigate = controller::onNavigate,
            onConfirmReset = controller::confirmReset,
            onConfirmEnableDocuments = controller::confirmEnableDocuments,
            onDismissDialog = controller::dismissDialog,
        )
    }

    // ——— READING PROGRESS ———
    composable(
        route = NavRoutes.READING_PROGRESS_PATTERN,
        arguments = listOf(
            // A String, not NavType.IntType, for DAILY_READING_PATTERN's reason: an ABSENT optional
            // Int has no representation this pattern can express without inventing a sentinel, and
            // absence must stay distinguishable from tab 0 — classic defaulted an absent extra to
            // the PERSISTED tab, which may well be 1.
            navArgument(NavRoutes.ARG_TAB) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.readingProgress
        // NOT run through NavRoutes.decodeArg: the navigation library has already percent-decoded
        // this query value by the time it reaches `arguments`, and a second pass would corrupt a
        // literal '%'. (A tab index cannot contain one, but the rule is the rule — see this file's
        // kdoc and AiNavGraph's long-form version.)
        val tabArg = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_TAB) }?.toIntOrNull()

        // Classic's `historyDialog` Activity field (`:84`), moved into the arm — the state belongs
        // where it is rendered. `remember`, not `rememberSaveable`: it is a transient one-shot
        // request holding a loaded row list, and classic's plain Activity field did not survive
        // process death either. It IS dropped when this arm is disposed by the settings child,
        // unlike classic — accepted because that is unreachable: the sheet is a modal
        // ModalBottomSheet, so the overflow that opens settings cannot be tapped while it is up.
        var historyDialog by remember { mutableStateOf<ReadHistoryRequest?>(null) }

        // Nothing is constructed until this destination composes. `tabArg` is passed as-is, null
        // included: the host resolves an ABSENT tab to the persisted `reading_progress_last_tab`,
        // exactly as classic's getIntExtra default did.
        //
        // The `remember` is NOT what keeps this controller alive — see ReadingProgressDeps
        // .controllerFor. This arm is disposed while the reading-progress SETTINGS child sits on
        // top of it, and the host returns the same controller when it re-composes, which is what
        // preserves the open chapter-detail panel and the memorize "show more" paging across that
        // round trip. The history-dialog setter below is re-pointed by the host on every call, so
        // a loader started by the previous composition still reaches the live one.
        val controller = remember {
            d.controllerFor(
                tabArg,
                { request -> historyDialog = request },
                { deps.readingProgressResults.deliver(navController, it) },
            )
        }

        // Classic's manifest label was `android:label="@string/reading_progress_title"`, and the
        // screen's own top bar draws LocalStrings.readingProgressTitle — the same string. Keyed on
        // the VALUE, per this graph's window-title convention.
        val windowTitle = LocalStrings.current.readingProgressTitle
        LaunchedEffect(windowTitle) { deps.setWindowTitle(windowTitle) }

        // Classic ran `controller.load()` once at the end of onCreate (`:185`). This runs once per
        // COMPOSITION of the entry, so it also re-runs when the reading-progress-settings child
        // pops — deliberately kept: it is what makes a just-changed memorization setting visible
        // immediately, and it is not a state loss, because `load()` preserves the open
        // chapter-detail book id and `loadMemorize()` preserves `memChapterDetail` and the paging
        // counts off the SURVIVING controller.
        LaunchedEffect(Unit) { controller.load() }

        val model by controller.model.collectAsState()
        val loading by controller.loading.collectAsState()
        var unmarkRow by remember { mutableStateOf<PassageRow?>(null) }
        var removeRow by remember { mutableStateOf<TargetRow?>(null) }

        ReadingProgressScreen(
            model = model,
            loading = loading,
            // Classic's `finish()`. popOrExit because this destination is normally entered directly
            // (BibleJavascriptInterface) but may also sit on a back stack one day; a bare
            // popBackStack() would be a dead up-arrow in the direct case.
            onUp = { navController.popOrExit(deps.exitHost) },
            onSelectTab = { controller.selectTab(it); d.persistTab(it) },
            onPrevCycle = controller::prevCycle,
            onNextCycle = controller::nextCycle,
            onNewCycle = controller::newCycle,
            onBookClick = controller::openChapterDetail,
            onBookLongClick = controller::bookLongPress,
            onChapterClick = { chapter -> model.chapterDetail?.let { controller.chapterTap(it.bookId, chapter) } },
            onChapterLongClick = { ch -> model.chapterDetail?.let { controller.chapterLongPress(it.bookId, ch) } },
            onCalendarDayClick = controller::calendarDayTap,
            // Classic opened this with ScreenLauncher.open(Screen.ReadingProgressSettings); it is a
            // destination in THIS graph now, so it navigates directly — the SYNC/AI precedent.
            onOpenSettings = { navController.navigate(NavRoutes.READING_PROGRESS_SETTINGS) },
            onShowHelp = d.onShowHelp,
            memorizeTabContent = {
                val m = model.memorize
                if (m == null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    MemorizeTabBody(
                        memorize = m,
                        onSetOverview = controller::setMemOverview,
                        onBookClick = controller::openMemChapterDetail,
                        onChapterClick = { ch -> m.memChapterDetail?.let { controller.chapterTap(it.bookId, ch) } },
                        onCalendarDayClick = {},
                        onPassageTap = controller::memorizePassageTap,
                        onPassageUnmark = { row -> unmarkRow = row },
                        onTargetTap = controller::memorizePassageTap,
                        onTargetRemove = { row -> removeRow = row },
                        onShowMorePassages = controller::showMorePassages,
                        onShowMoreTargets = controller::showMoreTargets,
                    )
                }
            },
        )

        unmarkRow?.let { row ->
            AbConfirmDialog(
                title = null,
                message = d.unmarkConfirmMessage(row.rangeName),
                confirmText = d.confirmText,
                dismissText = d.dismissText,
                onConfirm = { controller.unmarkPassage(row.startOrdinal, row.endOrdinal); unmarkRow = null },
                onDismiss = { unmarkRow = null },
            )
        }
        removeRow?.let { row ->
            AbConfirmDialog(
                title = null,
                message = d.removeTargetConfirmMessage(row.rangeName),
                confirmText = d.confirmText,
                dismissText = d.dismissText,
                onConfirm = { controller.removeTarget(row.id); removeRow = null },
                onDismiss = { removeRow = null },
            )
        }

        historyDialog?.let { req ->
            AbReadHistorySheet(
                title = req.title,
                rows = req.rows,
                // The cycle is read at APPLY time from the live model, as classic read
                // `controller.model.value.cycle` inside its own lambda.
                onApplyDeletes = { ids ->
                    d.onApplyHistoryDeletes(ids, controller.model.value.cycle, controller::refresh)
                },
                onDismiss = { historyDialog = null },
            )
        }
    }

    // ——— READING PROGRESS SETTINGS ———
    composable(NavRoutes.READING_PROGRESS_SETTINGS) {
        val d = deps.readingProgressSettings
        val controller = remember { d.controller() }
        val state by controller.state.collectAsState()

        // Same convention as the two Task 7 arms: the screen draws state.title, and that string
        // (`@string/reading_progress_settings`) is what the classic host's manifest label carried.
        LaunchedEffect(state.title) { deps.setWindowTitle(state.title) }

        AbSettingsScreen(
            state = state,
            onUp = { navController.popOrExit(deps.exitHost) },
            onSwitch = controller::onSwitch,
            onListChoice = controller::onListChoice,
            // Classic's two no-op slots (`:64-65`): this screen has no text input and no
            // navigation row, so both stay empty rather than reaching the controller.
            onTextInput = { _, _ -> },
            onNavigate = { _ -> },
        )
    }
}
