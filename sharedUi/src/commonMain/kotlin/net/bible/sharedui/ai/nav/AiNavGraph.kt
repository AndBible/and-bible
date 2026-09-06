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
import net.bible.sharedcore.ai.AiDocumentFilterController
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.ProviderVd
import net.bible.sharedcore.ai.ToolPermission
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.ai.AiDocumentFilterScreen
import net.bible.sharedui.ai.AiModelsScreen
import net.bible.sharedui.ai.GlobalToolPermissionsScreen
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
}
