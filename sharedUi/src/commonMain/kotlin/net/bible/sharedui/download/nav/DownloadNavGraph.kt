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

package net.bible.sharedui.download.nav

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import net.bible.sharedcore.download.CustomRepositoryController
import net.bible.sharedcore.download.CustomRepositoryEditorController
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.download.CustomRepositoriesScreen
import net.bible.sharedui.download.CustomRepositoryEditorScreen
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop
import net.bible.sharedui.strings.LocalStrings

/**
 * [CustomRepositoriesScreen]'s platform-supplied slots, ported from classic
 * `CustomRepositoriesComposeActivity`.
 *
 * This is the cluster's ROOT destination: nothing inside any graph navigates to it (it is reached
 * only as the host's start destination, through `ScreenLauncher.MIGRATED`), while it is itself the
 * one that navigates to [CustomRepositoryEditorDeps]'s destination.
 *
 * - [controllerFor] builds the [CustomRepositoryController] around the [onDuplicate] callback the
 *   arm hands it. It is a per-entry factory, not a host-memoised one, for the reason classic used a
 *   fresh Activity every time: this destination is the host's start destination on every live edge,
 *   so there is no sitting-child composition for a memoised controller to survive.
 *
 * - [title] is the WINDOW title, classic's `android:label="@string/custom_repositories"` (one host
 *   now serves every cluster) -- the same string [CustomRepositoriesScreen] draws in its own top bar.
 *
 * - [onDuplicate] is classic `handleResult`'s `ToastEvent(duplicate_custom_repository)` path,
 *   surfaced when [CustomRepositoryController.applyResult] rejects an upsert as a duplicate name. It
 *   is threaded through [controllerFor] rather than set on the controller afterwards, because the
 *   controller's own `onDuplicate` var has to be wired before the first `applyResult` call could
 *   possibly fire it.
 */
class CustomRepositoriesDeps(
    val controllerFor: (onDuplicate: (String) -> Unit) -> CustomRepositoryController,
    val title: String,
    val onDuplicate: (name: String) -> Unit,
)

/**
 * [CustomRepositoryEditorScreen]'s platform-supplied slots, ported from classic
 * `CustomRepositoryEditorComposeActivity`.
 *
 * This is the cluster's CHILD destination, and its only entry is from [CustomRepositoriesDeps]'s
 * `onRowClick`/`onCreate` -- see [DownloadNavDeps.repositoryEditorResults] for what that means for
 * its channel.
 *
 * - [controllerFor] builds the [CustomRepositoryEditorController] around the resolved [initialFor]
 *   payload. It takes **no** `onResult` lambda, unlike every controller factory in the bookmark
 *   cluster: the real constructor is `(service, scope, initial)`
 *   (`CustomRepositoryEditorController.kt:40`), which reports no outcome of its own at all -- the
 *   arm builds `RepositoryResult`s straight off the controller's own
 *   `buildSaveResult()`/`buildDeleteResult()`/`buildCancelResult()` and hands them to the channel
 *   itself, so an `onResult` slot here would never be called.
 *
 * - [title] is the WINDOW title, the same `@string/custom_repositories` classic's manifest gives
 *   BOTH Activities in this cluster (`AndroidManifest.xml`), and the same string
 *   [CustomRepositoryEditorScreen] draws in its own top bar.
 *
 * - [initialFor] resolves the route's OPTIONAL id (plan D9) to the row being edited: `null` is a
 *   brand-new repository -- classic's `newItem()`, a blank `RepositoryResult()` -- and a non-null id
 *   is looked up through the SAME `CustomRepositoryService` the list uses
 *   (`CoreModule.kt:88`), never a JSON payload carried on the route. `RepositoryResult` is a
 *   `:sharedCore` data class with no serializer, and `NavResults.kt`'s closing note forbids adding
 *   one -- an id is the only thing `NavRoutes.customRepositoryEditor` can carry, so this is the seam
 *   that turns it back into the row.
 *
 * - [readClipboard] is classic `paste()`: the clipboard's primary text clip, or `null` when there is
 *   none. `commonMain` has no `ClipboardManager`, so reading it is host work; what the arm does with
 *   the result (`controller.setUrl`) is unchanged from classic.
 */
class CustomRepositoryEditorDeps(
    val controllerFor: (initial: RepositoryResult) -> CustomRepositoryEditorController,
    val title: String,
    val initialFor: suspend (id: Long?) -> RepositoryResult,
    val readClipboard: () -> String?,
)

/**
 * Platform-supplied slots the Documents/downloads cluster's destinations need but `commonMain`
 * cannot provide. Same top-level shape as [net.bible.sharedui.bookmark.nav.BookmarkNavDeps]:
 * [exitHost] and [setWindowTitle] are graph-wide, one nested holder per destination below.
 *
 * **This class GROWS.** Slice 4 migrates seven Documents/downloads destinations across four tasks,
 * and this task builds only the first two -- [customRepositories] and [customRepositoryEditor].
 * Tasks 4 (`ProgressStatus`), 7a (`Download`) and 8 (`CloudDocuments`) each add their own nested deps
 * field to this class and their own arm to [downloadNavGraph], the same way
 * [net.bible.sharedui.bookmark.nav.BookmarkNavDeps] grew its three nested holders one task at a
 * time. Only [repositoryEditorResults] is declared up front, for [BookmarkNavDeps]'s own reason:
 * this is the file that owns it, and creating it later would widen this class's constructor for
 * every caller that already built one. A per-destination deps field cannot be front-loaded the same
 * way -- Tasks 4, 7a and 8 introduce the very types it would have to name -- so building one now
 * would be scaffolding for a destination this task does not implement.
 */
class DownloadNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) -- not the on-screen top-bar title a screen
     * draws for itself. Called from each destination's `LaunchedEffect(title)`, keyed on the VALUE
     * so a state-derived title cannot go stale, and never from a screen composable: screen
     * signatures are frozen for this migration.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * How the repository EDITOR hands its result back to the LIST -- see [NavResultChannel]'s own
     * kdoc for the two branches the channel can take.
     *
     * **Only the IN-GRAPH branch is reachable, and it is the only one that ever will be.** The
     * editor is entered from exactly one place, [CustomRepositoriesDeps]'s arm
     * (`navController.navigate(NavRoutes.customRepositoryEditor(id))` / `(null)`), it is absent from
     * `ScreenLauncher.MIGRATED` (its payload argument means there is no argument-free route to give
     * it), and `NavRoutes.customRepositoryEditor` is called from nowhere else in the tree. So, unlike
     * every other channel in these graphs, this one's `exitWithResult` lambda is a hard `error(...)`
     * on the HOST side (`NavHostComposeActivity.repositoryEditorResults`) rather than a real Intent
     * packing -- see that field's own kdoc for the argument in full. Reaching that branch from this
     * graph would mean the editor had been made externally entrable without ever being given a
     * result contract of its own, which is a defect worth failing loudly on rather than packing an
     * Intent nobody defined.
     */
    val repositoryEditorResults: NavResultChannel<RepositoryResult>,
    // — CUSTOM REPOSITORIES —
    val customRepositories: CustomRepositoriesDeps,
    // — CUSTOM REPOSITORY EDITOR —
    val customRepositoryEditor: CustomRepositoryEditorDeps,
)

/**
 * Pop to the parent destination, or -- when there is none, i.e. this destination is the host's start
 * destination -- leave the host. The same one-line extension every other cluster graph carries; the
 * boolean branch itself is [popOrExitOnFailedPop], shared so that "a failed pop still leaves" is
 * decided in one place.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The Documents/downloads cluster's destinations. Registered into the app's single `NavHost` by the
 * host Activity. This task builds the batch's simplest shape -- a parent and a child, one channel
 * between them, no external entry to the child -- and the arm below is the template the later
 * `ProgressStatus`/`Download`/`CloudDocuments` arms (Tasks 4, 7a, 8) follow.
 *
 * [NavRoutes.CUSTOM_REPOSITORIES_PATTERN] is reachable through `ScreenLauncher.MIGRATED`;
 * [NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN] deliberately is not, because its `repositoryId`
 * argument is OPTIONAL and an argument-free `Screen.CustomRepositoryEditor` entry would be
 * ambiguous about which id-less state it means -- the same reasoning `Screen.LabelEdit` and
 * `Screen.ManageLabels` document in `ScreenLauncher.MIGRATED`'s own comments, except here the
 * argument-free route DOES have a defined meaning (a new repository) and is simply never reachable
 * except from this graph's own `onCreate` call.
 *
 * Between this task and Task 7b, `CustomRepositories` is reached from `DownloadComposeActivity
 * .onCustomRepositories()`, which still launches it as an Activity through the classic
 * `awaitIntent`/`ScreenLauncher` path -- the coexistence seam working as designed, since that method
 * keeps working unchanged (`ScreenLauncher.intentFor` now answers through `MIGRATED` instead of a
 * classic Activity class). Task 7b replaces it with an in-graph `navigate` once `Download` itself
 * is a destination in this same file.
 */
fun NavGraphBuilder.downloadNavGraph(navController: NavHostController, deps: DownloadNavDeps) {
    // ——— CUSTOM REPOSITORIES ———
    composable(route = NavRoutes.CUSTOM_REPOSITORIES_PATTERN) {
        val d = deps.customRepositories
        val controller = remember { d.controllerFor(d.onDuplicate) }
        val state by controller.state.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // The child's result, consumed once and cleared -- the shape ManageLabels uses for
        // LabelEdit (BookmarkNavGraph.kt:641-646). Classic did this with awaitIntent + a `!!`
        // on a "data" extra (CustomRepositoriesComposeActivity.kt:94-96); the typed channel
        // removes both the Intent and the `!!` rather than preserving them (design §7.4).
        val pendingEditor by deps.repositoryEditorResults.pending.collectAsState()
        LaunchedEffect(pendingEditor) {
            if (pendingEditor == null) return@LaunchedEffect
            val result = deps.repositoryEditorResults.consume() ?: return@LaunchedEffect
            controller.applyResult(result)
        }

        CustomRepositoriesScreen(
            state = state,
            // The route argument is the repository's ID, not its JSON. Classic had to serialise the
            // whole row because an Intent extra was the only channel it had
            // (CustomRepositoriesComposeActivity.kt:91-92); a route can carry the id and let the
            // editor re-read the row from the same service the list uses. A NEW repository has no
            // id, so the argument is simply OMITTED (plan D9) -- absent, never present-and-empty.
            onRowClick = { id -> navController.navigate(NavRoutes.customRepositoryEditor(id)) },
            onCreate = { navController.navigate(NavRoutes.customRepositoryEditor(null)) },
            onUp = { navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— CUSTOM REPOSITORY EDITOR ———
    composable(
        route = NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_REPOSITORY_ID) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val d = deps.customRepositoryEditor
        // ABSENT means NEW (plan D9). Classic asserted its payload with `!!` in the lazy `initial`
        // (CustomRepositoryEditorComposeActivity.kt:63); there is nothing to assert here, and
        // there is deliberately NO pop-on-null backstop -- null is the create-new state, so popping
        // on it would make the "create repository" button silently do nothing.
        val id = backStackEntry.arguments
            ?.read { getStringOrNull(NavRoutes.ARG_REPOSITORY_ID) }
            ?.toLongOrNull()

        // The id resolves to a row through the same service the list uses; null means a blank
        // RepositoryData(), classic's `newItem()`. Null while it loads, so the arm renders nothing
        // rather than building a controller around a half-known payload.
        val initial by produceState<RepositoryResult?>(initialValue = null, id) {
            value = d.initialFor(id)
        }
        val resolved = initial ?: return@composable
        // No `onResult` lambda: the controller's constructor is (service, scope, initial)
        // (CustomRepositoryEditorController.kt:40), and every exit below delivers explicitly.
        val controller = remember(id, resolved) { d.controllerFor(resolved) }
        val state by controller.state.collectAsState()
        var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }
        val strings = LocalStrings.current

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's BackHandler (:78-81), which routed EVERY back press through the dirty gate.
        // PlatformBackHandler, never androidx.activity.compose.BackHandler: this is commonMain.
        PlatformBackHandler(enabled = true) {
            if (state.isDirty) showDiscardConfirm = true
            else deps.repositoryEditorResults.deliver(navController, controller.buildCancelResult())
        }

        CustomRepositoryEditorScreen(
            state = state,
            onUrlChange = controller::setUrl,
            onPaste = { d.readClipboard()?.let(controller::setUrl) },
            onPackageDirChange = controller::setPackageDir,
            onSave = { deps.repositoryEditorResults.deliver(navController, controller.buildSaveResult()) },
            onDelete = { deps.repositoryEditorResults.deliver(navController, controller.buildDeleteResult()) },
            onUp = { deps.repositoryEditorResults.deliver(navController, controller.buildCancelResult()) },
        )

        if (showDiscardConfirm) {
            AbConfirmDialog(
                title = null,
                message = strings.discardChangesConfirmation,
                confirmText = strings.yes,
                dismissText = strings.no,
                onConfirm = {
                    showDiscardConfirm = false
                    deps.repositoryEditorResults.deliver(navController, controller.buildCancelResult())
                },
                onDismiss = { showDiscardConfirm = false },
            )
        }
    }
}
