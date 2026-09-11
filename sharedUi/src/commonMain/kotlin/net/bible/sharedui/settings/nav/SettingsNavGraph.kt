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
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.SearchModeController
import net.bible.sharedcore.settings.AppSettingsController
import net.bible.sharedcore.settings.AppSettingsNav
import net.bible.sharedcore.settings.SyncSettingsController
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.settings.AppSettingsScreen
import net.bible.sharedui.settings.SyncSettingsScreen

/**
 * [AppSettingsScreen]'s platform-supplied slots, ported from classic `SettingsComposeActivity`.
 *
 * - [controller] is built by the HOST (it needs the host's `lifecycleScope` and an
 *   `AppSettingsLabels` assembled from `strings.xml`) — same shape as
 *   [net.bible.sharedui.ai.nav.AiConnectionSettingsDeps.controller]. Its own `onNavigate`
 *   constructor lambda is left unused: two of this screen's seven navigation rows are destinations
 *   in the host's ONE graph, so the branching belongs in this file's `composable` arm, where a
 *   `navController` exists — see the `onNavigate` binding below.
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
 *   (`Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`) and a deliberate delayed crash.
 * - [onOpenReadingProgressSettings] is a host `ScreenLauncher.open` TODAY only because
 *   `Screen.ReadingProgressSettings` is not a destination yet. **Task 8 adds that destination to
 *   this very graph**; when it does, this slot should be deleted and the `READING_PROGRESS` arm of
 *   the `when` below become `navController.navigate(NavRoutes.READING_PROGRESS_SETTINGS)`, joining
 *   `SYNC` and `AI`.
 * - [resetContentDescription] is `R.string.reset_settings`, a frozen parameter of the screen.
 * - [onResume] is classic's `onResume { service.refresh() }` (`:139-144`), driven from a
 *   lifecycle-aware effect scoped to THIS destination (see the arm). Never host-wide: one host now
 *   serves four clusters, and a host-wide `onResume` would refresh settings while a search result
 *   is the thing on screen.
 */
class AppSettingsDeps(
    val controller: AppSettingsController,
    val maybeRecreate: (key: String) -> Unit,
    val onConfirmReset: () -> Unit,
    val onShowDiscreteHelp: () -> Unit,
    val onOpenTextDisplaySettings: () -> Unit,
    val onOpenReadingProgressSettings: () -> Unit,
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
 *   IS an `ActivityBase`, so the classic construction carries over verbatim. The controller's own
 *   `onOpenCloudDocuments` stays where classic put it — `Screen.CloudDocuments` has no destination
 *   in any graph, so there is nothing for this file to route to and no reason to lift the branch
 *   out of [SyncSettingsController.onNavigate].
 * - [onResume] is classic's `onResume { service.refresh() }` (`:77-81`) — a sign-in/out or a
 *   `DocumentSyncSettings` change may have happened while this destination was not the visible
 *   one. Per-destination and lifecycle-aware, for the reason given on [AppSettingsDeps.onResume].
 */
class SyncSettingsDeps(
    val controller: SyncSettingsController,
    val onResume: () -> Unit,
)

/**
 * Platform-supplied slots the settings destinations need but `commonMain` cannot provide.
 * Mirrors [net.bible.sharedui.search.nav.SearchNavDeps]: [exitHost] and [setWindowTitle] are
 * graph-wide and sit at the top level, one nested holder per destination below.
 *
 * **Two holders now, four when the cluster is complete.** Task 7 declared [appSettings] and
 * [syncSettings]; **Task 8 appends `readingProgress` and `readingProgressSettings` to the END of
 * this constructor** (a holder cannot exist before the destination it supplies — the host would
 * have nothing to construct it from). Nothing above needs to move for that: the two top-level
 * slots stay first, the holders stay in destination order, and the only other Task 8 edit inside
 * this file is the one noted on [AppSettingsDeps.onOpenReadingProgressSettings].
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
)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// Up-navigation
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * Whether an up-navigation attempt that just tried to pop the back stack should fall through to
 * exiting the host outright, given [popped] (`navController.popBackStack()`'s result).
 *
 * Duplicated from `AiNavGraph.kt` / `ReadingPlanNavGraph.kt` / `SearchNavGraph.kt` rather than
 * shared: each cluster's graph file is self-contained, and widening one copy into a cross-package
 * utility would make an implementation detail of that file part of `:sharedUi`'s surface.
 * `internal` rather than `private` for the same reason as the other copies — it keeps a plain-JUnit
 * mirror test possible without a real `NavHostController` (which needs an Android `Context` to
 * construct, and `:sharedUi` has no Robolectric-style runner). As in slices 3 and 5, THIS copy has
 * no test of its own yet; `AiNavGraphPopOrExitTest` covers the AI cluster's identical one.
 */
internal fun popOrExitOnFailedPop(popped: Boolean, exitHost: () -> Unit) {
    if (!popped) exitHost()
}

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly. Both branches are
 * live here: app settings is entered directly from the main menu (`MenuCommandHandler.kt:182-186`)
 * and sync settings both directly (`MainBibleActivity.kt:796`, `MenuCommandHandler.kt:325-327`) and
 * as a child of app settings' `sync_settings_shortcut` row.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The settings cluster's destinations — [NavRoutes.SETTINGS] and [NavRoutes.SYNC_SETTINGS] as of
 * Task 7, plus reading progress and its settings screen in Task 8. Registered into the app's single
 * `NavHost` by the host Activity.
 *
 * Neither destination takes a navigation argument, so nothing in this file reads
 * `backStackEntry.arguments` — and when Task 8's `READING_PROGRESS_PATTERN` arm does, remember the
 * rule the search cluster documents at length: **the navigation library has ALREADY percent-decoded
 * a query value by the time it reaches `arguments`**, so `NavRoutes.decodeArg` must NOT be applied
 * to one a second time.
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
        val state by d.controller.state.collectAsState()

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
        // PlatformBackHandler — so the state belongs where it is used: in this arm, per back-stack
        // entry, disposed with the destination.
        var searchQuery by remember { mutableStateOf("") }
        val searchMode = remember { SearchModeController(onClearQuery = { searchQuery = "" }) }
        val searchModeActive by searchMode.active.collectAsState()

        // Classic's `onBackPressed` override (`:133-137`): Back closes the top bar's inline search
        // field instead of leaving the screen. Enabled ONLY while search mode is active, so every
        // other Back press falls through to the NavHost's own handler exactly as before.
        PlatformBackHandler(enabled = searchModeActive) { searchMode.close() }

        AppSettingsScreen(
            state = state,
            onUp = { navController.popOrExit(deps.exitHost) },
            // The controller write FIRST, then the recreate check — classic's order. See
            // AppSettingsDeps.maybeRecreate.
            onSwitch = { key, checked -> d.controller.onSwitch(key, checked); d.maybeRecreate(key) },
            onListChoice = { key, value -> d.controller.onListChoice(key, value); d.maybeRecreate(key) },
            onTextInput = d.controller::onTextInput,
            onSliderChange = d.controller::onSliderChange,
            onMultiSelectChange = d.controller::onMultiSelectChange,
            // Bound to this `when` rather than to controller::onNavigate (whose host lambda is left
            // unused), for AiNavGraph's AI_CONNECTION_SETTINGS reason: SYNC and AI are routes in
            // this host's graph and cannot be decided from :sharedCore. The other five rows have no
            // destination — a still-classic Activity, two platform dialogs/system screens, a crash
            // — and stay host callbacks.
            onNavigate = { key ->
                when (key) {
                    AppSettingsNav.SYNC -> navController.navigate(NavRoutes.SYNC_SETTINGS)
                    AppSettingsNav.AI -> navController.navigate(NavRoutes.AI_PROMPTS)
                    // Task 8 turns this into navigate(NavRoutes.READING_PROGRESS_SETTINGS).
                    AppSettingsNav.READING_PROGRESS -> d.onOpenReadingProgressSettings()
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
        val uiState by d.controller.state.collectAsState()

        // As above: the screen draws uiState.screen.title, and the classic host's manifest label
        // (`android:label="@string/cloud_sync_title"`) was the same string.
        LaunchedEffect(uiState.screen.title) { deps.setWindowTitle(uiState.screen.title) }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume() }

        SyncSettingsScreen(
            uiState = uiState,
            onUp = { navController.popOrExit(deps.exitHost) },
            onSwitch = d.controller::onSwitch,
            onListChoice = d.controller::onListChoice,
            onTextInput = d.controller::onTextInput,
            // Straight to the controller, unlike the app-settings arm: this screen's only
            // navigation row (`document_sync_manage`) opens Screen.CloudDocuments, which has no
            // destination in any graph — see SyncSettingsDeps.controller.
            onNavigate = d.controller::onNavigate,
            onConfirmReset = d.controller::confirmReset,
            onConfirmEnableDocuments = d.controller::confirmEnableDocuments,
            onDismissDialog = d.controller::dismissDialog,
        )
    }
}
