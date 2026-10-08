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

package net.bible.sharedui.ai.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.bible.sharedcore.ai.AiConnectionNav
import net.bible.sharedcore.ai.AiConnectionSettingsController
import net.bible.sharedcore.ai.AiDocumentFilterController
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.AiPromptsController
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedcore.ai.PromptCategoryVd
import net.bible.sharedcore.ai.PromptEditController
import net.bible.sharedcore.ai.RawLlmLogController
import net.bible.sharedcore.ai.RawLogHistoryController
import net.bible.sharedcore.ai.ToolCategoryVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.ai.AiConnectionSettingsScreen
import net.bible.sharedui.ai.AiDocumentFilterScreen
import net.bible.sharedui.ai.AiModelsScreen
import net.bible.sharedui.ai.AiProvidersScreen
import net.bible.sharedui.ai.EasySetupState
import net.bible.sharedui.ai.EasySetupStep
import net.bible.sharedui.ai.EasySetupTestResult
import net.bible.sharedui.ai.EasySetupWizard
import net.bible.sharedui.ai.GlobalToolPermissionsScreen
import net.bible.sharedui.ai.AiPromptsScreen
import net.bible.sharedui.ai.PromptEditScreen
import net.bible.sharedui.ai.RawLlmLogScreen
import net.bible.sharedui.ai.RawLogHistoryScreen
import net.bible.sharedui.ai.ToolInfoScreen
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.nav.popOrExitOnFailedPop
import net.bible.sharedui.strings.LocalStrings

/**
 * [ToolInfoScreen]'s platform-supplied slots. One such holder per destination, nested under
 * [AiNavDeps] — kept small and grouped rather than flattened, because at ten destinations a flat
 * [AiNavDeps] would mix ~40 fields (plain data, per-item lambdas, Task 9's suspend lambdas) in one
 * namespace with nothing but a naming convention telling them apart.
 *
 * **Why [readTools]/[writeTools]/[helpBody] are `() -> …` getters rather than plain values — the
 * convention the whole AI cluster follows, stated once here** (whole-branch review I2). An
 * `AiNavDeps` literal is assembled on EVERY launch of the host, which since slices 3/5/6 also serves
 * Search, Reading plan, Reading progress and Settings. Anything spelled as a value in that literal
 * is therefore computed on the main thread before the first frame of screens that have nothing to do
 * with AI: `ToolRegistry.getAllTools()` plus two `map`/`filter` passes for the two tool lists, and an
 * Android `getString` for each help body. A getter defers all of it to the arm's own
 * `remember { deps.…() }`, so a host that never opens an AI destination never pays it. The same shape
 * covers [AiModelsDeps.controller], [AiConnectionSettingsDeps.controller]/[AiConnectionSettingsDeps.languageChoices],
 * [AiProvidersDeps.controller]/[AiProvidersDeps.unknownErrorMessage] and [RawLlmLogDeps.defaultTitle];
 * it is the settings cluster's `AppSettingsDeps.controller` pattern, applied to this cluster.
 *
 * [helpReadMoreUrl] stays a plain `String`: it is a compile-time literal on the host, not a resource
 * lookup, so deferring it would buy nothing.
 */
class ToolInfoDeps(
    val readTools: () -> List<ToolVd>,
    val writeTools: () -> List<ToolVd>,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
)

/**
 * [AiDocumentFilterScreen]'s platform-supplied slots. [controllerFor] is a no-arg factory the host
 * supplies (it needs a `CoroutineScope` — the host's `lifecycleScope` — that `commonMain` cannot
 * provide); the graph `remember`s the result INSIDE its own composable arm, same shape as
 * [RawLlmLogDeps.controllerFor], so the back-stack entry's own composition owns the instance and
 * its staged (save-on-apply) state.
 *
 * This is why "Discard changes?" actually discards (whole-branch review C1): both
 * [AiDocumentFilterController] and [GlobalToolPermissionsController] seed their working set once,
 * in the constructor, and have no reload/reseed method — only [AiDocumentFilterController.save],
 * which re-baselines. A `controller: AiDocumentFilterController` field built with a bare
 * `remember {}` at HOST scope (as this used to be) is constructed exactly once for the whole
 * Activity, so an unsaved toggle discarded via Back on one visit was still sitting in that same
 * instance's working set on the NEXT visit, ready to ride along into a later `save()`. A factory
 * `remember`ed per back-stack entry gives every (re-)entry a fresh, freshly-seeded instance
 * instead.
 */
class AiDocumentFilterDeps(
    val controllerFor: () -> AiDocumentFilterController,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
)

/** [GlobalToolPermissionsScreen]'s platform-supplied slots. Same shape as [AiDocumentFilterDeps] —
 * see its kdoc for why [controllerFor] is a per-entry factory rather than a host-held instance. */
class GlobalToolPermissionsDeps(
    val controllerFor: () -> GlobalToolPermissionsController,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
)

/**
 * [AiModelsScreen]'s platform-supplied slots. [providersForPicker] mirrors classic's
 * `service.providersForPicker()` call (`LlmModelService` stays a host-side detail; the graph only
 * needs the resolved list, recomputed by the host the same way — `remember(models) { ... }` —
 * that [net.bible.android.view.activity.ai.AiModelsComposeActivity] already did).
 *
 * [onResume] is the established shape for a per-destination resume-refresh (parity with classic's
 * `Activity.onResume()`): the destination itself invokes it via [androidx.lifecycle.compose.LifecycleEventEffect]
 * on [androidx.lifecycle.Lifecycle.Event.ON_RESUME], scoped to only the composable arm that is
 * actually resumed — NOT a host-wide `Activity.onResume()` override, which would fire the refresh
 * (real DB work, for `LlmModelService`) on every resume of the shared nav host regardless of which
 * destination is showing. Later destinations with an analogous service `refresh()` (`RawLogService`,
 * `AiSettingsService`, `PromptService`, `LlmProviderService`) should add the same
 * `val onResume: (() -> Unit)? = null` field to their own `Deps` holder rather than growing a list
 * of unrelated refreshes on the host.
 */
class AiModelsDeps(
    /** Resolved by the arm's `remember { … }`, behind the host's `by lazy` — see [ToolInfoDeps]. */
    val controller: () -> AiModelsController,
    val providersForPicker: () -> List<ProviderVd>,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
    val onResume: (() -> Unit)? = null,
)

/**
 * [AiConnectionSettingsScreen]'s platform-supplied slots. This is the cluster's hub, so it carries
 * more Android-resource baggage than any destination migrated so far:
 *
 * - [controller] is built by the host (needs `labels: AiConnectionLabels`, all `getString` calls,
 *   plus the host's `lifecycleScope`) — same shape as [AiDocumentFilterDeps.controllerFor] etc., and
 *   a `() -> …` getter over a host `by lazy` rather than an instance, per [ToolInfoDeps]' convention:
 *   this constructor alone costs ~45 `getString` calls and starts a permanent snapshot collector that
 *   rebuilds the whole AI settings item tree, which no Search or Settings launch should pay for. Its
 *   own constructor `onNavigate` is a host-supplied no-op; the real navigation branching lives in
 *   THIS graph's `composable(NavRoutes.AI_CONNECTION_SETTINGS)` arm below (see the class kdoc on
 *   [aiNavGraph]), not on the controller — six of its seven edges are `navController.navigate(...)`
 *   and cannot be decided from `:sharedCore`.
 * - [languageChoices]/[customLanguageTag] are the AI-language picker's Android locale-array data
 *   (F32) — read from `R.array.prefs_interface_locale_*`, so they cannot be resolved here.
 * - [onCustomPromptSave]/[customPromptTextFor] wrap [net.bible.sharedcore.ai.AiSettingsService]
 *   calls, but the reset-vs-blank decision they apply
 *   (`net.bible.android.view.activity.ai.resolvedCustomPromptValue`) must stay in the `:app`
 *   module — the existing `AiConnectionSettingsComposeActivityTest` targets that top-level function
 *   by name, unqualified, so it cannot move to `:sharedCore` without breaking that test's imports.
 * - `RAW_LOG_HISTORY` no longer needs an equivalent field here — `RawLogHistory` is a destination
 *   in THIS graph as of Task 8 (like `AiProviders` since Task 6), so it navigates straight there
 *   (`navController.navigate(NavRoutes.AI_RAW_LOG_HISTORY)`), same as `MODELS`/`TOOL_PERMISSIONS`/
 *   `DOCUMENTS`/`PROVIDERS`/`EASY_SETUP`. Every one of the hub's six nav edges now stays inside
 *   this graph; `RESET_USAGE` now opens the controller's own
 *   [net.bible.sharedcore.ai.AiConnectionDialog.ConfirmResetUsage] (platform-dialog removal Task 14)
 *   instead of calling a host callback directly.
 * - [actions] is the help overflow (`CommonUtils.showHelpDialog`), same shape as every other
 *   destination's `helpBody`/`helpReadMoreUrl` pair, except this screen already takes a full
 *   `actions` slot rather than plain strings, so the host supplies the whole composable.
 * - [onResume] is classic's `AiConnectionSettingsComposeActivity.onResume { service.refresh() }`,
 *   ported per [AiModelsDeps.onResume]'s established convention (route-scoped, not host-wide).
 *
 * Task 14: `LlmCostTracker.reset` over `DatabaseContainer` has no `:sharedUi`/`:sharedCore`
 * equivalent, so it stays a host callback -- but it is now wired straight into the CONTROLLER's own
 * constructor (`AiConnectionSettingsController.onResetUsageConfirm`), run from `confirmDialog()`,
 * rather than reached through a `Deps` field the screen calls directly.
 */
class AiConnectionSettingsDeps(
    val controller: () -> AiConnectionSettingsController,
    val languageChoices: () -> List<SettingsItem.Choice>,
    val customLanguageTag: String,
    val onCustomPromptSave: (key: String, value: String?) -> Unit,
    val customPromptTextFor: (key: String) -> String,
    val actions: @Composable RowScope.() -> Unit,
    val onResume: (() -> Unit)? = null,
)

/**
 * [AiProvidersScreen]'s platform-supplied slots — the AI cluster's first destination that takes a
 * navigation argument ([NavRoutes.ARG_START_EASY_SETUP], read by this graph's
 * `composable(NavRoutes.AI_PROVIDERS_PATTERN)` arm, not stored here) and the one carrying the most
 * non-Compose Activity state ported from classic `AiProvidersComposeActivity`:
 *
 * - [controller] is built by the host (needs the host's `lifecycleScope`) — same shape as
 *   [AiDocumentFilterDeps.controllerFor] etc., a `() -> …` getter over a host `by lazy` per
 *   [ToolInfoDeps]' convention. It also owns the easy-setup wizard's service
 *   pass-throughs ([AiProvidersController.recommendedSetups], [AiProvidersController.testConnection],
 *   [AiProvidersController.performEasySetup]) and the disclaimer gate
 *   ([AiProvidersController.disclaimerAccepted]/`acceptDisclaimer`) — none of that needs an
 *   Android resource, so it lives on the controller rather than as ad hoc lambdas here (Task 4
 *   review guidance).
 * - [unknownErrorMessage] is the one piece of Android-resource text the easy-setup flow needs
 *   (`R.string.unknown_error`, classic's fallback when a test/setup failure carries no message) —
 *   kept here rather than on the controller for the same reason [AiModelsDeps.helpBody] etc. live
 *   on `Deps`: `:sharedCore` stays string-resource-free.
 * - The wizard's open/closed state ([net.bible.sharedui.ai.EasySetupState]), the disclaimer gate's
 *   stashed continuation, and the two dialog-dismiss "swallow" flags are NOT on this `Deps` — they
 *   are UI-flow state with no cross-process-death meaning (the continuation is a lambda, which
 *   plainly cannot survive it; the wizard and the swallow flags are transient interaction state),
 *   so they live in `remember` inside this graph's composable arm, same as
 *   [AiModelsScreen]'s `swallowNextDismiss` in the `AI_MODELS` arm.
 * - [onResume] is classic's `AiProvidersComposeActivity.onResume { service.refresh() }`, ported per
 *   [AiModelsDeps.onResume]'s established convention.
 */
class AiProvidersDeps(
    val controller: () -> AiProvidersController,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
    val unknownErrorMessage: () -> String,
    val onResume: (() -> Unit)? = null,
)

/**
 * [AiPromptsScreen]'s platform-supplied slots — the AI cluster's entry destination, and the only
 * one with a hard platform-async dependency (SAF, Android's Storage Access Framework).
 *
 * - [controllerFor] builds the (single, host-lifetime) [AiPromptsController] — but unlike every
 *   earlier single-instance `Deps.controller`, its three navigation callbacks
 *   (`onOpenPrompt`/`onNewPrompt`/`onOpenConnectionSettings`) can only be supplied from inside the
 *   graph's composable arm, since they call `navController.navigate(...)` — same shape as
 *   [RawLogHistoryDeps.controllerFor]'s `onOpenLog` param, extended to three callbacks instead of
 *   one. The graph `remember`s the result once with no keys (parity with every other
 *   single-instance controller here): the lambdas close over `navController` itself, which is
 *   stable across recompositions, so nothing about them ever changes between builds.
 * - [onImportCsv]/[onExportCsv] are the SAF seam. Classic `exportPrompts()`/`importPrompts()` (now
 *   ported verbatim onto the HOST, not reimplemented — see
 *   [net.bible.android.view.activity.nav.NavHostComposeActivity]'s kdoc) need `awaitIntent` (an
 *   [net.bible.android.view.activity.base.ActivityBase] suspend bridge to a system file picker),
 *   `Toast`, `contentResolver` and `SharedConstants.modulesDir` — none of which `commonMain` can
 *   reach. Platform-dialog removal Task 14 moved the editable-vs-add-on CHOICE (what used to be an
 *   `AlertDialog.Builder#setItems` pick-list inside `importPrompts()`) onto
 *   [AiPromptsController.chooseImportMode] — the host's `importPrompts()` now `await`s that instead
 *   of building its own dialog, which is exactly why [onControllerLifecycle] exists: the host has no
 *   other way to reach the controller this arm builds. Both [onImportCsv]/[onExportCsv] are plain
 *   `() -> Unit` here, NOT `suspend` — deliberately,
 *   because of what launches them: a `rememberCoroutineScope()` in the `AI_PROMPTS` composable arm
 *   would be cancelled the instant that back-stack entry stops being the top one (Navigation
 *   disposes a popped/navigated-away-from entry's composition immediately), which is almost
 *   instantly on any Up-press or intra-cluster navigate — unlike classic's Activity
 *   `lifecycleScope`, cancelled only at `onDestroy()`. `installCsvAsAddon`'s file copy +
 *   `addCsvPromptBook` + cache-clear + `refresh()` sequence must not be cut mid-way (a partial
 *   write that is already registered as an add-on, with stale caches, is strictly worse than
 *   before). So the HOST launches these on its own `lifecycleScope` (`NavHostComposeActivity`
 *   supplies `onImportCsv = { lifecycleScope.launch { importPrompts() } }` and the equivalent for
 *   export) and the graph's arm merely calls the lambda — no coroutine scope of its own needed.
 * - [onResume] is classic `AiPromptsComposeActivity.onResume { service.refresh() }`: a child
 *   PromptEdit save does not emit `AiSettings.configChanged` (no prompt write calls `notifyConfigChanged`), and CSV imports and
 *   add-on installs performed here (or an add-on installed from somewhere else entirely) need an
 *   explicit re-query that nothing else triggers — same domain reason
 *   [RawLogHistoryDeps.onResume]'s kdoc restates for its own destination, and
 *   [AiModelsDeps.onResume]'s kdoc for the general route-scoped-not-host-wide convention this
 *   follows.
 * - [onControllerLifecycle] (Task 14, plan correction 11) publishes the live [AiPromptsController]
 *   instance to the host for exactly as long as this destination's composition is alive (`null` on
 *   dispose): [onImportCsv]'s host implementation needs to call `chooseImportMode()` on THIS
 *   destination's controller, and `AppDialogOverlay`'s `onSheetOpening` needs to dismiss a showing
 *   import-mode choice when an app-wide sheet is about to open (two modal sheets must never stack).
 *   Neither reach exists any other way: the controller is built inside this arm (its three nav
 *   callbacks need `navController`), never as a host `by lazy` field.
 * - [currentController] (M5 fix-round) is the read half of that same host field: the arm's
 *   `onDispose` reads it back before clearing, and only clears when it is STILL this instance's own
 *   controller. Without it, a SECOND `AI_PROMPTS` back-stack entry's mount (its `DisposableEffect`
 *   runs `onControllerLifecycle(newController)` first) followed by the FIRST entry's eventual dispose
 *   would null out the host's reference to the new, live controller -- not reachable today (nothing
 *   pushes a second `AI_PROMPTS` entry on top of itself), but a real bug the moment something did.
 */
class AiPromptsDeps(
    val controllerFor: (
        onOpenPrompt: (promptId: String) -> Unit,
        onNewPrompt: () -> Unit,
        onOpenConnectionSettings: () -> Unit,
    ) -> AiPromptsController,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
    val onImportCsv: () -> Unit,
    val onExportCsv: () -> Unit,
    val onResume: (() -> Unit)? = null,
    val onControllerLifecycle: (AiPromptsController?) -> Unit = {},
    val currentController: () -> AiPromptsController? = { null },
)

/**
 * [PromptEditScreen]'s platform-supplied slots.
 *
 * - [controllerFor] builds a fresh [PromptEditController] from the (already-decoded) nav
 *   arguments. Unlike every earlier `Deps.controller` (one instance, alive for the host's whole
 *   lifetime), this destination needs a NEW controller whenever its arguments change — both on a
 *   fresh entry into the route and on the self-navigating "Copy to customize" edge (see
 *   [aiNavGraph]'s `PROMPT_EDIT_PATTERN` arm) — mirroring classic's `by lazy { PromptEditController(...) }`
 *   which read `intent` once per Activity instance. The graph `remember`s the result keyed on the
 *   three constructor arguments, so it is still built exactly once per backstack entry.
 * - [categories]/[toolsByCategory]/[modelChoices]/[globalToolPermission] wrap plain
 *   `PromptService` reads (no Android resource involved) — same shape as [AiModelsDeps.providersForPicker].
 * - [globalMaxIterationsLabel] is the one piece of Android-resource text this screen's reference
 *   data needs (`CommonUtils.aiSettings.maxIterations` formatted via
 *   `R.string.prompt_max_iterations_unlimited`) — kept here for the same reason [AiModelsDeps.helpBody]
 *   etc. live on `Deps`.
 * - [onPromptCopied] shows the "Prompt copied" toast (`R.string.prompt_copied`, `Toast.makeText`);
 *   platform-only, ported verbatim from classic `copyToCustomizeAndFinish`.
 *
 * **No result slot.** Classic's `saveAndMaybeFinish()` conditionally set
 * `RESULT_PROMPT_ID`/`EXTRA_EXECUTE_AFTER_SAVE` on save — dead code with no consumer anywhere in
 * `app/src` (verified by grep; see Task 7's report), so it is simply not ported: `onSave` below
 * only pops.
 */
class PromptEditDeps(
    val controllerFor: (promptId: String?, template: String?, defaultContext: String?) -> PromptEditController,
    val categories: () -> List<PromptCategoryVd>,
    val toolsByCategory: () -> List<Pair<ToolCategoryVd, List<ToolVd>>>,
    val modelChoices: () -> List<SettingsItem.Choice>,
    val globalToolPermission: (toolId: String) -> ToolPermission,
    val globalMaxIterationsLabel: () -> String,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
    val onPromptCopied: () -> Unit,
)

/**
 * [RawLlmLogScreen]'s platform-supplied slots — the cluster's only destination with two
 * mutually-exclusive argument modes (a DB record id XOR an in-memory workspace id; both absent is
 * a third, degenerate "nothing to show" case). Ported from classic [RawLlmLogController]'s host,
 * `RawLlmLogComposeActivity`, except for what genuinely cannot live in commonMain:
 *
 * - [controllerFor] builds a fresh [RawLlmLogController] per backstack entry — same shape as
 *   [PromptEditDeps.controllerFor] — since (unlike every earlier single-instance `Deps.controller`)
 *   two different navigations into this route must not share load state. The graph `remember`s the
 *   result keyed on the decoded `(recordId, workspaceId)` arguments, mirroring
 *   [PromptEditDeps.controllerFor]'s convention.
 * - [defaultTitle]/[recordTitleFor] are the DB-mode title text: the static fallback
 *   (`R.string.raw_llm_log_title`) and a suspend DB lookup + `SimpleDateFormat`/`Locale` format
 *   (classic's `"<modelName> — <yyyy-MM-dd HH:mm>"`) that cannot move into `:sharedCore`/
 *   `:sharedUi` — neither `DatabaseContainer` nor `java.text.SimpleDateFormat` exist on the iOS
 *   target. [recordTitleFor] returns `null` when the record is gone, mirroring classic: the title
 *   stays [defaultTitle] and the graph does not await `recordText`.
 * - [onCopy]/[onShare]/[onReportBug] take the CURRENT `(recordId, workspaceId, recordText)` the
 *   graph already holds, rather than re-deriving classic's `getLogText()` from a stashed controller
 *   reference — the host has no other way to reach this per-entry controller's state, since it is
 *   built and `remember`ed inside the graph, not held on `Deps`. Ported verbatim from classic
 *   `getLogText()`/`copyLog()`/`shareLog()`/`reportBug()`.
 * - [onDelete] performs ONLY the DB delete ([net.bible.sharedcore.ai.RawLogService.deleteByIds]);
 *   popping back to `RawLogHistory` is the graph's job ([popOrExit]), not this lambda's — see the
 *   `RAW_LLM_LOG_PATTERN` arm's kdoc for why that pop is also what keeps the parent list consistent.
 */
class RawLlmLogDeps(
    val controllerFor: () -> RawLlmLogController,
    val defaultTitle: () -> String,
    val recordTitleFor: suspend (recordId: String) -> String?,
    val onCopy: (recordId: String?, workspaceId: String?, recordText: String?) -> Unit,
    val onShare: (recordId: String?, workspaceId: String?, recordText: String?) -> Unit,
    val onDelete: (recordId: String) -> Unit,
    val onReportBug: (recordId: String?, workspaceId: String?) -> Unit,
)

/**
 * [RawLogHistoryScreen]'s platform-supplied slots. [controllerFor] takes the graph's own
 * `onOpenLog` navigation lambda (`navController.navigate(NavRoutes.rawLlmLog(logRecordId = id))`)
 * and builds the [RawLogHistoryController] around it — unlike every earlier single-instance
 * `Deps.controller`, this one needs `navController`, which is only available inside the graph's
 * composable arm, so it cannot simply be `remember`ed on the host like [AiModelsDeps.controller].
 * [onResume] is classic `RawLogHistoryComposeActivity.onResume { service.refresh() }`, ported per
 * [AiModelsDeps.onResume]'s established convention — this is also what keeps the list consistent
 * after a delete on the child [RawLlmLogScreen] returns here (see the `RAW_LLM_LOG_PATTERN` arm's
 * kdoc): `NavBackStackEntry` lifecycle re-enters `RESUMED` when this destination becomes the top of
 * the back stack again, the same mechanism [AiModelsDeps.onResume]/[AiProvidersDeps.onResume]
 * already rely on for a returning navigation, not only a fresh Activity `onResume()`.
 */
class RawLogHistoryDeps(
    val controllerFor: (onOpenLog: (String) -> Unit) -> RawLogHistoryController,
    val helpBody: () -> String,
    val helpReadMoreUrl: String,
    val onResume: (() -> Unit)? = null,
)

/**
 * Platform-supplied slots the AI destinations need but `commonMain` cannot provide: help text
 * (Android string resources today), the data each screen renders, and — via [exitHost] — the way
 * to leave the graph entirely. [exitHost] sits at the top level rather than in a per-destination
 * holder because it is graph-wide, not destination-specific: see [popOrExit]. Grows one nested
 * holder per destination as the cluster migrates.
 */
class AiNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack), which is not the same thing as the on-screen
     * top-bar title a screen draws for itself. One host now serves four clusters, so a static
     * `android:label` in the manifest cannot be right for every destination — see plan D2. Called
     * from each destination's `LaunchedEffect(title)` — keyed on the VALUE, not `Unit`, so a
     * state-derived title (the `AI_CONNECTION_SETTINGS` and `PROMPT_EDIT` arms both have one)
     * cannot go stale — and never from a screen composable: screen signatures are frozen for this
     * migration.
     */
    val setWindowTitle: (String) -> Unit,
    // — TOOL INFO —
    val toolInfo: ToolInfoDeps,
    // — AI DOCUMENT FILTER —
    val aiDocumentFilter: AiDocumentFilterDeps,
    // — GLOBAL TOOL PERMISSIONS —
    val globalToolPermissions: GlobalToolPermissionsDeps,
    // — AI MODELS —
    val aiModels: AiModelsDeps,
    // — AI CONNECTION SETTINGS —
    val aiConnectionSettings: AiConnectionSettingsDeps,
    // — AI PROVIDERS —
    val aiProviders: AiProvidersDeps,
    // — AI PROMPTS —
    val aiPrompts: AiPromptsDeps,
    // — PROMPT EDIT —
    val promptEdit: PromptEditDeps,
    // — RAW LLM LOG —
    val rawLlmLog: RawLlmLogDeps,
    // — RAW LOG HISTORY —
    val rawLogHistory: RawLogHistoryDeps,
)

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly. All ten AI-cluster
 * screens are migrated now, and eight of them normally sit on top of an in-graph parent (reached via
 * `navController.navigate(...)` from elsewhere in this same graph) — so falling through to
 * [AiNavDeps.exitHost] is the EXCEPTION today, not the normal case: it only fires for the two screens
 * `ScreenLauncher`/the host launch directly as the graph's sole entry (`AiPrompts` from the Settings
 * menu, `PromptEdit` from the Vue reading view's `openPromptEditor`), or after a process-death
 * restore drops the back stack down to one.
 *
 * The boolean branch itself is [net.bible.sharedui.nav.popOrExitOnFailedPop] — ONE `internal`
 * helper shared by all four cluster graphs and tested there (whole-branch review M1, which
 * retired four byte-identical copies covered by a single test). What stays here is the part that
 * is genuinely cluster-specific: which of its two branches is live for THIS cluster.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

/**
 * The AI cluster's destinations. Registered into the app's single `NavHost` by the host Activity.
 *
 * All inter-screen navigation lives HERE, not in the host: the host owns platform plumbing only.
 * Up-navigation binds through [popOrExit] — note the screens spell that lambda three different
 * ways (`onUp`, `onBack`, `onNavigateUp`), which is pre-existing and not normalised here because
 * the screen signatures are frozen for this migration.
 */
fun NavGraphBuilder.aiNavGraph(navController: NavHostController, deps: AiNavDeps) {
    composable(NavRoutes.AI_TOOL_INFO) {
        val strings = LocalStrings.current
        val title = strings.viewToolsMenuLabel
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        ToolInfoScreen(
            readTools = remember { deps.toolInfo.readTools() },
            writeTools = remember { deps.toolInfo.writeTools() },
            onUp = { navController.popOrExit(deps.exitHost) },
            helpBody = remember { deps.toolInfo.helpBody() },
            helpReadMoreUrl = deps.toolInfo.helpReadMoreUrl,
        )
    }
    composable(NavRoutes.AI_DOCUMENT_FILTER) {
        val strings = LocalStrings.current
        val title = strings.aiDocumentFilterTitle
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        // Fresh per back-stack entry, not host-held — see AiDocumentFilterDeps' kdoc (C1).
        val controller = remember { deps.aiDocumentFilter.controllerFor() }
        val groups by controller.state.collectAsState()
        val isDirty by controller.isDirty.collectAsState()
        var showDiscardConfirm by remember { mutableStateOf(false) }

        // System back gesture/button: the plain composable's own up-navigation icon already gates
        // itself behind a discard-confirm dialog (see AiDocumentFilterScreen's kdoc), but that does
        // not intercept system back — mirrors classic AiDocumentFilterComposeActivity's BackHandler.
        PlatformBackHandler(enabled = isDirty) { showDiscardConfirm = true }

        AiDocumentFilterScreen(
            groups = groups,
            isDirty = isDirty,
            onUp = { navController.popOrExit(deps.exitHost) },
            onToggle = controller::toggle,
            onResetAll = controller::resetAll,
            onSave = { controller.save(); navController.popOrExit(deps.exitHost) },
            helpBody = remember { deps.aiDocumentFilter.helpBody() },
            helpReadMoreUrl = deps.aiDocumentFilter.helpReadMoreUrl,
        )

        if (showDiscardConfirm) {
            AbConfirmDialog(
                title = null,
                message = strings.discardChangesConfirmation,
                confirmText = strings.yes,
                dismissText = strings.no,
                onConfirm = { showDiscardConfirm = false; navController.popOrExit(deps.exitHost) },
                onDismiss = { showDiscardConfirm = false },
            )
        }
    }
    composable(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS) {
        val strings = LocalStrings.current
        val title = strings.globalToolPermissionsTitle
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        // Fresh per back-stack entry, not host-held — see AiDocumentFilterDeps' kdoc (C1).
        val controller = remember { deps.globalToolPermissions.controllerFor() }
        val groups by controller.state.collectAsState()
        val permissions by controller.permissions.collectAsState()
        val isDirty by controller.isDirty.collectAsState()
        var showDiscardConfirm by remember { mutableStateOf(false) }

        // Same system-back gate as AI_DOCUMENT_FILTER above — see that arm's comment.
        PlatformBackHandler(enabled = isDirty) { showDiscardConfirm = true }

        GlobalToolPermissionsScreen(
            groups = groups,
            permissionFor = { permissions[it] ?: ToolPermission.ASK },
            isDirty = isDirty,
            onUp = { navController.popOrExit(deps.exitHost) },
            onSetPermission = controller::setPermission,
            onSetCategoryRead = controller::setCategoryRead,
            onSetCategoryWrite = controller::setCategoryWrite,
            onResetAll = controller::resetAll,
            onSave = { controller.save(); navController.popOrExit(deps.exitHost) },
            helpBody = remember { deps.globalToolPermissions.helpBody() },
            helpReadMoreUrl = deps.globalToolPermissions.helpReadMoreUrl,
        )

        if (showDiscardConfirm) {
            AbConfirmDialog(
                title = null,
                message = strings.discardChangesConfirmation,
                confirmText = strings.yes,
                dismissText = strings.no,
                onConfirm = { showDiscardConfirm = false; navController.popOrExit(deps.exitHost) },
                onDismiss = { showDiscardConfirm = false },
            )
        }
    }
    composable(NavRoutes.AI_MODELS) {
        val strings = LocalStrings.current
        val title = strings.aiModelsTitle
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        val controller = remember { deps.aiModels.controller() }
        val models by controller.models.collectAsState()
        val editState by controller.dialog.collectAsState()
        val providers = remember(models) { deps.aiModels.providersForPicker() }

        // Parity with classic AiModelsComposeActivity's onResume() -> service.refresh() (models/keys
        // may have changed elsewhere). Route-scoped: fires only while THIS destination is resumed,
        // not on every resume of the shared nav host — see AiModelsDeps.onResume's kdoc. Called
        // unconditionally (a no-op when null) rather than behind an `if`, so the composable call
        // shape here never depends on a value that could differ between recompositions.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { deps.aiModels.onResume?.invoke() }

        // Swallows the single synchronous `onDismiss` that `AbListChoiceDialog` fires right after
        // `onSelect` when a provider is picked — see AiModelsComposeActivity's kdoc for the full
        // rationale. No Activity to hold a plain field on here, so `remember`ed state instead.
        var swallowNextDismiss by remember { mutableStateOf(false) }

        AiModelsScreen(
            models = models,
            providers = providers,
            editState = editState,
            onUp = { navController.popOrExit(deps.exitHost) },
            onAdd = controller::startAdd,
            onPickProvider = { providerId ->
                swallowNextDismiss = true
                controller.pickProvider(providerId)
            },
            onPickModel = controller::pickModel,
            onStartEdit = controller::startEdit,
            onField = controller::updateField,
            onSave = controller::save,
            onDelete = controller::delete,
            onSetDefault = controller::setDefault,
            onSetAsDefault = controller::setAsDefault,
            onSetShowUnsupported = controller::setShowUnsupported,
            onDismiss = {
                if (swallowNextDismiss) {
                    swallowNextDismiss = false
                } else {
                    controller.dismissDialog()
                }
            },
            helpBody = remember { deps.aiModels.helpBody() },
            helpReadMoreUrl = deps.aiModels.helpReadMoreUrl,
        )
    }
    composable(NavRoutes.AI_CONNECTION_SETTINGS) {
        val d = deps.aiConnectionSettings
        val controller = remember { d.controller() }
        val state by controller.state.collectAsState()
        val dialog by controller.dialog.collectAsState()

        // No `strings.xxxTitle` constant here: this screen's own top bar renders `state.title`
        // (an AbScaffold(title = state.title) inside AbSettingsScreen — see
        // AiConnectionSettingsController's SettingsScreenState builder), so that is the value this
        // destination's window title follows too.
        LaunchedEffect(state.title) { deps.setWindowTitle(state.title) }

        // Parity with classic AiConnectionSettingsComposeActivity's onResume() -> service.refresh()
        // — see AiConnectionSettingsDeps.onResume's kdoc for why this is route-scoped, not host-wide.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume?.invoke() }

        AiConnectionSettingsScreen(
            state = state,
            onUp = { navController.popOrExit(deps.exitHost) },
            onSwitch = controller::onSwitch,
            onListChoice = controller::onListChoice,
            onTextInputInt = controller::onTextInputInt,
            onCustomPromptSave = d.onCustomPromptSave,
            customPromptTextFor = d.customPromptTextFor,
            languageChoices = remember { d.languageChoices() },
            customLanguageValue = d.customLanguageTag,
            // The hub's six nav edges: MODELS/TOOL_PERMISSIONS/DOCUMENTS/PROVIDERS/EASY_SETUP/
            // RAW_LOG_HISTORY all have destinations in THIS graph now (RawLogHistory joined as of
            // Task 8, AiProviders as of Task 6), so every one navigates straight there. RESET_USAGE
            // has no destination at all (a dialog) -- Task 14 moved its question onto the
            // controller's own `dialog` state, so this arm just requests it.
            onNavigate = { key ->
                when (key) {
                    AiConnectionNav.EASY_SETUP -> navController.navigate(NavRoutes.aiProviders(startEasySetup = true))
                    AiConnectionNav.PROVIDERS -> navController.navigate(NavRoutes.aiProviders(startEasySetup = false))
                    AiConnectionNav.MODELS -> navController.navigate(NavRoutes.AI_MODELS)
                    AiConnectionNav.TOOL_PERMISSIONS -> navController.navigate(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS)
                    AiConnectionNav.DOCUMENTS -> navController.navigate(NavRoutes.AI_DOCUMENT_FILTER)
                    AiConnectionNav.RAW_LOG_HISTORY -> navController.navigate(NavRoutes.AI_RAW_LOG_HISTORY)
                    AiConnectionNav.RESET_USAGE -> controller.requestResetUsage()
                }
            },
            dialog = dialog,
            onConfirmDialog = controller::confirmDialog,
            onDismissDialog = controller::dismissDialog,
            actions = d.actions,
            backHandler = { onBack -> PlatformBackHandler(enabled = true, onBack = onBack) },
        )
    }
    composable(
        route = NavRoutes.AI_PROVIDERS_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_START_EASY_SETUP) { type = NavType.BoolType; defaultValue = false },
        ),
    ) { backStackEntry ->
        val strings = LocalStrings.current
        val title = strings.aiProvidersTitle
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        val d = deps.aiProviders
        val controller = remember { d.controller() }
        val providers by controller.providers.collectAsState()
        val dialog by controller.dialog.collectAsState()

        // Classic showAddProviderTypeDialog hides already-configured builtin types and always
        // keeps CUSTOM. Recompute on every provider-list change — see AiProvidersDeps' kdoc for why
        // this stays a controller-method call rather than a raw-service lambda on Deps.
        val providerTypes = remember(providers) {
            val configuredTypeIds = providers.map { it.providerTypeId }.toSet()
            controller.providerTypes().filter { it.isCustom || it.id !in configuredTypeIds }
        }

        // Parity with classic AiProvidersComposeActivity's onResume() -> service.refresh() — see
        // AiModelsDeps.onResume's kdoc for why this is route-scoped, not host-wide.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume?.invoke() }

        // Destination-scoped on purpose (unlike AI_PROMPTS' importPrompts/exportPrompts, which
        // deliberately use the HOST's lifecycleScope -- see AiPromptsDeps' onImportCsv/onExportCsv
        // kdoc): testConnection/performEasySetup below only feed local easySetupState, so being
        // cancelled the instant this destination leaves composition is correct, not a hazard -- there
        // is no file/DB side effect here that navigating away should let keep running.
        val scope = rememberCoroutineScope()

        // F31: the continuation stashed while the "Accept AI disclaimer" dialog is shown (`null` =
        // no dialog pending). A lambda, so it cannot survive process death — kept in `remember`,
        // never routed through a SavedStateHandle. See AiProvidersDeps' kdoc.
        val enteredForQuickSetup = remember {
            backStackEntry.arguments?.read { getBooleanOrNull(NavRoutes.ARG_START_EASY_SETUP) } ?: false
        }
        val quickSetupExit = remember { QuickSetupEntryExit(enteredForQuickSetup) { navController.popOrExit(deps.exitHost) } }
        // Whether the pending disclaimer guards the Quick Setup start (vs. an Add) -- see QuickSetupEntryExit.
        var pendingDisclaimerIsQuickSetup by remember { mutableStateOf(false) }
        var pendingDisclaimerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        fun ensureDisclaimerAccepted(forQuickSetup: Boolean = false, onAccepted: () -> Unit) {
            if (controller.disclaimerAccepted()) {
                onAccepted()
            } else {
                pendingDisclaimerIsQuickSetup = forQuickSetup
                pendingDisclaimerAction = onAccepted
            }
        }

        // Swallows the single synchronous onDismiss the PICK_TYPE-step AbListChoiceDialog fires
        // right after onSelect when a provider type is picked — same class of fix as AiModelsScreen's
        // swallowNextDismiss (AI_MODELS arm above). Ported from AiProvidersComposeActivity's
        // swallowNextPickTypeDismiss.
        var swallowNextPickTypeDismiss by remember { mutableStateOf(false) }

        // Same fix, for the easy-setup wizard's step-1 AbListChoiceDialog. Ported from
        // AiProvidersComposeActivity's swallowNextEasySetupDismiss.
        var swallowNextEasySetupDismiss by remember { mutableStateOf(false) }

        // Host-owned UI-flow state for the easy-setup wizard (null = wizard closed). Ported from
        // AiProvidersComposeActivity's easySetupState.
        var easySetupState by remember { mutableStateOf<EasySetupState?>(null) }

        fun startEasySetup() {
            swallowNextEasySetupDismiss = false
            easySetupState = EasySetupState.initial(controller.recommendedSetups())
        }

        // Pre-existing wart, PRESERVED not fixed (see Task 6 report): classic read
        // EXTRA_START_EASY_SETUP in onCreate, i.e. on every Activity (re)create, so a configuration
        // change reopened the easy-setup wizard. LaunchedEffect(Unit) is this arm's analogue of
        // onCreate — it (re)runs once per fresh entry into composition, which happens again whenever
        // the host Activity is recreated (e.g. by a config change without configChanges handling).
        LaunchedEffect(Unit) {
            if (enteredForQuickSetup) {
                ensureDisclaimerAccepted(forQuickSetup = true) { startEasySetup() }
            }
        }

        AiProvidersScreen(
            providers = providers,
            providerTypes = providerTypes,
            editState = dialog,
            onUp = { navController.popOrExit(deps.exitHost) },
            onAdd = { ensureDisclaimerAccepted { controller.startAdd() } },
            onPickType = { typeId ->
                swallowNextPickTypeDismiss = true
                controller.pickType(typeId)
            },
            onStartEdit = controller::startEdit,
            onField = controller::updateField,
            onSave = controller::save,
            onDelete = controller::delete,
            onDismiss = {
                if (swallowNextPickTypeDismiss) {
                    swallowNextPickTypeDismiss = false
                } else {
                    controller.dismissDialog()
                }
            },
            helpBody = remember { d.helpBody() },
            helpReadMoreUrl = d.helpReadMoreUrl,
            showAcceptDisclaimerDialog = pendingDisclaimerAction != null,
            onAcceptDisclaimer = {
                controller.acceptDisclaimer()
                val onAccepted = pendingDisclaimerAction
                pendingDisclaimerAction = null
                pendingDisclaimerIsQuickSetup = false
                onAccepted?.invoke()
            },
            onDismissAcceptDisclaimer = {
                val wasQuickSetupStart = pendingDisclaimerIsQuickSetup
                pendingDisclaimerAction = null
                pendingDisclaimerIsQuickSetup = false
                quickSetupExit.disclaimerDismissed(wasQuickSetupStart)
            },
        )

        easySetupState?.let { state ->
            EasySetupWizard(
                state = state,
                onPick = { setupId ->
                    swallowNextEasySetupDismiss = true
                    easySetupState = state.copy(selectedSetupId = setupId, step = EasySetupStep.ENTER_KEY)
                },
                onKeyChange = { key ->
                    easySetupState = easySetupState?.copy(apiKey = key, testResult = null)
                },
                onTest = {
                    val current = easySetupState
                    val setup = current?.selectedSetup
                    if (current != null && setup != null) {
                        easySetupState = current.copy(testing = true, testResult = null)
                        scope.launch {
                            val result = controller.testConnection(setup.providerTypeId, current.apiKey)
                            val testResult = if (result.isSuccess) {
                                EasySetupTestResult.Success
                            } else {
                                EasySetupTestResult.Failure(result.exceptionOrNull()?.message ?: d.unknownErrorMessage())
                            }
                            easySetupState = easySetupState?.copy(testing = false, testResult = testResult)
                        }
                    }
                },
                onConfirm = {
                    val current = easySetupState
                    val setupId = current?.selectedSetupId
                    if (current != null && setupId != null) {
                        scope.launch {
                            runCatching { controller.performEasySetup(setupId, current.apiKey) }
                                .onSuccess {
                                    easySetupState = easySetupState?.copy(step = EasySetupStep.DONE)
                                }
                                .onFailure { e ->
                                    easySetupState = easySetupState?.copy(
                                        testResult = EasySetupTestResult.Failure(e.message ?: d.unknownErrorMessage()),
                                    )
                                }
                        }
                    }
                },
                onDismiss = {
                    if (swallowNextEasySetupDismiss) {
                        swallowNextEasySetupDismiss = false
                    } else {
                        easySetupState = null
                        quickSetupExit.wizardClosed()
                    }
                },
            )
        }
    }
    composable(NavRoutes.AI_PROMPTS) {
        val strings = LocalStrings.current
        val title = strings.aiPromptsTitle
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        val d = deps.aiPrompts

        // Same shape as RawLogHistoryDeps.controllerFor's onOpenLog -- these three callbacks need
        // navController, which only exists here, so the controller is built once inside this arm
        // rather than remembered on the host (which owns the platform SAF-only Deps below, but
        // never navigation). navController is stable, so a no-args remember still builds this
        // exactly once, same as every other single-instance controller in this graph.
        val controller = remember {
            d.controllerFor(
                { promptId -> navController.navigate(NavRoutes.promptEdit(promptId = promptId)) },
                { navController.navigate(NavRoutes.promptEdit()) },
                { navController.navigate(NavRoutes.AI_CONNECTION_SETTINGS) },
            )
        }
        val configured by controller.configured.collectAsState()
        val groups by controller.groups.collectAsState()
        val showHidden by controller.showHidden.collectAsState()
        val hasHiddenPrompts by controller.hasHiddenPrompts.collectAsState()
        val dialog by controller.dialog.collectAsState()

        // Task 14: publish/withdraw the live controller so the host's onImportCsv can reach
        // chooseImportMode() and AppDialogOverlay can dismiss a showing import-mode choice -- see
        // AiPromptsDeps.onControllerLifecycle's kdoc. M5 fix-round: only clear the host's reference
        // if it is STILL this instance (see AiPromptsDeps.currentController's kdoc). T14 fix-round:
        // dismissImportModeChoice() first, so a chooseImportMode() still in flight when this
        // destination is torn down (composition disposed, e.g. Up-press mid-import) resolves
        // promptly with null instead of leaking an awaiting coroutine.
        DisposableEffect(controller) {
            d.onControllerLifecycle(controller)
            onDispose {
                controller.dismissImportModeChoice()
                if (d.currentController() === controller) d.onControllerLifecycle(null)
            }
        }

        // Parity with classic AiPromptsComposeActivity's onResume() -> service.refresh() -- see
        // AiPromptsDeps.onResume's kdoc.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume?.invoke() }

        AiPromptsScreen(
            configured = configured,
            groups = groups,
            showHidden = showHidden,
            hasHiddenPrompts = hasHiddenPrompts,
            onUp = { navController.popOrExit(deps.exitHost) },
            onOpenPrompt = controller::onOpenPrompt,
            onNewPrompt = controller::onNewPrompt,
            onToggleFavorite = controller::onToggleFavorite,
            onSetPromptHidden = controller::onSetPromptHidden,
            onSetCategoryHidden = controller::onSetCategoryHidden,
            onDeletePrompt = controller::onDeletePrompt,
            onDeleteCategory = controller::onDeleteCategory,
            onMovePrompt = controller::onMovePrompt,
            onMoveCategory = controller::onMoveCategory,
            onCreateCategory = controller::onCreateCategory,
            onRenameCategory = controller::onRenameCategory,
            onSetShowHidden = controller::onSetShowHidden,
            onOpenConnectionSettings = controller::onOpenConnectionSettings,
            // Plain () -> Unit straight through -- d.onImportCsv/onExportCsv already launch on
            // the HOST's lifecycleScope, not a scope owned by this composable arm. See
            // AiPromptsDeps' kdoc for why: a rememberCoroutineScope() here would be cancelled the
            // instant this back-stack entry stops being the top one (near-instant on Up/navigate),
            // unlike the host lifecycleScope classic relied on (cancelled only at onDestroy()),
            // and cutting installCsvAsAddon's file-copy+DB-write sequence mid-way is strictly
            // worse than the old behaviour.
            onImportCsv = d.onImportCsv,
            onExportCsv = d.onExportCsv,
            onCopyPrompt = controller::onCopyPrompt,
            onMovePromptToCategory = controller::onMovePromptToCategory,
            categoriesProvider = { controller.categories() },
            helpBody = remember { d.helpBody() },
            helpReadMoreUrl = d.helpReadMoreUrl,
            dialog = dialog,
            onConfirmImportMode = controller::confirmImportMode,
            onDismissImportModeChoice = controller::dismissImportModeChoice,
            onDismissDialog = controller::dismissDialog,
        )
    }
    composable(
        route = NavRoutes.PROMPT_EDIT_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_PROMPT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_PROMPT_TEMPLATE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_DEFAULT_CONTEXT) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_EXECUTE_AFTER_SAVE) { type = NavType.BoolType; defaultValue = false },
        ),
    ) { backStackEntry ->
        val d = deps.promptEdit
        val strings = LocalStrings.current

        val promptId = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_PROMPT_ID) }
        // template/defaultContext are FREE TEXT: NavRoutes.promptEdit() percent-encoded them on
        // the way in (see NavRoutes' kdoc), so they must be decoded here, at the read site.
        // executeAfterSave is registered (dormant parity port, plan D3) but never read: the
        // result it used to gate is dead code, deleted rather than ported — see PromptEditDeps' kdoc.
        val template = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_PROMPT_TEMPLATE) }
            ?.let(NavRoutes::decodeArg)
        val defaultContext = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_DEFAULT_CONTEXT) }
            ?.let(NavRoutes::decodeArg)

        // Mirrors classic PromptEditComposeActivity's `by lazy { PromptEditController(...) }`:
        // built once per (promptId, template, defaultContext) — i.e. once per backstack entry,
        // since a fresh entry (including the self-navigation below) always carries fresh
        // arguments. See PromptEditDeps.controllerFor's kdoc.
        val controller = remember(promptId, template, defaultContext) {
            d.controllerFor(promptId, template, defaultContext)
        }

        val state by controller.state.collectAsState()
        val tab by controller.tab.collectAsState()
        val availableTabs by controller.availableTabs.collectAsState()
        val isDirty by controller.isDirty.collectAsState()
        val canSave by controller.canSave.collectAsState()

        // Mirrors PromptEditScreen's own top-bar title logic (see that screen's `val title = when
        // {...}` below its parameter list) rather than a single `strings.xxxTitle` constant, since
        // this destination's title genuinely depends on state (new / built-in / user-edited).
        val title = when {
            controller.isNew -> strings.newPrompt
            state.isBuiltIn -> strings.promptEditTitleBuiltIn
            else -> strings.promptEditTitleEdit
        }
        LaunchedEffect(title) { deps.setWindowTitle(title) }

        val categories = remember { d.categories() }
        val toolsByCategory = remember { d.toolsByCategory() }
        val modelChoices = remember { d.modelChoices() }
        val globalMaxIterationsLabel = remember { d.globalMaxIterationsLabel() }

        var showDiscardConfirm by remember { mutableStateOf(false) }
        // System back gesture/button: PromptEditScreen's own up-navigation icon already gates
        // itself behind a discard-confirm dialog (see that screen's kdoc), but that does not
        // intercept system back — mirrors classic PromptEditComposeActivity's BackHandler.
        PlatformBackHandler(enabled = isDirty) { showDiscardConfirm = true }

        PromptEditScreen(
            state = state,
            tab = tab,
            availableTabs = availableTabs,
            disabledContexts = controller.disabledContexts,
            hiddenAdvancedKeys = controller.hiddenAdvancedKeys,
            isDirty = isDirty,
            canSave = canSave,
            isReadOnly = state.isReadOnly,
            isBuiltIn = state.isBuiltIn,
            isNew = controller.isNew,
            categories = categories,
            toolsByCategory = toolsByCategory,
            modelChoices = modelChoices,
            globalToolPermission = d.globalToolPermission,
            globalMaxIterationsLabel = globalMaxIterationsLabel,
            onSelectTab = controller::selectTab,
            onSetName = controller::setName,
            onSetDescription = controller::setDescription,
            onSetTemplate = controller::setTemplate,
            onSetCategory = controller::setCategory,
            onToggleContext = controller::toggleContext,
            onSetBibleOnly = controller::setBibleOnly,
            onSetTextTransformation = controller::setTextTransformation,
            onSetPermissionMode = controller::setPermissionMode,
            onSetToolPermission = controller::setToolPermission,
            onSetCategoryRead = controller::setCategoryRead,
            onSetCategoryWrite = controller::setCategoryWrite,
            onResetToolPermissions = controller::resetToolPermissions,
            onSetModelOverride = controller::setModelOverride,
            onSetMaxIterations = controller::setMaxIterations,
            onSetSwitch = controller::setSwitch,
            // No result to report — see PromptEditDeps' kdoc "No result slot". Only pop when a
            // save actually happened (controller.save() returns null if canSave was false,
            // defensively — the Save action is already disabled in that case).
            onSave = { if (controller.save() != null) navController.popOrExit(deps.exitHost) },
            onDelete = { controller.delete(); navController.popOrExit(deps.exitHost) },
            // Self-replacing navigation (mirrors classic copyToCustomizeAndFinish): navigate to a
            // fresh PROMPT_EDIT entry for the copy, popping the CURRENT entry off so the old
            // (now-stale) editor never lingers on the back stack. popUpTo targets the PATTERN
            // (not this entry's own concrete route) because NavController resolves popUpTo by
            // route identity, and every PROMPT_EDIT entry — this one and the new one about to be
            // pushed — shares that same registered route; popping by pattern removes exactly the
            // one entry currently on the stack for it, which is what "replace" means here.
            onCopyToCustomize = {
                val newId = controller.copyToCustomize()
                if (newId != null) {
                    d.onPromptCopied()
                    navController.navigate(NavRoutes.promptEdit(promptId = newId)) {
                        popUpTo(NavRoutes.PROMPT_EDIT_PATTERN) { inclusive = true }
                    }
                }
            },
            onViewTools = { navController.navigate(NavRoutes.AI_TOOL_INFO) },
            onBack = { navController.popOrExit(deps.exitHost) },
            helpBody = remember { d.helpBody() },
            helpReadMoreUrl = d.helpReadMoreUrl,
        )

        if (showDiscardConfirm) {
            AbConfirmDialog(
                title = null,
                message = strings.discardChangesConfirmation,
                confirmText = strings.yes,
                dismissText = strings.no,
                onConfirm = { showDiscardConfirm = false; navController.popOrExit(deps.exitHost) },
                onDismiss = { showDiscardConfirm = false },
            )
        }
    }
    // RawLogHistory.onOpenLog (below) navigates to NavRoutes.rawLlmLog(...), so this arm must
    // exist in the SAME graph -- but NavGraphBuilder.composable(...) calls are independent,
    // order-insensitive registrations resolved by route string at navigate time, not sequential
    // references, so which of the two is written first here has no runtime effect. This one is
    // simply listed first because it is the target of the other's edge, not because registration
    // order matters to Navigation.
    composable(
        route = NavRoutes.RAW_LLM_LOG_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_LOG_RECORD_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_WORKSPACE_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.rawLlmLog

        // Both ids are IdType-shaped strings (unreserved characters only, like ARG_PROMPT_ID),
        // never free text, so — matching that precedent — neither is run through
        // NavRoutes.decodeArg here.
        val recordId = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_LOG_RECORD_ID) }
        val workspaceId = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_WORKSPACE_ID) }

        // Mirrors classic RawLlmLogComposeActivity's per-Activity-instance controller: a fresh
        // RawLlmLogController per (recordId, workspaceId) combination, i.e. once per backstack
        // entry — see RawLlmLogDeps.controllerFor's kdoc.
        val controller = remember(recordId, workspaceId) { d.controllerFor() }
        val recordText by controller.recordText.collectAsState()
        val entries by controller.entries.collectAsState()
        val expandedIndices by controller.expandedIndices.collectAsState()
        val canReportBug by controller.canReportBug.collectAsState()

        var loading by remember(recordId, workspaceId) { mutableStateOf(true) }
        var title by remember(recordId, workspaceId) { mutableStateOf(d.defaultTitle()) }
        LaunchedEffect(title) { deps.setWindowTitle(title) }

        // Ported unchanged from classic RawLlmLogComposeActivity's onCreate LaunchedEffect(Unit) —
        // see RawLlmLogDeps' kdoc for what moved to the host (recordTitleFor) and why.
        LaunchedEffect(recordId, workspaceId) {
            when {
                recordId != null -> {
                    controller.loadRecord(recordId)
                    val recordTitle = d.recordTitleFor(recordId)
                    if (recordTitle != null) {
                        title = recordTitle
                        // Await the decompress so we don't flash the empty state before the text arrives.
                        controller.recordText.filterNotNull().first()
                    }
                    loading = false
                }
                workspaceId != null -> {
                    controller.loadSession(workspaceId)
                    loading = false
                }
                else -> loading = false
            }
        }

        RawLlmLogScreen(
            title = title,
            loading = loading,
            recordText = recordText,
            entries = entries,
            expandedIndices = expandedIndices,
            canReportBug = canReportBug,
            onToggleExpanded = controller::toggleExpanded,
            onCopy = { d.onCopy(recordId, workspaceId, recordText) },
            onShare = { d.onShare(recordId, workspaceId, recordText) },
            // DB mode only (the screen only surfaces delete when recordText != null). The DB
            // delete itself is host-side (d.onDelete); popping back to RawLogHistory is this
            // graph's job — its LifecycleEventEffect(ON_RESUME) is what re-collects the now-stale
            // row away, see RawLogHistoryDeps' kdoc.
            onDelete = {
                recordId?.let { d.onDelete(it) }
                navController.popOrExit(deps.exitHost)
            },
            onReportBug = { d.onReportBug(recordId, workspaceId) },
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }
    composable(NavRoutes.AI_RAW_LOG_HISTORY) {
        val strings = LocalStrings.current
        val title = strings.rawLogHistoryTitle
        LaunchedEffect(title) { deps.setWindowTitle(title) }
        val d = deps.rawLogHistory

        // Needs navController (the onOpenLog edge below), so built here rather than remembered on
        // the host — see RawLogHistoryDeps' kdoc.
        val controller = remember {
            d.controllerFor { id -> navController.navigate(NavRoutes.rawLlmLog(logRecordId = id)) }
        }
        val summaries by controller.summaries.collectAsState()
        val selection by controller.selection.collectAsState()
        val selectionMode by controller.selectionMode.collectAsState()

        // Parity with classic RawLogHistoryComposeActivity's onResume() -> service.refresh() — see
        // RawLogHistoryDeps.onResume's kdoc for why this is also what keeps the list consistent
        // after a delete on the child RawLlmLogScreen returns here.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume?.invoke() }

        RawLogHistoryScreen(
            summaries = summaries,
            selection = selection,
            selectionMode = selectionMode,
            onOpenLog = controller::openLog,
            onToggleSelect = controller::toggleSelect,
            onClearSelection = controller::clearSelection,
            onDeleteSelected = controller::deleteSelected,
            onDeleteOlderThan = controller::deleteOlderThan,
            onDeleteAll = controller::deleteAll,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
            helpBody = remember { d.helpBody() },
            helpReadMoreUrl = d.helpReadMoreUrl,
        )
    }
}
