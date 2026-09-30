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

package net.bible.sharedui.navigation.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.remember
import androidx.savedstate.read
import net.bible.sharedcore.nav.DocumentResult
import net.bible.sharedcore.nav.KeyChooserResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.PassageResult
import net.bible.sharedcore.navigation.ChooseDictionaryWordController
import net.bible.sharedcore.navigation.ChooseGeneralBookKeyController
import net.bible.sharedcore.navigation.ChooseMapKeyController
import net.bible.sharedcore.navigation.DictRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.anySelectedDeletable
import net.bible.sharedui.PlatformBackHandler
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.nav.popOrExitOnFailedPop
import net.bible.sharedui.navigation.ChooseDictionaryWordScreen
import net.bible.sharedui.navigation.ChooseGeneralBookKeyScreen
import net.bible.sharedui.navigation.ChooseMapKeyScreen
import net.bible.sharedui.navigation.DocumentSelectionScreen
import net.bible.sharedui.navigation.GridChoosePassageScreen
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings

/**
 * [ChooseGeneralBookKeyScreen]'s platform-supplied slots, ported from
 * `ChooseGeneralBookKeyComposeActivity` (still in the tree, unreachable, until nav-graph slice 7
 * Task 13 deletes it -- the standing rule of this phase, which is what keeps a mid-batch bisect
 * possible).
 *
 * - [controllerFor] resolves the page's key list host-side (`page.keyChooserKeys()`, JSword) and
 *   builds the controller around it, reporting the chosen key through [onResult]. It returns
 *   **null** at exactly the point classic's `onCreate` returned early: an EMPTY key list (`:79-83`).
 *   A per-entry factory, not a host-memoised one: the list is read off
 *   `windowControl.activeWindowPageManager.currentGeneralBook`, i.e. off whatever page is active at
 *   the moment the chooser is opened, and classic re-read it on every launch by getting a fresh
 *   Activity. A memoised controller would go on offering the keys of a document the user has since
 *   left.
 * - [emptyResult] is classic's `buildResult(null)` on that same empty-list path -- the FALLBACK
 *   selection (`doc.globalKeyList.first()`), which classic delivered with `setResult` before
 *   finishing. It is a separate slot rather than a nullable return from [controllerFor] because it
 *   is only ever asked for on that branch, and because both halves read the same host-side key list
 *   [controllerFor] just resolved.
 * - [title] is `R.string.general_book`, which is BOTH the screen's own top-bar title and the
 *   manifest `android:label` this destination's window title has to reproduce.
 */
class ChooseGeneralBookKeyDeps(
    val title: String,
    val controllerFor: (onResult: (KeyChooserResult) -> Unit) -> ChooseGeneralBookKeyController?,
    val emptyResult: () -> KeyChooserResult,
)

/**
 * [ChooseMapKeyScreen]'s platform-supplied slots, ported from `ChooseMapKeyComposeActivity`. The
 * same three slots as [ChooseGeneralBookKeyDeps], for the same reasons -- see its kdoc; this
 * destination differs only in the page it reads (`currentMap`), in its title
 * (`R.string.doc_type_map`) and in its result never carrying a `bookAndKey` (classic's
 * `buildResult` sets `key`/`book` unconditionally).
 */
class ChooseMapKeyDeps(
    val title: String,
    val controllerFor: (onResult: (KeyChooserResult) -> Unit) -> ChooseMapKeyController?,
    val emptyResult: () -> KeyChooserResult,
)

/**
 * [ChooseDictionaryWordScreen]'s platform-supplied slots, ported from
 * `ChooseDictionaryWordComposeActivity`.
 *
 * - [controllerFor] returns **null** where classic returned early -- `page.currentDocument == null`
 *   (`:72`) -- and that branch is NOT the same as [ChooseGeneralBookKeyDeps]'s empty-list one:
 *   classic `finish()`ed there with NO `setResult` at all, so the arm pops with nothing published
 *   rather than delivering a fallback. There is no fallback key to deliver when there is no
 *   dictionary.
 * - [loadRows] is classic's `lifecycleScope.launch { withContext(Dispatchers.IO) {
 *   page.cachedGlobalKeyList } }` (`:77-85`), which can take seconds on a large dictionary and so
 *   cannot run inside the composition. **Null means the load FAILED** -- the host has already
 *   logged it, exactly as classic's `catch` did, and the arm's job on that branch is
 *   `controller.showError()`, classic's own reaction. A thrown exception crossing back into the arm
 *   would put the `Log.e` in `commonMain`, which has no logger.
 * - [loadSnippet] is classic's `snippetFor` (`:111-116`) and the three private text helpers under
 *   it: JSword `readOsisFragment` plus jdom2 `Element` walking, none of which can cross into
 *   `commonMain`. The `keyId` is an INDEX into the same host-side list [loadRows] built (see
 *   `KeyRow.keyId`/`DictRow.keyId`), so the host resolves it against the list it is already holding.
 * - [title]/[hint] are `R.string.dictionary` and `R.string.search`; [title] is also the manifest
 *   label this window title reproduces.
 */
class ChooseDictionaryWordDeps(
    val title: String,
    val hint: String,
    val controllerFor: (onResult: (KeyChooserResult) -> Unit) -> ChooseDictionaryWordController?,
    val loadRows: suspend () -> List<DictRow>?,
    val loadSnippet: suspend (keyId: String) -> String,
)

/**
 * [GridChoosePassageScreen]'s platform-supplied slots, ported from
 * `GridChoosePassageComposeActivity`.
 *
 * - [controllerFor] takes the route's `isScripture` and builds the whole controller around the
 *   `GridPassageHostSupport.kt` seams (versification, book/chapter/verse grids, read/memorise
 *   progress) plus the two step fields classic held as Activity state (`selectedBookNo`,
 *   `selectedChapter`). Per-entry, like every other chooser here.
 *
 *   **`navigateToVerse` is deliberately NOT a parameter** (design §6.1.1). Classic read it from an
 *   Intent extra whose single producer (`BibleJavascriptInterface.refChooserDialog`) moves to the
 *   ref-chooser SHEET this slice, so the extra has no producer left; what remains is the fallback
 *   classic already applied to that extra -- `CommonUtils.settings.getBoolean("navigate_to_verse_pref",
 *   false)` (`GridChoosePassageComposeActivity.kt:56`) -- which is a `settings` read, i.e. host-side,
 *   and is applied inside this factory. An argument with no producer would read to a later reader as
 *   a live contract.
 *
 *   The screen's `"title"` extra (`:55`) is dropped for the same producerless reason, and is now held
 *   down by `ClassicPassageGridRemovalGuardTest.noCallSiteStillPutsAPassageGridTitleExtraOnAnIntent`
 *   -- the same containment scan over shipping sources the two ChooseDocument extras get, and with
 *   the same limit: `ClassicRemovalScan.appSources()` does not look at `src/test` or
 *   `src/androidTest`.
 * - [windowTitle] exists because this is the ONE destination of the five whose classic Activity had
 *   **no `android:label`** (`AndroidManifest.xml:119-121`): its window title fell back to the
 *   application label, while the title the SCREEN draws is step-dependent and comes from the
 *   controller's own `GridUi.title`. So the host supplies the application label here rather than the
 *   screen's title, and the window title stays what classic's did.
 */
class GridChoosePassageDeps(
    val windowTitle: String,
    val controllerFor: (
        isScripture: Boolean,
        onResult: (PassageResult) -> Unit,
    ) -> GridChoosePassageController,
)

/**
 * [DocumentSelectionScreen]'s platform-supplied slots for the READING view's document picker, ported
 * from `ChooseDocumentComposeActivity` -- the largest of the five, and the sibling of
 * [net.bible.sharedui.download.nav.DownloadDeps], which drives the same screen in download mode.
 *
 * - [controllerFor] builds the [DocumentSelectionController] around the type filter the arm resolved
 *   from the route, and reports a chosen document through [onResult]. **Per-entry, not
 *   host-memoised** -- the opposite choice from [net.bible.sharedui.download.nav.DownloadDeps.controllerFor]
 *   and for [net.bible.sharedui.download.nav.CloudDocumentsDeps.controllerFor]'s stated reason: this
 *   destination has no child sitting on top of it, but it IS opened repeatedly from the reading view,
 *   and classic gave every open a fresh Activity and therefore a fresh filter/search/selection. A
 *   memoised controller would silently carry the previous open's state into the next one.
 * - [initialTypeFilter] is classic's `initialTypeFilter()` (`:425-432`) minus its dead `addons`
 *   branch (see below): the route's `type` when there is one, else the stored
 *   `selected_document_filter_no`. [persistTypeFilter] is the write half, which classic did inline in
 *   its `onTypeFilterChange` (`:186`).
 * - [loadDocuments] is classic's `lifecycleScope.launch { loadDocuments() }` (`:136-138`): a JSword
 *   `SwordDocumentFacade.documents` scan flattened through `DocRowMapper`, pushed onto the controller
 *   and mirrored host-side in the `docId -> Book` map every seam below reads. The arm owns only WHEN
 *   it runs (once per entry, as classic ran it once per Activity).
 * - [topBarActions] is classic's `OverflowMenu()` (`:373-392`) -- Download / Backup modules /
 *   Install zip. Host-composed in full, like
 *   [net.bible.sharedui.download.nav.CloudDocumentsDeps.topBarActions]: every row needs an
 *   `R.drawable` painter and a `getString`. Download (`:394-408`) and Install zip (`:414-421`) are
 *   destinations of the same graph and are reached in-graph, never by an `awaitIntent` aimed at the
 *   `singleTop` host itself (the shape
 *   [net.bible.sharedui.download.nav.DownloadDeps.reloadCatalogueIfRequested] had to stop using); the
 *   Download row's follow-up runs from [loadDocuments] when this entry composes again.
 * - `confirmDelete`/`confirmDeleteIndex`/`onAbout`/`onUnlock` are absent for
 *   [net.bible.sharedui.download.nav.DownloadDeps]'s own reason: they are the
 *   [DocumentSelectionController]'s constructor seams, wired host-side inside [controllerFor], so the
 *   arm never names them.
 *
 * **Two classic Intent extras are deliberately not carried** (design §6.1): `"search"` (`:132`), which
 * pre-seeded the free-text filter, and `"addons"` (`:428`), which forced the ADDON type filter.
 * Neither had a producer left anywhere in the tree, so carrying them onto route arguments would be
 * inventing a contract rather than preserving one.
 *
 * What is actually GUARDED, and what is not, because the difference matters to whoever reads this
 * next: `ClassicDocumentSelectionRemovalGuardTest.noCallSiteStillPutsADownloadExtraOnAnIntent` scans
 * `ClassicRemovalScan.appSources()`, which skips `src/test` and `src/androidTest` -- so it keeps the
 * two extras out of the SHIPPING sources, and only those. The test and androidTest trees were swept
 * by hand when this destination was ported and had no producer either, but nothing keeps that true;
 * a test-only producer would reappear silently. Widening the scan is a guard-suite change, not this
 * cluster's, and is called out in this task's report.
 */
class ChooseDocumentDeps(
    val title: String,
    val controllerFor: (
        initialTypeFilter: DocTypeFilter,
        onResult: (DocumentResult) -> Unit,
    ) -> DocumentSelectionController,
    val initialTypeFilter: (type: String?) -> DocTypeFilter,
    val persistTypeFilter: (DocTypeFilter) -> Unit,
    val loadDocuments: suspend () -> Unit,
    val topBarActions: @Composable RowScope.() -> Unit,
)

/**
 * Platform-supplied slots the five chooser destinations need but `commonMain` cannot provide. Same
 * top-level shape as [net.bible.sharedui.download.nav.DownloadNavDeps] and
 * [net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps]: [exitHost] and [setWindowTitle] are
 * graph-wide, the result channels next, then one nested holder per destination.
 *
 * **Three channels for five destinations** (design §6.2), and that is the point rather than an
 * economy: `ChooseGeneralBookKey`, `ChooseMapKey` and `ChooseDictionaryWord` already produce a
 * deliberately identical payload -- the classic `Intent`s differ in nothing but which of
 * `bookAndKey` / `key`+`book` they fill -- and a single arm
 * (`MainBibleActivity.onActivityResult`'s `ActivityResultKind.GenBookKey` branch) consumes all
 * three. Five channels would be five names for one contract, and the consumer (Task 9) would have to
 * merge them back.
 */
class ChooserNavDeps(
    /**
     * Leave the host. Reachable only through [popOrExitOnFailedPop] below, i.e. only if a pop
     * somehow fails -- these five destinations are entered from inside the graph, so there is always
     * a parent to pop to. Note the deliberate asymmetry with the channels' own `exitWithResult`,
     * which is a hard `error(...)` on the host side (design §1.1): losing a RESULT silently is a
     * defect worth failing loudly on, whereas a bare up-press has nothing to lose, and leaving the
     * host beats a dead up-arrow.
     */
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) -- not the on-screen top-bar title a screen
     * draws for itself. Called from each destination's `LaunchedEffect(title)`, keyed on the VALUE
     * so a state-derived title cannot go stale, and never from a screen composable. Each of the four
     * labelled destinations passes the string its classic `android:label` carried; `GridChoosePassage`
     * had no label at all, which is what [GridChoosePassageDeps.windowTitle] is for.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * The ONE channel the three key choosers share -- see this class's own kdoc, and
     * [NavResultChannel]'s for the two branches `deliver` can take. Design §1.1 rules out the exit
     * branch for this slice entirely: the host's `exitWithResult` for this channel is an `error(...)`,
     * so a destination reached with no parent entry fails loudly instead of silently packing an
     * Intent for a caller that does not exist.
     */
    val keyChooserResults: NavResultChannel<KeyChooserResult>,
    /** `GridChoosePassage`'s channel -- same in-graph-only contract as [keyChooserResults]. */
    val passageResults: NavResultChannel<PassageResult>,
    /** `ChooseDocument`'s channel -- same in-graph-only contract as [keyChooserResults]. */
    val documentResults: NavResultChannel<DocumentResult>,
    // — CHOOSE GENERAL BOOK KEY —
    val chooseGeneralBookKey: ChooseGeneralBookKeyDeps,
    // — CHOOSE MAP KEY —
    val chooseMapKey: ChooseMapKeyDeps,
    // — CHOOSE DICTIONARY WORD —
    val chooseDictionaryWord: ChooseDictionaryWordDeps,
    // — GRID CHOOSE PASSAGE —
    val gridChoosePassage: GridChoosePassageDeps,
    // — CHOOSE DOCUMENT —
    val chooseDocument: ChooseDocumentDeps,
)

/**
 * Pop to the parent destination, or -- when there is none -- leave the host. The same one-line
 * extension every other cluster graph carries, this cluster's own private copy; the boolean branch
 * itself is [popOrExitOnFailedPop], shared so that "a failed pop still leaves" is decided in one
 * place.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

/**
 * The seven type-filter rows [DocumentSelectionScreen] offers, in classic's order. A private copy of
 * [net.bible.sharedui.download.nav.DownloadNavGraph]'s own private `typeFilterLabels`, which is
 * where the status quo already was: classic `ChooseDocumentComposeActivity.typeFilterLabels`
 * (`:434-442`) and classic `DownloadComposeActivity`'s were two identical hand-maintained copies,
 * and this task ports one of them rather than merging both. Folding the two into one shared helper
 * is a worthwhile follow-up and is called out in this task's report; doing it here would edit
 * another cluster's graph for no gain this task needs.
 */
private fun chooserTypeFilterLabels(strings: Strings): List<Pair<DocTypeFilter, String>> = listOf(
    DocTypeFilter.ALL to strings.docTypeAll,
    DocTypeFilter.BIBLE to strings.docTypeBible,
    DocTypeFilter.COMMENTARY to strings.docTypeCommentary,
    DocTypeFilter.DICTIONARY to strings.docTypeDictionary,
    DocTypeFilter.GENERAL_BOOK to strings.docTypeGeneralBook,
    DocTypeFilter.MAPS to strings.docTypeMaps,
    DocTypeFilter.ADDON to strings.docTypeAddon,
)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The five chooser destinations, registered into the app's single `NavHost` by the host Activity.
 *
 * **Nothing routes to any of them yet.** `ScreenLauncher.MIGRATED` is untouched until nav-graph
 * slice 7 Task 8, the reading destination that consumes the three channels arrives in Task 9, and
 * the five classic Activities stay in the tree, compiling and still reachable by their own
 * `Screen.X` arms, until Task 13. This task builds the destinations and their result plumbing only,
 * which is what makes the batch bisectable: a defect here cannot yet reach a user path.
 *
 * Every result is delivered IN-GRAPH (design §1.1): no destination here sets an Activity result, and
 * each channel's host-side `exitWithResult` is a hard `error(...)` rather than an Intent packing, so
 * an entry with no parent is a loud failure instead of a silent one.
 */
fun NavGraphBuilder.chooserNavGraph(navController: NavHostController, deps: ChooserNavDeps) {
    // ——— CHOOSE GENERAL BOOK KEY ———
    composable(route = NavRoutes.CHOOSE_GENERAL_BOOK_KEY) {
        val d = deps.chooseGeneralBookKey

        val controller = remember {
            d.controllerFor { result -> deps.keyChooserResults.deliver(navController, result) }
        }

        // Classic's `keys.isEmpty()` early exit (`:79-83`): deliver the FALLBACK selection and leave
        // without ever showing a list. Deliberately BEFORE the window-title effect -- a destination
        // that pops in the same frame it composed should not leave its title on the window, since
        // the parent's own `LaunchedEffect(title)` will not re-run to put it back.
        if (controller == null) {
            LaunchedEffect(Unit) { deps.keyChooserResults.deliver(navController, d.emptyResult()) }
            return@composable
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val rows by controller.rows.collectAsState()
        val currentKeyId by controller.currentKeyId.collectAsState()
        val error by controller.error.collectAsState()

        ChooseGeneralBookKeyScreen(
            title = d.title,
            rows = rows,
            currentKeyId = currentKeyId,
            error = error,
            onSelect = controller::select,
            onDismissError = controller::dismissError,
            // Classic's `onNavigateUp = { finish() }`: a bare leave with NO result, which classic
            // never routed through `setResult` at all.
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— CHOOSE MAP KEY ———
    composable(route = NavRoutes.CHOOSE_MAP_KEY) {
        val d = deps.chooseMapKey

        val controller = remember {
            d.controllerFor { result -> deps.keyChooserResults.deliver(navController, result) }
        }

        // Classic `ChooseMapKeyComposeActivity:70-74`, identical to the general-book arm above.
        if (controller == null) {
            LaunchedEffect(Unit) { deps.keyChooserResults.deliver(navController, d.emptyResult()) }
            return@composable
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val rows by controller.rows.collectAsState()
        val currentKeyId by controller.currentKeyId.collectAsState()
        val error by controller.error.collectAsState()

        ChooseMapKeyScreen(
            title = d.title,
            rows = rows,
            currentKeyId = currentKeyId,
            error = error,
            onSelect = controller::select,
            onDismissError = controller::dismissError,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— CHOOSE DICTIONARY WORD ———
    composable(route = NavRoutes.CHOOSE_DICTIONARY_WORD) {
        val d = deps.chooseDictionaryWord

        val controller = remember {
            d.controllerFor { result -> deps.keyChooserResults.deliver(navController, result) }
        }

        // Classic's `if (page.currentDocument == null) { finish(); return }` (`:72`) -- and note it
        // is NOT the general-book arm's branch: classic set no result here, so nothing is published.
        // There is no fallback key to hand back when there is no dictionary at all.
        if (controller == null) {
            LaunchedEffect(Unit) { navController.popOrExit(deps.exitHost) }
            return@composable
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's `lifecycleScope.launch { ... }` key-list load (`:77-85`), now scoped to this
        // entry's composition. Keyed on the controller, i.e. once per entry, exactly as classic ran
        // it once per Activity. A null return is the host's already-logged failure -- see
        // [ChooseDictionaryWordDeps.loadRows] -- and `showError()` is classic's own reaction to it.
        LaunchedEffect(controller) {
            val rows = d.loadRows()
            if (rows == null) controller.showError() else controller.setAllRows(rows)
        }

        val loading by controller.loading.collectAsState()
        val query by controller.query.collectAsState()
        val rows by controller.rows.collectAsState()
        val error by controller.error.collectAsState()

        ChooseDictionaryWordScreen(
            title = d.title,
            hint = d.hint,
            loading = loading,
            query = query,
            rows = rows,
            error = error,
            loadSnippet = { keyId -> d.loadSnippet(keyId) },
            onQueryChange = controller::setQuery,
            onSelect = controller::select,
            onDismissError = controller::dismissError,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— GRID CHOOSE PASSAGE ———
    composable(
        route = NavRoutes.GRID_CHOOSE_PASSAGE_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_IS_SCRIPTURE) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val d = deps.gridChoosePassage

        // Read off the ARGUMENTS, not through `NavRoutes.readGridChoosePassage` (which the plan
        // named): that parser takes a route STRING, and an arm never has one -- `destination.route`
        // is the PATTERN, with the placeholders unsubstituted. The host uses the parser on inbound
        // routes (`onNewIntent`); an arm uses the bundle the library already parsed and decoded. The
        // value is the same either way, including the "anything but a literal true is false" rule,
        // which is what `== "true"` is here.
        val isScripture = backStackEntry.arguments?.read {
            getStringOrNull(NavRoutes.ARG_IS_SCRIPTURE)
        } == "true"

        val controller = remember(isScripture) {
            d.controllerFor(isScripture) { result -> deps.passageResults.deliver(navController, result) }
        }

        LaunchedEffect(d.windowTitle) { deps.setWindowTitle(d.windowTitle) }

        val ui by controller.ui.collectAsState()
        val options by controller.options.collectAsState()

        // Classic's `onBackPressedDispatcher.addCallback(this) { if (!controller.back()) finish() }`
        // (`:84`): the grid's INTERNAL step back-stack (book -> chapter -> verse) is walked first,
        // and only an exhausted one leaves the screen. Always enabled, exactly as classic's callback
        // was -- the branch lives inside the handler, never in its `enabled` flag, because
        // `controller.back()` is the thing that decides.
        PlatformBackHandler(enabled = true) {
            if (!controller.back()) navController.popOrExit(deps.exitHost)
        }

        GridChoosePassageScreen(
            ui = ui,
            options = options,
            onPick = controller::pick,
            onToggle = controller::toggle,
            // Classic's `onNavigateUp` (`:95`) is the same branch as its back callback: the up-arrow
            // walks the step stack too.
            onNavigateUp = { if (!controller.back()) navController.popOrExit(deps.exitHost) },
        )
    }

    // ——— CHOOSE DOCUMENT ———
    composable(
        route = NavRoutes.CHOOSE_DOCUMENT_PATTERN,
        arguments = listOf(
            navArgument(NavRoutes.ARG_DOCUMENT_TYPE) {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val d = deps.chooseDocument
        val strings = LocalStrings.current

        // Read plainly, never through `NavRoutes.decodeArg`: the library has already decoded the
        // argument, and decoding twice is the trap `SearchNavGraph`'s `searchArgs()` kdoc documents.
        val type = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_DOCUMENT_TYPE) }

        val controller = remember(type) {
            d.controllerFor(d.initialTypeFilter(type)) { result ->
                deps.documentResults.deliver(navController, result)
            }
        }

        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        // Classic's `lifecycleScope.launch { loadDocuments() }` (`:136-138`). Keyed on the
        // controller, i.e. once per entry -- classic ran it once per Activity, and this arm's
        // controller is per-entry (see [ChooseDocumentDeps.controllerFor]).
        LaunchedEffect(controller) { d.loadDocuments() }

        val loading by controller.loading.collectAsState()
        val displayed by controller.displayed.collectAsState()
        val grouped by controller.grouped.collectAsState()
        val languages by controller.languages.collectAsState()
        val selectedLanguage by controller.selectedLanguage.collectAsState()
        val shownTypeFilter by controller.shownTypeFilter.collectAsState()
        val query by controller.query.collectAsState()
        val resultCount by controller.resultCount.collectAsState()
        val selectionMode by controller.selectionMode.collectAsState()
        val selectedIds by controller.selectedIds.collectAsState()
        val error by controller.error.collectAsState()
        val dialog by controller.dialog.collectAsState()
        val searchModeActive by controller.searchModeActive.collectAsState()
        val arrangement by controller.arrangement.collectAsState()
        val repositories by controller.repositories.collectAsState()
        val rememberArrangement by controller.rememberArrangement.collectAsState()
        val arrangementIsDefault by controller.arrangementIsDefault.collectAsState()

        // Classic's `onBackPressed` override (`:223-231`): back dismisses what is visually on top, so
        // the selection bar goes before the search bar (AbSelectionScaffold's precedence). ONE gated
        // handler with the branch inside it, never two stacked ones -- two would work only by
        // declaration ORDER (`BookmarkNavGraph.kt` argues this at length).
        PlatformBackHandler(enabled = selectionMode || searchModeActive) {
            if (selectionMode) controller.clearSelection() else controller.closeSearch()
        }

        DocumentSelectionScreen(
            title = d.title,
            downloadMode = false,
            loading = loading,
            isRefreshing = false,
            onRefresh = null,
            grouped = grouped,
            languages = languages,
            selectedLanguage = selectedLanguage,
            typeFilters = chooserTypeFilterLabels(strings),
            selectedTypeFilter = shownTypeFilter,
            query = query,
            resultCount = strings.docFilterResults(resultCount),
            selectionMode = selectionMode,
            selectedIds = selectedIds,
            error = error,
            dialog = dialog,
            topBarActions = { d.topBarActions(this) },
            onQueryChange = controller::setQuery,
            searchModeActive = searchModeActive,
            onOpenSearch = controller::openSearch,
            onCloseSearch = controller::closeSearch,
            onLanguageChange = controller::setLanguage,
            // Classic persisted `selected_document_filter_no` inline before applying the filter
            // (`:184-188`), so "else last saved" keeps working on the next open.
            onTypeFilterChange = { if (controller.pickTypeFilter(it)) d.persistTypeFilter(it) },
            arrangement = arrangement,
            groupKeys = controller.groupKeys,
            repositories = repositories,
            rememberArrangement = rememberArrangement,
            arrangementIsDefault = arrangementIsDefault,
            onMoveSort = controller::moveSortCriterion,
            onToggleSortDirection = controller::toggleSortDirection,
            onGroupByChange = controller::setGroupBy,
            onRepositoryChange = controller::setRepositoryFilter,
            onRememberChange = controller::setRememberArrangement,
            onResetArrangement = controller::resetArrangement,
            onRowClick = { row ->
                if (selectionMode) controller.toggle(row.docId) else controller.select(row.docId)
            },
            onRowLongClick = { row ->
                controller.enterSelection()
                controller.toggle(row.docId)
            },
            // Classic's two no-ops: this screen is not download mode (`:207-208`).
            onDownload = { },
            onCancel = { },
            onSelectionAbout = controller::about,
            onSelectionDelete = controller::delete,
            onSelectionDeleteIndex = controller::deleteIndex,
            onSelectionUnlock = controller::unlock,
            unlockVisible = displayed.firstOrNull { it.docId in selectedIds }?.enciphered == true,
            deleteVisible = anySelectedDeletable(displayed, selectedIds),
            onDismissError = controller::dismissError,
            onConfirmDialog = controller::confirmDialog,
            onDismissDialog = controller::dismissDialog,
            // ChooseDocument never calls controller.askProceed() (Download-only), so
            // ProceedWithDownload never shows here -- wired for signature parity, like every other
            // controller-method callback on this call, per DocumentSelectionScreen's own KDoc.
            onConfirmProceed = controller::confirmProceed,
            onDismissProceed = controller::dismissProceed,
            onNavigateUp = { navController.popOrExit(deps.exitHost) },
            onExitSelection = controller::clearSelection,
        )
    }
}
