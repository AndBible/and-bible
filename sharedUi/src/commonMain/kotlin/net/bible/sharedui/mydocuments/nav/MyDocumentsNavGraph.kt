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
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.mydocuments.MyDocumentsController
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.mydocuments.MyDocumentPagesScreen
import net.bible.sharedui.mydocuments.MyDocumentsScreen
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop

/**
 * [MyDocumentPagesScreen]'s platform-supplied slots, ported from classic
 * `MyDocumentPagesComposeActivity` (deleted by nav-graph slice 4 Task 9; a prose citation, not a
 * KDoc link, because the class no longer exists to link to -- the line references below are against
 * that file as it stood at the port).
 *
 * This is the cluster's CHILD destination -- reached both from inside the graph, via the
 * `MyDocuments` arm below (nav-graph slice 4 Task 6), and from a classic caller entirely outside it,
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
 * [MyDocumentsScreen]'s platform-supplied slots, ported from classic `MyDocumentsComposeActivity`
 * (deleted by nav-graph slice 4 Task 9 -- prose, not a KDoc link, for the reason
 * [MyDocumentPagesDeps]'s own kdoc gives). This is the cluster's PARENT
 * destination and the batch's root: reached only as the host's start destination through
 * [net.bible.android.view.ScreenLauncher.MIGRATED] (nav-graph slice 4 Task 6), it navigates IN to
 * [MyDocumentPagesDeps] and must survive that child sitting on top of it.
 *
 * - [controllerFor] is a HOST-MEMOISED factory, not a per-entry one, for
 *   [net.bible.sharedui.bookmark.nav.BookmarksDeps.controllerFor]'s own reason: this destination
 *   NAVIGATES to [NavRoutes.MY_DOCUMENT_PAGES_PATTERN], navigation-compose disposes this arm's
 *   composition while that child is on top, and the controller holds view state classic's
 *   merely-paused Activity kept -- the document list, dirty flag, search query, multi-selection. A
 *   per-entry factory rebuilt on every return from the pages editor would silently drop all of it.
 *   [onResult] mirrors [MyDocumentPagesDeps.controllerFor]'s own parameter for shape parity, but
 *   nothing in [MyDocumentsController] itself calls it: unlike the pages editor's `onOpenPage`,
 *   `MyDocuments`' own "open a document" action is the arm's `onOpen` below (it needs
 *   [routeForPages] and the `NavHostController`, neither of which a host-side factory has), and its
 *   `Selected` result arrives instead through the pages child's own channel -- see the arm's
 *   `myDocumentPagesResults.pending` consumption.
 *
 * - [title] is classic's plain `getString(R.string.my_documents_title)` (`:96`) -- no format
 *   argument, unlike [MyDocumentPagesDeps.titleFor], so a bare `String` rather than a function.
 *
 * - [onImport]/[onExport]/[onExportSelected] are the three SAF-backed actions classic ran through
 *   `registerForActivityResult` launchers (plan D4): those launchers must be registered before
 *   `STARTED`, so they stay host-side, wired into the [MyDocumentsController] [controllerFor]
 *   builds -- the arm reaches them through `controller::importDocuments`/`controller::export`/
 *   `controller::exportSelected`, the same indirection [MyDocumentPagesDeps] uses for its own three.
 *
 * - [importNamePrompt] is classic's `importNamePrompt` Compose state (`:79`), seeded from
 *   `getString(R.string.my_document_new_name, n)` when the SAF launcher returns URIs (`:235-244`)
 *   and cleared by [onConfirmImport]/[onDismissImport] (`:246-258`). A host-created
 *   `MutableStateFlow<String?>`, the shape [net.bible.sharedui.readingplan.nav
 *   .ReadingPlanNavDeps.pendingSelection] established: a flow `remember`ed in the arm would be gone
 *   before the launcher's callback (which fires with no destination composed at all, mid-SAF) could
 *   write to it. **Not** `rememberSaveable` in the arm either, for the same reason -- the value's
 *   owner is the launcher, not the composition.
 *
 * - [routeForPages] is the host building [NavRoutes.myDocumentPages] from its own `entityByLong`
 *   row (`MyDocumentsComposeActivity.kt:211`), because the Room entity the id resolves to is
 *   `:app`-only and cannot cross into `commonMain`. Returns null exactly where classic's
 *   `entityByLong[id] ?: return` did -- an id with nothing behind it -- so the arm's `onOpen` can
 *   no-op the same way.
 */
class MyDocumentsDeps(
    val controllerFor: (onResult: (MyDocumentsResult) -> Unit) -> MyDocumentsController,
    val title: String,
    val onImport: () -> Unit,
    val onExport: (id: Long) -> Unit,
    val onExportSelected: (ids: List<Long>) -> Unit,
    val importNamePrompt: MutableStateFlow<String?>,
    val onConfirmImport: (name: String) -> Unit,
    val onDismissImport: () -> Unit,
    val routeForPages: (id: Long) -> String?,
)

/**
 * Platform-supplied slots the My-Documents cluster's destinations need but `commonMain` cannot
 * provide. Same top-level shape as [net.bible.sharedui.bookmark.nav.BookmarkNavDeps] and
 * [net.bible.sharedui.download.nav.DownloadNavDeps]: [exitHost] and [setWindowTitle] are graph-wide,
 * one nested holder per destination below.
 *
 * Nav-graph slice 4 Task 5 built [myDocumentPages] and [myDocumentPagesResults] alone, provable in
 * both of [MyDocumentPagesDeps]'s entry modes without the parent existing. Task 6 adds
 * [myDocumentsResults] and [myDocuments] -- the parent destination and the channel it produces on --
 * a pure addition: nothing above changes, the same way `DownloadNavDeps` grew a `progressStatus`
 * field in nav-graph slice 4 Task 4 without touching what Task 3 had already built.
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
     * `CurrentGeneralBookPage.kt` (outside, exit branch) and `MyDocuments` (inside, publish-and-pop
     * branch -- the arm below consumes it).
     */
    val myDocumentPagesResults: NavResultChannel<MyDocumentPagesResult>,
    /**
     * How `MyDocuments` hands its own result back. Originally documented here as having only ONE
     * entry mode (the batch's root, reached solely as the host's start destination, so
     * [NavResultChannel.deliver] always took the exit branch and the pending/consume half of its
     * contract was dead code for this field) -- **F60 made that false.** `onSwitchDocument`'s
     * `navigate(NavRoutes.myDocuments()) { popUpTo(MY_DOCUMENT_PAGES_PATTERN) { inclusive = true };
     * launchSingleTop = true }` pops the leaving `Pages` entry and, when there is no existing
     * `MyDocuments` entry to reuse (the reading-view entry path, `CurrentGeneralBookPage.kt:159-169`,
     * where `Pages` sits directly on `reading` with nothing of this cluster's below it), pushes a
     * FRESH `MyDocuments` entry on top of `reading` -- a second, inside-graph entry mode, with
     * `reading` as its parent. [NavResultChannel.deliver] branches on
     * `navController.previousBackStackEntry`, so in that shape it now takes the publish-to-[pending]
     * branch instead of the exit branch; `NavHostComposeActivity`'s `readingResultCollectors` already
     * wires a `ReadingResultKind.MyDocuments` collector against this exact field (added when
     * [myDocumentPagesResults] first needed it, for symmetry), so consumption was always live -- only
     * the STACK SHAPE that reaches it is new. Declared as a channel anyway, not a bare host lambda,
     * for the same reason every other result-producing destination in this tree is: one mechanism,
     * not two.
     */
    val myDocumentsResults: NavResultChannel<MyDocumentsResult>,
    // — MY DOCUMENT PAGES —
    val myDocumentPages: MyDocumentPagesDeps,
    // — MY DOCUMENTS —
    val myDocuments: MyDocumentsDeps,
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
 * Activity. Nav-graph slice 4 Task 5 built `MyDocumentPages` alone -- the CHILD half of the pair --
 * because it was the one already reachable both ways: `CurrentGeneralBookPage` reaches it from
 * entirely outside the graph, and `MyDocuments` (Task 6, below) reaches it from inside.
 * [NavRoutes.MY_DOCUMENT_PAGES_PATTERN] is deliberately absent from `ScreenLauncher.MIGRATED`: all
 * three of its arguments are required, so an argument-free entry has nothing meaningful to show
 * (the `Screen.LabelEdit`/`Screen.ManageLabels` precedent).
 */
fun NavGraphBuilder.myDocumentsNavGraph(navController: NavHostController, deps: MyDocumentsNavDeps) {
    // ——— MY DOCUMENTS ———
    composable(route = NavRoutes.MY_DOCUMENTS_PATTERN) {
        val d = deps.myDocuments

        // Classic's `finished` latch (`:71`, `:452-453`, `:465-468`): set before every `deliver`,
        // read by the autosave DisposableEffect below so a dispose AFTER an explicit save/cancel/
        // relayed-selection does not run a second, redundant `save()` -- the controller never
        // clears `dirty` inside `save()` itself, same as `MyDocumentPages` (Task 5).
        val finished = remember { mutableStateOf(false) }

        // HOST-MEMOISED (see MyDocumentsDeps.controllerFor's kdoc), so `remember` here is only
        // "build it if this is the first composition" -- the host returns the SAME controller on
        // every later call, across the pages editor sitting on top of this destination and popping
        // back off it.
        val controller = remember {
            d.controllerFor { result ->
                finished.value = true
                deps.myDocumentsResults.deliver(navController, result)
            }
        }

        val documents by controller.documents.collectAsState()
        val dirty by controller.dirty.collectAsState()
        val query by controller.query.collectAsState()
        val filtering by controller.filtering.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val selection by controller.selection.collectAsState()
        val totalCount by controller.totalCount.collectAsState()
        val importNamePrompt by d.importNamePrompt.collectAsState()

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's `onDetachedFromWindow` autosave (`:465-468`) with its `finished` latch. A window
        // callback has no analogue here -- the arm's disposal is the equivalent moment, which
        // includes the composition being torn down while `MyDocumentPages` sits on top of it (the
        // arm's own `onOpen` below already saves before navigating in that case, so this is the
        // redundant-but-harmless second write classic's own timing never had -- see the task
        // report).
        DisposableEffect(controller) {
            onDispose { if (!finished.value && controller.dirty.value) controller.save() }
        }

        // The pages child's result, relayed. Classic's `pagesLauncher` callback relayed the two
        // extras into its own `resultIntent` and called `finishOk()` (`:222-233`); the graph
        // equivalent reads them off the channel `MyDocumentPages` published to before popping.
        val pendingPages by deps.myDocumentPagesResults.pending.collectAsState()
        LaunchedEffect(pendingPages) {
            if (pendingPages == null) return@LaunchedEffect
            val result = deps.myDocumentPagesResults.consume() ?: return@LaunchedEffect
            if (result is MyDocumentPagesResult.Selected) {
                finished.value = true
                deps.myDocumentsResults.deliver(
                    navController,
                    MyDocumentsResult.Selected(result.documentInitials, result.pageKey),
                )
            }
        }

        // Classic's `onBackPressed` override (`:456-463`): back dismisses what is visually on top --
        // the selection bar covers the search bar, so selection goes first. ONE gated handler with
        // the branch inside it, never two stacked ones (`BookmarkNavGraph.kt:652-659` argues why).
        PlatformBackHandler(enabled = selection.isNotEmpty() || searchModeActive) {
            if (selection.isNotEmpty()) controller.clearSelection() else controller.closeSearch()
        }

        MyDocumentsScreen(
            title = d.title,
            documents = documents,
            dirty = dirty,
            query = query,
            filtering = filtering,
            searchModeActive = searchModeActive,
            totalCount = totalCount,
            onOpenSearch = controller::openSearch,
            onCloseSearch = controller::closeSearch,
            onQueryChange = controller::setQuery,
            onMove = controller::moveItem,
            // Classic built an Intent through `targetFor` and launched it with `pagesLauncher`
            // (`:210-233`). Now a plain `navigate`: [MyDocumentsDeps.routeForPages] resolves the id
            // to a route (or null, classic's `entityByLong[id] ?: return`) through the host's own
            // Room-backed map, since the entity itself cannot cross into `commonMain`.
            onOpen = { id ->
                val route = d.routeForPages(id)
                if (route != null) {
                    // Classic auto-saved before leaving rather than prompting (:212-213).
                    //
                    // LATCHED, like every other exit from this arm (slice 4 final-review fix M2).
                    // The navigate disposes this composition, which fires the autosave
                    // `DisposableEffect` below -- and `save()` does not clear `dirty` (D6's whole
                    // premise), so without the latch that dispose would run `applyChanges` a SECOND
                    // time: a second delete pass and a second `AiDocPagesChangedEvent` on top of the
                    // save the user just got. `finished` is a plain `remember`, so popping back
                    // rebuilds this composition with it reset to false and the autosave is armed
                    // again for the next leave.
                    finished.value = true
                    if (controller.dirty.value) controller.save()
                    navController.navigate(route)
                }
            },
            onRename = controller::rename,
            onEditDescription = controller::editDescription,
            onDelete = controller::delete,
            onExport = controller::export,
            onCreate = controller::create,
            onImport = controller::importDocuments,
            // Classic's Save button, `{ controller.save(); finishOk() }` (`:126`) -- `finishOk()`
            // with NO documentInitials/pageKey extras, since those are the pages relay's alone; the
            // honest nav-result shape for that is [MyDocumentsResult.Saved].
            onSave = {
                finished.value = true
                controller.save()
                deps.myDocumentsResults.deliver(navController, MyDocumentsResult.Saved)
            },
            // Classic's Dismiss button, `{ finishCanceled() }` (`:127`).
            onCancel = {
                finished.value = true
                deps.myDocumentsResults.deliver(navController, MyDocumentsResult.Cancelled)
            },
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
            importNamePrompt = importNamePrompt,
            onConfirmImport = d.onConfirmImport,
            onDismissImport = d.onDismissImport,
            selection = selection,
            onToggleSelected = controller::toggleSelect,
            onClearSelection = controller::clearSelection,
            onDeleteSelected = controller::deleteSelected,
            onExportSelected = controller::exportSelected,
        )
    }

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
            // F60 fix round 1: `popUpTo(MY_DOCUMENT_PAGES_PATTERN) { inclusive = true }` ALONE
            // removes only the LEAVING Pages entry -- if a `MyDocuments` entry is already sitting
            // below it (the normal `MyDocuments -> open doc -> Pages` path), a bare `navigate`
            // pushes a SECOND, redundant `MyDocuments` entry on top of it instead of reusing the
            // one that's there, so the stack grows by one on every switch. `launchSingleTop = true`
            // fixes that: after the pop, if the (now-top) entry left behind already IS `MyDocuments`
            // (the normal path), `navigate` reuses it in place rather than stacking a duplicate; if
            // it is not (the reading-view path, `CurrentGeneralBookPage.kt:159-169`, where Pages sits
            // directly on `reading` with no `MyDocuments` below it), a fresh `MyDocuments` entry is
            // pushed exactly once, and every switch after that reuses IT the same way. Either way
            // the Pages entry being left is always popped, so back from the list goes wherever the
            // page list was entered from -- and the stack is the same size after one switch as after
            // five.
            onSwitchDocument = {
                navController.navigate(NavRoutes.myDocuments()) {
                    popUpTo(NavRoutes.MY_DOCUMENT_PAGES_PATTERN) { inclusive = true }
                    launchSingleTop = true
                }
            },
            selection = selection,
            onToggleSelected = controller::toggleSelect,
            onClearSelection = controller::clearSelection,
            onDeleteSelected = controller::deleteSelected,
            onExportSelected = controller::exportSelected,
        )
    }
}
