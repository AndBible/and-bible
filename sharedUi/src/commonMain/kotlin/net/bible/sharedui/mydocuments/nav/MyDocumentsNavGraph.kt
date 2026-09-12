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

package net.bible.sharedui.mydocuments.nav

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
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.mydocuments.MyDocumentPagesScreen
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop

/**
 * [MyDocumentPagesScreen]'s platform-supplied slots, ported from classic
 * [net.bible.android.view.mydocuments.MyDocumentPagesComposeActivity].
 *
 * This is the cluster's CHILD destination -- reached both from inside the graph (once Task 6 builds
 * the `MyDocuments` arm this file will grow to hold) and from a classic caller entirely outside it,
 * `CurrentGeneralBookPage.kt`'s `doc?.isMyDocument == true ->` branch. It is the batch's second
 * dual-entry destination (the first was `ManageLabels`), which is exactly what
 * [net.bible.sharedui.nav.NavResultChannel] exists for.
 *
 * - [controllerFor] builds the (per-back-stack-entry) [MyDocumentPagesController] around the route's
 *   own `documentId`/`documentInitials` and reports every open-a-page pick through [onResult]. A
 *   factory rather than a host-held instance for the reason every other per-entry factory in this
 *   tree gives: this destination's composition is disposed whenever another sits on top of it, and a
 *   controller built once would go on editing the wrong document.
 *
 *   It ALSO carries the Room access classic held as `MyDocumentPagesComposeActivity` instance
 *   fields (`entityByLong`, the DAO) -- `:app`-only state that cannot cross into `commonMain`, so it
 *   stays entirely on the host side of this factory.
 *
 * - [titleFor] is classic's `getString(R.string.my_document_pages_title, documentName)` (`:99`) --
 *   an Android string RESOURCE with a format argument, which `commonMain` cannot resolve. The
 *   manifest's static `android:label` never carried this formatted string and has no destination
 *   equivalent.
 *
 * - [onImport]/[onExportSelected]/[onExportPage] are the three SAF-backed actions classic ran
 *   through `registerForActivityResult` launchers (plan D4): those launchers must be registered
 *   before `STARTED`, so they -- and the `pendingExportIds` state only they touch -- stay host-side,
 *   beside [controllerFor]'s own wiring of the identical actions into the
 *   [MyDocumentPagesController] it builds. [onExportPage] keeps routing through
 *   `BackupControl.saveOrShare`, with the host's own `chooseDestination` Compose dialog in place of
 *   `saveOrShare`'s platform `AlertDialog` -- the seam the host already built for the reading-plan
 *   export, not a dialog conversion.
 */
class MyDocumentPagesDeps(
    val controllerFor: (
        documentId: String,
        documentInitials: String,
        onResult: (MyDocumentPagesResult) -> Unit,
    ) -> MyDocumentPagesController,
    val titleFor: (documentName: String) -> String,
    val onImport: () -> Unit,
    val onExportSelected: (ids: List<Long>) -> Unit,
    val onExportPage: (id: Long) -> Unit,
)

/**
 * Platform-supplied slots the My-Documents cluster's destinations need but `commonMain` cannot
 * provide. Same top-level shape as [net.bible.sharedui.bookmark.nav.BookmarkNavDeps] and
 * [net.bible.sharedui.download.nav.DownloadNavDeps]: [exitHost] and [setWindowTitle] are graph-wide,
 * one nested holder per destination below.
 *
 * **This class GROWS.** This task builds only [MyDocumentPagesDeps] -- the CHILD destination --
 * because it is the one already provable in both entry modes without the parent existing: entered
 * from outside, it is the host's start destination today; entered from inside, [myDocumentPagesResults]
 * is declared here (rather than waiting for its own producing arm the way
 * [net.bible.sharedui.download.nav.DownloadNavDeps.repositoryEditorResults] was) because it is a
 * `NavResultChannel<MyDocumentPagesResult>` over a result type slice 4 Task 2 already shipped, not a
 * per-destination deps field naming a type that does not exist yet. Task 6 adds exactly two more
 * constructor parameters -- `myDocumentsResults: NavResultChannel<MyDocumentsResult>` and
 * `myDocuments: MyDocumentsDeps` -- plus the `MyDocuments` arm itself, and nothing above changes: a
 * pure addition, the same way `DownloadNavDeps` grew a `progressStatus` field in nav-graph slice 4
 * Task 4 without touching what Task 3 had already built.
 */
class MyDocumentsNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) -- not the on-screen top-bar title a screen
     * draws for itself. Called from each destination's `LaunchedEffect(title)`, keyed on the VALUE
     * so a state-derived title cannot go stale, and never from a screen composable: screen
     * signatures are frozen for this migration.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * How `MyDocumentPages` hands its result back, in either of the two ways it can be entered --
     * see [NavResultChannel]'s own kdoc for the two branches. Both are live as of this task:
     * `CurrentGeneralBookPage.kt` (outside, exit branch) and, once Task 6 lands, `MyDocuments`
     * (inside, publish-and-pop branch).
     */
    val myDocumentPagesResults: NavResultChannel<MyDocumentPagesResult>,
    // — MY DOCUMENT PAGES —
    val myDocumentPages: MyDocumentPagesDeps,
)

/**
 * Pop to the parent destination, or -- when there is none, i.e. this destination is the host's start
 * destination -- leave the host. The same one-line extension every other cluster graph carries; the
 * boolean branch itself is [popOrExitOnFailedPop], shared so that "a failed pop still leaves" is
 * decided in one place. This cluster's OWN private copy, not a shared one with
 * [net.bible.sharedui.download.nav.DownloadNavGraph]'s or
 * [net.bible.sharedui.bookmark.nav.BookmarkNavGraph]'s -- every cluster graph in this tree carries
 * its own.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The My-Documents cluster's destinations. Registered into the app's single `NavHost` by the host
 * Activity. This task builds `MyDocumentPages` alone -- the CHILD half of the pair Task 6 completes
 * with `MyDocuments` -- because it is the one already reachable both ways: `CurrentGeneralBookPage`
 * reaches it from entirely outside the graph today, and `MyDocuments` will reach it from inside once
 * Task 6 lands. [NavRoutes.MY_DOCUMENT_PAGES_PATTERN] is deliberately absent from
 * `ScreenLauncher.MIGRATED`: all three of its arguments are required, so an argument-free entry has
 * nothing meaningful to show (the `Screen.LabelEdit`/`Screen.ManageLabels` precedent).
 */
fun NavGraphBuilder.myDocumentsNavGraph(navController: NavHostController, deps: MyDocumentsNavDeps) {
    // ——— MY DOCUMENT PAGES ———
    composable(
        route = NavRoutes.MY_DOCUMENT_PAGES_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_DOCUMENT_ID) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_DOCUMENT_INITIALS) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_DOCUMENT_NAME) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.myDocumentPages
        val documentId = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_DOCUMENT_ID) }
        val documentInitials =
            backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_DOCUMENT_INITIALS) } ?: ""
        val documentName =
            backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_DOCUMENT_NAME) } ?: ""

        // Classic's `if (docIdStr == null) { finish(); return }` (`:94`). There is no argument-shaped
        // result to construct here -- Cancelled is the honest nearest shape, a plain "nothing
        // happened" exit -- and it goes through the channel like every other exit, rather than a
        // bare popOrExit, so a caller waiting on a result is not left hanging forever.
        if (documentId == null) {
            LaunchedEffect(Unit) { deps.myDocumentPagesResults.deliver(navController, MyDocumentPagesResult.Cancelled) }
            return@composable
        }

        // Classic's `finished` latch (`:74`, `:356-359`): set before every `deliver`, read by the
        // autosave DisposableEffect below so a dispose AFTER an explicit save/open/cancel does not
        // run `applyChanges` a second time -- the controller never clears `dirty` inside `save()`
        // itself, which is what makes the latch genuinely mandatory rather than a nicety.
        val finished = remember { mutableStateOf(false) }

        val controller = remember(documentId, documentInitials) {
            d.controllerFor(documentId, documentInitials) { result ->
                finished.value = true
                deps.myDocumentPagesResults.deliver(navController, result)
            }
        }

        val pages by controller.pages.collectAsState()
        val dirty by controller.dirty.collectAsState()
        val query by controller.query.collectAsState()
        val filtering by controller.filtering.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val selection by controller.selection.collectAsState()
        val totalCount by controller.totalCount.collectAsState()

        // remember(documentName), not a bare call: classic recomputed this once per Activity
        // instance, and a route argument change is the equivalent "new instance" here.
        val title = remember(documentName) { d.titleFor(documentName) }
        LaunchedEffect(title) { deps.setWindowTitle(title) }

        // Classic's `onDetachedFromWindow` autosave (`:356-359`) with its `finished` latch. A window
        // callback has no analogue here -- the arm's disposal is the equivalent moment.
        DisposableEffect(controller) {
            onDispose { if (!finished.value && controller.dirty.value) controller.save() }
        }

        // Classic's `onBackPressed` override (`:347-353`): back dismisses what is visually on top --
        // the selection bar covers the search bar, so selection goes first. ONE gated handler with
        // the branch inside it, never two stacked ones (`BookmarkNavGraph.kt:652-659` argues why).
        // With neither active the handler is disabled and the NavHost's own back does exactly what
        // classic's `super.onBackPressed()` did -- a bare leave with no result, via [onNavigateUp]
        // below (`AbSelectionScaffold` never routes its up-arrow to this handler's branches: the
        // scaffold itself swaps the whole bar for a close icon in selection mode and for the search
        // bar's own back arrow in search mode, so `onNavigateUp` is reachable only in plain mode).
        PlatformBackHandler(enabled = selection.isNotEmpty() || searchModeActive) {
            if (selection.isNotEmpty()) controller.clearSelection() else controller.closeSearch()
        }

        MyDocumentPagesScreen(
            title = title,
            pages = pages,
            dirty = dirty,
            query = query,
            filtering = filtering,
            searchModeActive = searchModeActive,
            totalCount = totalCount,
            onOpenSearch = controller::openSearch,
            onCloseSearch = controller::closeSearch,
            onQueryChange = controller::setQuery,
            onMove = controller::moveItem,
            onOpen = controller::openPage,
            onRename = controller::rename,
            onDelete = controller::delete,
            onExport = controller::export,
            onCreate = controller::createPage,
            onImport = controller::importPage,
            // Classic's Save button, `{ controller.save(); finishOk() }` (`:128`) with NO
            // documentInitials/pageKey extras attached (those are [onOpen]'s alone) -- the honest
            // nav-result shape for that is [MyDocumentPagesResult.Saved], not [MyDocumentPagesResult.Selected].
            onSave = {
                finished.value = true
                controller.save()
                deps.myDocumentPagesResults.deliver(navController, MyDocumentPagesResult.Saved)
            },
            // Classic's Dismiss button, `{ finishCanceled() }` (`:129`, `:344`): RESULT_CANCELED with
            // the kind still tagged, unlike a bare leave through onNavigateUp/hardware-back-with-
            // nothing-active, which classic never routed through setResult at all.
            onCancel = {
                finished.value = true
                deps.myDocumentPagesResults.deliver(navController, MyDocumentPagesResult.Cancelled)
            },
            // Reachable only in plain mode (see the PlatformBackHandler note above), so it needs no
            // selection/search branch of its own -- a bare leave with NO result, classic's
            // `super.onBackPressed()` after neither branch fired, which never called `setResult` at
            // all. Same shape as the bookmark LIST's own `onUp` (`BookmarkNavGraph.kt`).
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
            selection = selection,
            onToggleSelected = controller::toggleSelect,
            onClearSelection = controller::clearSelection,
            onDeleteSelected = controller::deleteSelected,
            onExportSelected = controller::exportSelected,
        )
    }
}
