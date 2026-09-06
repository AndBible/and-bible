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
import net.bible.sharedui.ai.PromptEditScreen
import net.bible.sharedui.ai.RawLlmLogScreen
import net.bible.sharedui.ai.RawLogHistoryScreen
import net.bible.sharedui.ai.ToolInfoScreen
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.strings.LocalStrings

/**
 * [ToolInfoScreen]'s platform-supplied slots. One such holder per destination, nested under
 * [AiNavDeps] — kept small and grouped rather than flattened, because at nine destinations a flat
 * [AiNavDeps] would mix ~40 fields (plain data, per-item lambdas, Task 9's suspend lambdas) in one
 * namespace with nothing but a naming convention telling them apart.
 */
class ToolInfoDeps(
    val readTools: List<ToolVd>,
    val writeTools: List<ToolVd>,
    val helpBody: String,
    val helpReadMoreUrl: String,
)

/**
 * [AiDocumentFilterScreen]'s platform-supplied slots. [controller] is constructed by the host
 * (it needs a `CoroutineScope` — the host's `lifecycleScope` — that `commonMain` cannot provide).
 */
class AiDocumentFilterDeps(
    val controller: AiDocumentFilterController,
    val helpBody: String,
    val helpReadMoreUrl: String,
)

/** [GlobalToolPermissionsScreen]'s platform-supplied slots. Same shape as [AiDocumentFilterDeps]. */
class GlobalToolPermissionsDeps(
    val controller: GlobalToolPermissionsController,
    val helpBody: String,
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
    val controller: AiModelsController,
    val providersForPicker: () -> List<ProviderVd>,
    val helpBody: String,
    val helpReadMoreUrl: String,
    val onResume: (() -> Unit)? = null,
)

/**
 * [AiConnectionSettingsScreen]'s platform-supplied slots. This is the cluster's hub, so it carries
 * more Android-resource baggage than any destination migrated so far:
 *
 * - [controller] is built by the host (needs `labels: AiConnectionLabels`, all `getString` calls,
 *   plus the host's `lifecycleScope`) — same shape as [AiDocumentFilterDeps.controller] etc. Its
 *   own constructor `onNavigate` is a host-supplied no-op; the real navigation branching lives in
 *   THIS graph's `composable(NavRoutes.AI_CONNECTION_SETTINGS)` arm below (see the class kdoc on
 *   [aiNavGraph]), not on the controller — three of the six edges are `navController.navigate(...)`
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
 *   this graph; `RESET_USAGE` alone remains a host callback (it opens a platform dialog, not a
 *   destination).
 * - [onResetUsageConfirm] shows a platform `AlertDialog` and runs `LlmCostTracker.reset` over
 *   `DatabaseContainer` — neither has a `:sharedUi`/`:sharedCore` equivalent, so it stays a host
 *   callback rather than becoming a `:sharedUi` dialog.
 * - [actions] is the help overflow (`CommonUtils.showHelpDialog`), same shape as every other
 *   destination's `helpBody`/`helpReadMoreUrl` pair, except this screen already takes a full
 *   `actions` slot rather than plain strings, so the host supplies the whole composable.
 * - [onResume] is classic's `AiConnectionSettingsComposeActivity.onResume { service.refresh() }`,
 *   ported per [AiModelsDeps.onResume]'s established convention (route-scoped, not host-wide).
 */
class AiConnectionSettingsDeps(
    val controller: AiConnectionSettingsController,
    val languageChoices: List<SettingsItem.Choice>,
    val customLanguageTag: String,
    val onCustomPromptSave: (key: String, value: String?) -> Unit,
    val customPromptTextFor: (key: String) -> String,
    val onResetUsageConfirm: () -> Unit,
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
 *   [AiDocumentFilterDeps.controller] etc. It also owns the easy-setup wizard's service
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
    val controller: AiProvidersController,
    val helpBody: String,
    val helpReadMoreUrl: String,
    val unknownErrorMessage: String,
    val onResume: (() -> Unit)? = null,
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
    val helpBody: String,
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
    val defaultTitle: String,
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
    val helpBody: String,
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
    // — PROMPT EDIT —
    val promptEdit: PromptEditDeps,
    // — RAW LLM LOG —
    val rawLlmLog: RawLlmLogDeps,
    // — RAW LOG HISTORY —
    val rawLogHistory: RawLogHistoryDeps,
)

/**
 * Whether an up-navigation attempt that just tried to pop the back stack should fall through to
 * exiting the host outright, given [popped] (`navController.popBackStack()`'s result). Split out
 * from [popOrExit] as a plain boolean-in function — rather than folded into it — so this branch is
 * unit-testable without a real `NavHostController`: that class requires an Android `Context` to
 * construct and has no lightweight fake, while `:sharedUi` (as of this file) has no Robolectric-
 * style test runner, only plain JUnit via `kotlin("test")`. `internal` rather than `private` for
 * exactly that reason — a visible seam that is tested beats a private one that is not.
 */
internal fun popOrExitOnFailedPop(popped: Boolean, exitHost: () -> Unit) {
    if (!popped) exitHost()
}

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly — which is the
 * normal case while `ScreenLauncher` launches each migrated screen straight into the host (today,
 * `ToolInfo` is always the graph's only entry, since nothing else is migrated yet).
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
        ToolInfoScreen(
            readTools = deps.toolInfo.readTools,
            writeTools = deps.toolInfo.writeTools,
            onUp = { navController.popOrExit(deps.exitHost) },
            helpBody = deps.toolInfo.helpBody,
            helpReadMoreUrl = deps.toolInfo.helpReadMoreUrl,
        )
    }
    composable(NavRoutes.AI_DOCUMENT_FILTER) {
        val strings = LocalStrings.current
        val controller = deps.aiDocumentFilter.controller
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
            helpBody = deps.aiDocumentFilter.helpBody,
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
        val controller = deps.globalToolPermissions.controller
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
            helpBody = deps.globalToolPermissions.helpBody,
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
        val controller = deps.aiModels.controller
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
            helpBody = deps.aiModels.helpBody,
            helpReadMoreUrl = deps.aiModels.helpReadMoreUrl,
        )
    }
    composable(NavRoutes.AI_CONNECTION_SETTINGS) {
        val d = deps.aiConnectionSettings
        val state by d.controller.state.collectAsState()

        // Parity with classic AiConnectionSettingsComposeActivity's onResume() -> service.refresh()
        // — see AiConnectionSettingsDeps.onResume's kdoc for why this is route-scoped, not host-wide.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { d.onResume?.invoke() }

        AiConnectionSettingsScreen(
            state = state,
            onUp = { navController.popOrExit(deps.exitHost) },
            onSwitch = d.controller::onSwitch,
            onListChoice = d.controller::onListChoice,
            onTextInputInt = d.controller::onTextInputInt,
            onCustomPromptSave = d.onCustomPromptSave,
            customPromptTextFor = d.customPromptTextFor,
            languageChoices = d.languageChoices,
            customLanguageValue = d.customLanguageTag,
            // The hub's six nav edges: MODELS/TOOL_PERMISSIONS/DOCUMENTS/PROVIDERS/EASY_SETUP/
            // RAW_LOG_HISTORY all have destinations in THIS graph now (RawLogHistory joined as of
            // Task 8, AiProviders as of Task 6), so every one navigates straight there. RESET_USAGE
            // has no destination at all (a dialog), so it always stays a host callback.
            onNavigate = { key ->
                when (key) {
                    AiConnectionNav.EASY_SETUP -> navController.navigate(NavRoutes.aiProviders(startEasySetup = true))
                    AiConnectionNav.PROVIDERS -> navController.navigate(NavRoutes.aiProviders(startEasySetup = false))
                    AiConnectionNav.MODELS -> navController.navigate(NavRoutes.AI_MODELS)
                    AiConnectionNav.TOOL_PERMISSIONS -> navController.navigate(NavRoutes.AI_GLOBAL_TOOL_PERMISSIONS)
                    AiConnectionNav.DOCUMENTS -> navController.navigate(NavRoutes.AI_DOCUMENT_FILTER)
                    AiConnectionNav.RAW_LOG_HISTORY -> navController.navigate(NavRoutes.AI_RAW_LOG_HISTORY)
                    AiConnectionNav.RESET_USAGE -> d.onResetUsageConfirm()
                }
            },
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
        val d = deps.aiProviders
        val controller = d.controller
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

        val scope = rememberCoroutineScope()

        // F31: the continuation stashed while the "Accept AI disclaimer" dialog is shown (`null` =
        // no dialog pending). A lambda, so it cannot survive process death — kept in `remember`,
        // never routed through a SavedStateHandle. See AiProvidersDeps' kdoc.
        var pendingDisclaimerAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        fun ensureDisclaimerAccepted(onAccepted: () -> Unit) {
            if (controller.disclaimerAccepted()) onAccepted() else pendingDisclaimerAction = onAccepted
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
            val startEasySetupArg = backStackEntry.arguments?.read {
                getBooleanOrNull(NavRoutes.ARG_START_EASY_SETUP)
            } ?: false
            if (startEasySetupArg) {
                ensureDisclaimerAccepted { startEasySetup() }
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
            helpBody = d.helpBody,
            helpReadMoreUrl = d.helpReadMoreUrl,
            showAcceptDisclaimerDialog = pendingDisclaimerAction != null,
            onAcceptDisclaimer = {
                controller.acceptDisclaimer()
                val onAccepted = pendingDisclaimerAction
                pendingDisclaimerAction = null
                onAccepted?.invoke()
            },
            onDismissAcceptDisclaimer = { pendingDisclaimerAction = null },
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
                                EasySetupTestResult.Failure(result.exceptionOrNull()?.message ?: d.unknownErrorMessage)
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
                                        testResult = EasySetupTestResult.Failure(e.message ?: d.unknownErrorMessage),
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
                    }
                },
            )
        }
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
            helpBody = d.helpBody,
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
    // Registered BEFORE RAW_LOG_HISTORY on purpose: RawLogHistory.onOpenLog navigates to
    // NavRoutes.rawLlmLog(...), so registering that arm first means the route it targets already
    // exists in the graph the moment RawLogHistory's own arm is added below.
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
        var title by remember(recordId, workspaceId) { mutableStateOf(d.defaultTitle) }

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
            helpBody = d.helpBody,
            helpReadMoreUrl = d.helpReadMoreUrl,
        )
    }
}
