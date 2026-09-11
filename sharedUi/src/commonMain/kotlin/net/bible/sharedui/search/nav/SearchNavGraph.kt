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

package net.bible.sharedui.search.nav

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import kotlinx.coroutines.delay
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchFormController
import net.bible.sharedcore.search.SearchIndexProgressController
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.search.SearchIndexProgressScreen
import net.bible.sharedui.search.SearchIndexScreen
import net.bible.sharedui.search.SearchScreen

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The chain's arguments (plan D3)
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The five arguments the classic `Search -> SearchIndex -> SearchIndexProgress -> SearchResults`
 * chain forwarded as an OPAQUE BUNDLE (plan D3), named.
 *
 * Classic forwarded the whole intent twice — `SearchIndexComposeActivity.kt:72-75`
 * (`putExtras(intent)` plus a `SEARCH_DOCUMENT` override) and
 * `SearchIndexProgressComposeActivity.kt:146` (`putExtras(intent.extras!!)` into whichever of four
 * screens it routed to). **In route-world there is no bundle**: an argument nobody names is an
 * argument silently lost. So each hop rebuilds the next route from these five, and this one type
 * plus [searchArgs] is what stops the three hops drifting apart.
 *
 * File-private on purpose — it is the internal shape of this cluster's forwarding, not part of
 * `:sharedUi`'s surface. Slice 5's three remaining destinations live in this same file and read it
 * the same way.
 */
private data class SearchArgs(
    val searchText: String?,
    val highlightText: String?,
    val searchDocument: String?,
    val selectedTranslations: List<String>,
    val isStrongsSearch: Boolean,
)

/**
 * Reads [SearchArgs] off a back-stack entry.
 *
 * **No [NavRoutes.decodeArg] here, deliberately — do not "fix" this back.** The navigation library
 * has ALREADY percent-decoded these values by the time they reach `arguments`:
 * `NavDeepLink.getMatchingQueryArguments` obtains them through `android.net.Uri.getQueryParameters`,
 * which returns `Uri.decode(...)`-ed values (verified against `navigation-common-android` 2.9.7
 * during Task 3). A second `decodeArg` pass would DOUBLE-DECODE, and for free text — which a search
 * query is — that is not cosmetic but a crash: a `searchText` of `100%` is emitted by
 * [NavRoutes.encodeArg] as `100%25`, the library hands back `100%`, and `decodeArg("100%")` then
 * trips its own truncated-escape `require`. A literal `%` is perfectly ordinary in a search box.
 *
 * [NavRoutes.ARG_SELECTED_TRANSLATIONS] goes straight into [NavRoutes.decodeList] for the same
 * reason: the value the library hands back is already the decoded `A,B,C` join, so splitting it is
 * all that is left to do.
 */
private fun NavBackStackEntry.searchArgs(): SearchArgs = SearchArgs(
    searchText = arguments?.read { getStringOrNull(NavRoutes.ARG_SEARCH_TEXT) },
    highlightText = arguments?.read { getStringOrNull(NavRoutes.ARG_SEARCH_HIGHLIGHT_TEXT) },
    searchDocument = arguments?.read { getStringOrNull(NavRoutes.ARG_SEARCH_DOCUMENT) },
    selectedTranslations = NavRoutes.decodeList(
        arguments?.read { getStringOrNull(NavRoutes.ARG_SELECTED_TRANSLATIONS) }
    ),
    isStrongsSearch = arguments?.read { getBooleanOrNull(NavRoutes.ARG_IS_STRONGS_SEARCH) } ?: false,
)

/** The four chain arguments every hop registers. [NavRoutes.ARG_IS_STRONGS_SEARCH] is added separately. */
private fun chainArguments() = listOf(
    navArgument(NavRoutes.ARG_SEARCH_TEXT) { type = NavType.StringType; nullable = true; defaultValue = null },
    navArgument(NavRoutes.ARG_SEARCH_HIGHLIGHT_TEXT) { type = NavType.StringType; nullable = true; defaultValue = null },
    navArgument(NavRoutes.ARG_SEARCH_DOCUMENT) { type = NavType.StringType; nullable = true; defaultValue = null },
    navArgument(NavRoutes.ARG_SELECTED_TRANSLATIONS) { type = NavType.StringType; nullable = true; defaultValue = null },
    // Present in every route NavRoutes builds (searchChainRoute emits it as `required`), so a
    // BoolType with a default rather than a nullable String: absence can only mean a hand-written
    // route, and classic's `getBooleanExtra(..., false)` said exactly that absence == false.
    navArgument(NavRoutes.ARG_IS_STRONGS_SEARCH) { type = NavType.BoolType; defaultValue = false },
)

/** `SEARCH_INDEX_PROGRESS_PATTERN` carrying these five arguments forward unchanged. */
private fun SearchArgs.indexProgressRoute(): String = NavRoutes.searchIndexProgress(
    searchText = searchText,
    highlightText = highlightText,
    searchDocument = searchDocument,
    selectedTranslations = selectedTranslations,
    isStrongsSearch = isStrongsSearch,
)

/**
 * `SEARCH_RESULTS_PATTERN` carrying these five forward. [searchText] is required there (a results
 * screen with nothing to search for is not a state), so every caller checks it is non-empty first —
 * which is exactly the branch classic's `StringUtils.isNotEmpty(...SEARCH_TEXT)` already made.
 */
private fun SearchArgs.resultsRoute(): String = NavRoutes.searchResults(
    searchText = searchText.orEmpty(),
    highlightText = highlightText,
    searchDocument = searchDocument,
    selectedTranslations = selectedTranslations,
    isStrongsSearch = isStrongsSearch,
)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// Deps
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * What classic `SearchComposeActivity.onCreate` read off JSword before it could draw the form:
 * the window/screen [title] (`R.string.search_in` with the current document's abbreviation) and the
 * [currentBookName] the section chooser labels its "current book" option with.
 */
data class SearchFormSetup(
    val title: String,
    val currentBookName: String,
)

/** The installed Bibles and the persisted selection, as one snapshot. See [SearchFormDeps.loadTranslations]. */
data class TranslationSelection(
    /** `initials to abbreviation`, sorted by abbreviation — [SearchScreen]'s `availableTranslations`. */
    val available: List<Pair<String, String>>,
    val selected: List<String>,
)

/**
 * Where a submitted search form must go next — the data half of classic
 * `SearchComposeActivity.onSubmit`'s branch. The DECISION's inputs are computed host-side (they are
 * all JSword: index status, `decorateSearchString`, `highlightSearchString`); the branch itself is
 * taken in this graph, because both arms are `navController.navigate(...)`.
 */
sealed interface SearchSubmission {
    /**
     * `bibleSearchService.validateIndex(request)` said no. [documentId] is the FIRST selected
     * translation without a usable index (classic's `firstOrNull { indexStatus != DONE }`), or null
     * when none could be identified — in which case the index prompt falls back to the current
     * page's document, exactly as classic `SearchIndexComposeActivity.kt:46` does.
     */
    data class NeedsIndex(val documentId: String?) : SearchSubmission

    /**
     * Everything is indexed. [decoratedQuery] is `decorate(request)`, [highlightText] the
     * section-less `highlightSearchString(...)`, [searchDocument] the CURRENT page's document
     * initials (classic passed that, not a selected translation) and [translationIds] the request's
     * own selection.
     */
    data class Results(
        val decoratedQuery: String,
        val highlightText: String?,
        val searchDocument: String?,
        val translationIds: List<String>,
    ) : SearchSubmission
}

/**
 * `SEARCH_FORM_PATTERN`'s platform-supplied slots, ported from classic `SearchComposeActivity`.
 *
 * - [prepare] is classic's `onCreate` preamble: the `search-last-used` timestamp, the current
 *   document gate (it returns **null** when there is no document to search — classic's immediate
 *   `finish()`) and the two strings in [SearchFormSetup]. It takes the route's restored
 *   `bibleBook` argument because classic seeded `currentBookName` from its history extra before
 *   falling back to `searchControl.currentBookName`.
 * - [loadTranslations] is BOTH classic's `onCreate` seed and its `onResume` re-seed (`:128-143`),
 *   which are the same two reads: every `SwordBook` sorted by abbreviation, and the persisted
 *   selection falling back to the current document. The graph calls it once when it builds the
 *   controller and again from a `LifecycleEventEffect(ON_RESUME)` — a lifecycle-aware effect, not
 *   at composition, because the reason classic re-read it is that the RESULTS screen's document
 *   selector may have changed the persisted choice while this screen sat in the back stack.
 * - [controllerFor] builds the [SearchFormController] host-side: its three constructor lambdas
 *   (`persistTranslations`, `persistRecentTerms`, `loadRecentTerms`) all reach app settings.
 * - [submit] is classic `onSubmit`'s JSword half, returning a [SearchSubmission] for the graph to
 *   branch on rather than navigating itself.
 */
class SearchFormDeps(
    val prepare: (restoredBibleBook: String?) -> SearchFormSetup?,
    val loadTranslations: () -> TranslationSelection,
    val controllerFor: (currentBookName: String) -> SearchFormController,
    val submit: (SearchRequest) -> SearchSubmission,
)

/** The document an index prompt is about. See [SearchIndexPromptDeps.resolve]. */
data class IndexTarget(
    val documentId: String,
    val documentName: String,
    /** `SearchIndexService.hasIndex(documentId)` — an existing index makes this a REBUILD prompt. */
    val isRebuild: Boolean,
)

/**
 * `SEARCH_INDEX_PATTERN`'s platform-supplied slots, ported from classic `SearchIndexComposeActivity`
 * (which has no controller at all — it calls `SearchIndexService` directly).
 *
 * - [resolve] is classic's `documentToIndex` getter: the route's `searchDocument` if it names an
 *   installed document, else the CURRENT page's document (`:44-49`). Null means neither existed —
 *   classic's `finish()`. The resolved [IndexTarget.documentId] matters beyond display: it is what
 *   the next hop's `searchDocument` is overridden with, so a prompt entered with no argument still
 *   hands the progress screen a concrete document to watch.
 * - [createIndex] is `SearchIndexService.createIndex(docId)` — kickoff only; the progress
 *   destination watches the result.
 * - [title] is `R.string.search_index`, screen and window.
 */
class SearchIndexPromptDeps(
    val resolve: (searchDocument: String?) -> IndexTarget?,
    val createIndex: (documentId: String) -> Unit,
    val title: String,
)

/**
 * How classic `SearchIndexProgressComposeActivity.jobFinished` (`:132-161`) decided what to do once
 * a job reported itself finished — see [SearchIndexProgressDeps.awaitIndexed] for the blocking wait
 * that produces it.
 */
enum class IndexOutcome {
    /** The document reached `IndexStatus.DONE`. Route on to the search form or the results. */
    INDEXED,

    /** As [INDEXED], but the document is an EPUB — classic picked the epub twin of each destination. */
    INDEXED_EPUB,

    /**
     * Still not `DONE` after the wait, and EVERY job has finished, so nothing is going to change it:
     * classic's `Log.e` + `controller.showError()`.
     */
    FAILED,

    /**
     * Still not `DONE`, but other jobs are running. Classic did nothing at all in this branch —
     * another `jobFinished` will arrive. Named rather than folded into [FAILED] so the graph's
     * `when` states that doing nothing is deliberate.
     */
    STILL_RUNNING,
}

/**
 * `SEARCH_INDEX_PROGRESS_PATTERN`'s platform-supplied slots — the hardest of the three, because
 * every one of them is JSword's `JobManager`.
 *
 * - [requestNotificationPermission] is classic `onResume`'s
 *   `CommonUtils.requestNotificationPermission(this)` (`:82`), which needs a real `Activity`. It is
 *   a plain `() -> Unit` here; the host launches the suspend call in its own scope.
 * - [observeJobs] is the `onResume`/`onPause` PAIR (`:80-96`) as one call: it runs classic's
 *   initial `refreshJobs()`, registers the `WorkListener`, and returns the un-registration. The
 *   pair is mandatory — a leaked listener holds `onJobs`/`onJobFinished`, which close over this
 *   destination's controller and its navigation, so leaking it leaks the destination. The
 *   destination drives it from [LifecycleResumeEffect], whose `onPauseOrDispose` is exactly
 *   classic's `onPause`, plus the disposal case an Activity never had to think about.
 *   `onJobFinished` fires ONCE per newly-finished job: the de-duplication (classic's
 *   `finishedJobs: HashSet<Progress>`) is keyed on JSword `Progress` identities and so stays
 *   host-side.
 * - [awaitIndexed] is classic's `jobFinished` wait: "give the document up to 12 secs to reload —
 *   the Progress declares itself finished before the index status has been changed", six rounds of
 *   a BLOCKING `CommonUtils.pause(2)`. It is `suspend` here and the host runs the blocking loop on
 *   a background dispatcher, so no composition ever blocks; the graph awaits it in a
 *   `LaunchedEffect` and then routes on the [IndexOutcome] it returns. The decision of WHERE to go
 *   stays in the graph — the outcome only reports index status, epub-ness and whether any job is
 *   still running.
 * - [title] is `R.string.search_index`, screen and window.
 */
class SearchIndexProgressDeps(
    val requestNotificationPermission: () -> Unit,
    val observeJobs: (onJobs: (List<ProgressJob>) -> Unit, onJobFinished: () -> Unit) -> () -> Unit,
    val awaitIndexed: suspend (documentId: String?) -> IndexOutcome,
    val title: String,
)

/**
 * Platform-supplied slots the search destinations need but `commonMain` cannot provide.
 * Mirrors [net.bible.sharedui.readingplan.nav.ReadingPlanNavDeps]: [exitHost], [setWindowTitle] and
 * the two history-route halves are graph-wide and sit at the top level, one nested holder per
 * destination below.
 *
 * **Three holders, not six.** Task 4 owns `SEARCH_FORM_PATTERN`, `SEARCH_INDEX_PATTERN` and
 * `SEARCH_INDEX_PROGRESS_PATTERN`; slice 5's task ADDS its own three (results, epub search, epub
 * results) to the end of this constructor. A holder cannot be declared for a destination that does
 * not exist yet — the host would have nothing to construct it from.
 */
class SearchNavDeps(
    val exitHost: () -> Unit,
    /**
     * Sets the HOST WINDOW's title (Recents, TalkBack) — not the on-screen top-bar title a screen
     * draws for itself. Called from each destination's `LaunchedEffect(title)`, keyed on the VALUE
     * so a state-derived title cannot go stale, and never from inside a screen composable: screen
     * signatures are frozen for this migration.
     */
    val setWindowTitle: (String) -> Unit,
    /**
     * Reports the route `HistoryManager` should re-launch for what is CURRENTLY on screen. Four of
     * this cluster's six destinations are `integrateWithHistoryManager = true` classically and
     * mutated their own intent to say which search they were showing; a nav destination has no
     * intent, so the host builds one from this route instead
     * (`NavHostComposeActivity.intentForHistoryList`).
     *
     * [owner] is an opaque token the destination `remember`s — one identity per back-stack entry
     * composition — which the host records alongside the route so [clearHistoryRoute] can compare
     * against it.
     */
    val setHistoryRoute: (owner: Any, route: String) -> Unit,
    /**
     * Clears the history route, but ONLY if [owner] is still the entry that published it — a
     * compare-and-clear, never a plain clear.
     *
     * navigation-compose composes the ENTERING destination (and runs its `LaunchedEffect`s) BEFORE
     * it disposes the exiting one. So on any A -> B where both publish a route, an unconditional
     * `DisposableEffect(Unit) { onDispose { clear() } }` on A runs after B has already published,
     * and wipes it: B shows with `isIntegrateWithHistoryManager == false` and nothing notices.
     * `Search -> SearchResults` in this very cluster is exactly that shape.
     */
    val clearHistoryRoute: (owner: Any) -> Unit,
    // — SEARCH FORM —
    val searchForm: SearchFormDeps,
    // — SEARCH INDEX PROMPT —
    val searchIndex: SearchIndexPromptDeps,
    // — SEARCH INDEX PROGRESS —
    val searchIndexProgress: SearchIndexProgressDeps,
)

// ——————————————————————————————————————————————————————————————————————————————————————————————
// Up-navigation
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * Whether an up-navigation attempt that just tried to pop the back stack should fall through to
 * exiting the host outright, given [popped] (`navController.popBackStack()`'s result).
 *
 * Duplicated from `AiNavGraph.kt` / `ReadingPlanNavGraph.kt` rather than shared: each cluster's
 * graph file is self-contained, and widening one copy into a cross-package utility would make an
 * implementation detail of that file part of `:sharedUi`'s surface. `internal` rather than
 * `private` for the same reason as the other copies — it keeps a plain-JUnit mirror test possible
 * without a real `NavHostController` (which needs an Android `Context` to construct, and
 * `:sharedUi` has no Robolectric-style runner). As in slice 3, THIS copy has no test of its own
 * yet; `AiNavGraphPopOrExitTest` covers the AI cluster's identical one.
 */
internal fun popOrExitOnFailedPop(popped: Boolean, exitHost: () -> Unit) {
    if (!popped) exitHost()
}

/**
 * Up-navigation for a destination that may be the graph's START destination. `popBackStack()`
 * returns false and does nothing on a single-entry back stack, so a bare `popBackStack()` binding
 * makes the up-arrow a dead button whenever the destination was entered directly. Both branches are
 * live here: the search form and the index prompt are each reachable directly from
 * `ScreenLauncher` AND as a child of the other.
 */
private fun NavHostController.popOrExit(exitHost: () -> Unit) {
    popOrExitOnFailedPop(popBackStack(), exitHost)
}

// ——————————————————————————————————————————————————————————————————————————————————————————————
// The graph
// ——————————————————————————————————————————————————————————————————————————————————————————————

/**
 * The search cluster's destinations. Registered into the app's single `NavHost` by the host
 * Activity, alongside the AI and reading-plan clusters.
 *
 * Three of the six live here (slice 5 adds results, epub search and epub results). The rule that
 * shapes this file is plan D3: the classic chain forwarded an opaque bundle from hop to hop, and a
 * route has no bundle — so every hop rebuilds the next route from [SearchArgs], and the index hop
 * additionally overrides `searchDocument` with the document it actually indexed.
 */
fun NavGraphBuilder.searchNavGraph(navController: NavHostController, deps: SearchNavDeps) {
    // ——— SEARCH FORM ———
    composable(
        route = NavRoutes.SEARCH_FORM_PATTERN,
        arguments = listOf(
            // The form's four HISTORY-RESTORE arguments, not the chain's five: classic Search wrote
            // "Search"/"Words"/"Selection"/"BibleBook" onto its own intent so HistoryManager could
            // bring the filled-in Find screen back (`SearchComposeActivity.kt:154-157`).
            navArgument(NavRoutes.ARG_SEARCH_TEXT) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_SEARCH_TYPE) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_SEARCH_SECTION) { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument(NavRoutes.ARG_BIBLE_BOOK) { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    ) { backStackEntry ->
        val d = deps.searchForm
        // Read plainly — no decodeArg. See searchArgs()'s kdoc: the library already decoded these,
        // and a query is free text where a second pass would crash on a literal '%'.
        val restoredText = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_SEARCH_TEXT) }
        val restoredType = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_SEARCH_TYPE) }
        val restoredSection = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_SEARCH_SECTION) }
        val restoredBibleBook = backStackEntry.arguments?.read { getStringOrNull(NavRoutes.ARG_BIBLE_BOOK) }

        val setup = remember(restoredBibleBook) { d.prepare(restoredBibleBook) }
        if (setup == null) {
            // Classic's "no document to search -> finish() immediately". Nothing was pushed on top
            // of us, so leaving means leaving the host.
            LaunchedEffect(Unit) { navController.popOrExit(deps.exitHost) }
        } else {
            LaunchedEffect(setup.title) { deps.setWindowTitle(setup.title) }

            val controller = remember(setup) {
                d.controllerFor(setup.currentBookName).also { c ->
                    val translations = d.loadTranslations()
                    c.setAvailableTranslations(translations.available)
                    // Seed, not set: loading must not re-persist what it just read.
                    c.seedTranslations(translations.selected)
                    // The history-restore arguments. An UNKNOWN searchType/searchSection name is
                    // IGNORED, not an error — classic used firstOrNull and fell through to the
                    // controller's own default.
                    restoredText?.takeIf { it.isNotEmpty() }?.let(c::setQuery)
                    restoredType
                        ?.let { name -> SearchType.entries.firstOrNull { it.name == name } }
                        ?.let(c::setSearchType)
                    restoredSection
                        ?.let { name -> SearchBibleSection.entries.firstOrNull { it.name == name } }
                        ?.let(c::setBibleSection)
                }
            }

            // Classic Search.onResume (`:128-143`): the RESULTS screen's document selector may have
            // changed the persisted selection while this screen sat in the back stack, so the list
            // is re-read on every resume — including a return through the back stack, which is the
            // only form that case takes inside a nav graph. A lifecycle-aware effect, not a bare
            // LaunchedEffect, for exactly that reason.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                val translations = d.loadTranslations()
                controller.setAvailableTranslations(translations.available)
                controller.seedTranslations(translations.selected)
            }

            val query by controller.query.collectAsState()
            val searchType by controller.searchType.collectAsState()
            val bibleSection by controller.bibleSection.collectAsState()
            val availableTranslations by controller.availableTranslations.collectAsState()
            val selectedTranslationIds by controller.selectedTranslationIds.collectAsState()
            val recentTerms by controller.recentTerms.collectAsState()

            // THE HISTORY SEAM. Classic wrote the four restore extras onto its own intent inside
            // onSubmit, because ActivityBase.startActivity captured intentForHistoryList on the way
            // out. There is no startActivity here and no intent to mutate, so the route is
            // published CONTINUOUSLY from the form's live state instead: whenever anything records
            // a history item while this destination is showing, the stored route already names the
            // query, search type and section on screen.
            val historyOwner = remember { Any() }
            LaunchedEffect(query, searchType, bibleSection, setup.currentBookName) {
                deps.setHistoryRoute(
                    historyOwner,
                    NavRoutes.searchForm(
                        searchText = query.takeIf { it.isNotEmpty() },
                        searchType = searchType.name,
                        searchSection = bibleSection.name,
                        bibleBook = setup.currentBookName,
                    ),
                )
            }
            // Never an unconditional clear — see SearchNavDeps.clearHistoryRoute: this runs AFTER
            // the entering destination has published its own route, and Search -> SearchResults is
            // the exact case that breaks.
            DisposableEffect(Unit) {
                onDispose { deps.clearHistoryRoute(historyOwner) }
            }

            SearchScreen(
                title = setup.title,
                query = query,
                searchType = searchType,
                bibleSection = bibleSection,
                availableTranslations = availableTranslations,
                selectedTranslationIds = selectedTranslationIds,
                currentBookName = setup.currentBookName,
                onQueryChange = controller::setQuery,
                onSearchType = controller::setSearchType,
                onBibleSection = controller::setBibleSection,
                onTranslations = controller::setTranslations,
                onSubmit = {
                    val request = controller.buildRequest()
                    // Classic's `if (request.query.isBlank()) return`.
                    if (request.query.isNotBlank()) {
                        controller.recordRecentTerm(controller.query.value)
                        // Classic finished itself on both arms (`startActivity(...); finish()`), so
                        // Back from the results does NOT land on the form again — the HistoryManager
                        // entry published above is what brings it back, filled in. popUpTo
                        // reproduces that.
                        val popSelf: NavOptionsBuilder.() -> Unit = {
                            popUpTo(NavRoutes.SEARCH_FORM_PATTERN) { inclusive = true }
                        }
                        when (val next = d.submit(request)) {
                            // Classic passed ONLY SEARCH_DOCUMENT to the index prompt — no search
                            // text — which is why the chain comes back to the empty form rather
                            // than to results once the index is built. Preserved exactly.
                            is SearchSubmission.NeedsIndex -> navController.navigate(
                                NavRoutes.searchIndex(searchDocument = next.documentId),
                                popSelf,
                            )
                            is SearchSubmission.Results -> navController.navigate(
                                NavRoutes.searchResults(
                                    searchText = next.decoratedQuery,
                                    highlightText = next.highlightText,
                                    searchDocument = next.searchDocument,
                                    selectedTranslations = next.translationIds,
                                ),
                                popSelf,
                            )
                        }
                    }
                },
                onNavigateUp = { navController.popOrExit(deps.exitHost) },
                recentTerms = recentTerms,
                onRecentTermSelected = controller::setQuery,
            )
        }
    }

    // ——— SEARCH INDEX PROMPT ———
    composable(
        route = NavRoutes.SEARCH_INDEX_PATTERN,
        arguments = chainArguments(),
    ) { backStackEntry ->
        val d = deps.searchIndex
        val args = backStackEntry.searchArgs()
        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val target = remember(args.searchDocument) { d.resolve(args.searchDocument) }
        if (target == null) {
            // Classic's "nothing to index -> finish() immediately".
            LaunchedEffect(Unit) { navController.popOrExit(deps.exitHost) }
        } else {
            SearchIndexScreen(
                title = d.title,
                documentName = target.documentName,
                isRebuild = target.isRebuild,
                onCancel = { navController.popOrExit(deps.exitHost) },
                onCreate = {
                    d.createIndex(target.documentId)
                    // Classic `SearchIndexComposeActivity.kt:72-75`: forward every current extra,
                    // then ALWAYS override the document with the one actually being indexed —
                    // which matters when this prompt was entered with no searchDocument at all and
                    // resolve() fell back to the current page's book. In route-world the forward is
                    // an explicit rebuild of the five named arguments.
                    navController.navigate(args.copy(searchDocument = target.documentId).indexProgressRoute()) {
                        // Classic's `finish()` after startActivity: Back from the progress screen
                        // must not return to a prompt whose job has already been kicked off.
                        popUpTo(NavRoutes.SEARCH_INDEX_PATTERN) { inclusive = true }
                    }
                },
                onNavigateUp = { navController.popOrExit(deps.exitHost) },
            )
        }
    }

    // ——— SEARCH INDEX PROGRESS ———
    composable(
        route = NavRoutes.SEARCH_INDEX_PROGRESS_PATTERN,
        arguments = chainArguments(),
    ) { backStackEntry ->
        val d = deps.searchIndexProgress
        val args = backStackEntry.searchArgs()
        LaunchedEffect(d.title) { deps.setWindowTitle(d.title) }

        val controller = remember {
            // Classic's onHide = finish(): "Continue in background".
            SearchIndexProgressController(onHide = { navController.popOrExit(deps.exitHost) })
        }

        // A newly-finished job is reported by the work listener, which is not a coroutine; this
        // counter is the bridge into one. A NEW tick restarts the LaunchedEffect below, cancelling
        // any wait still in flight — deliberate: the most recent completion is the informative one,
        // and it also makes a double navigation impossible.
        var finishedJobTicks by remember { mutableStateOf(0) }
        var resumeTicks by remember { mutableStateOf(0) }

        // Classic's onResume/onPause PAIR (`:80-96`), which is mandatory: the listener holds these
        // lambdas, which hold the controller and this destination's navigation, so leaking it leaks
        // the destination. LifecycleResumeEffect's onPauseOrDispose is classic's onPause plus the
        // disposal case (a nav destination can be disposed without a pause an Activity would see).
        LifecycleResumeEffect(Unit) {
            resumeTicks += 1
            d.requestNotificationPermission()
            val stopObserving = d.observeJobs(
                { jobs -> controller.setJobs(jobs) },
                { finishedJobTicks += 1 },
            )
            onPauseOrDispose { stopObserving() }
        }

        // Classic parity: the "no tasks running" line appears only after ~4s if still idle
        // (`uiHandler.postDelayed(..., 4000)` in onResume — hence keyed on the resume, not on Unit).
        LaunchedEffect(resumeTicks) {
            delay(4000)
            controller.revealNoTasksIfIdle()
        }

        LaunchedEffect(finishedJobTicks) {
            if (finishedJobTicks > 0) {
                // The blocking half of classic's jobFinished (six rounds of pause(2)) runs
                // host-side on a background dispatcher; only the ROUTING decision is here.
                val outcome = d.awaitIndexed(args.searchDocument)
                when (outcome) {
                    IndexOutcome.INDEXED, IndexOutcome.INDEXED_EPUB -> {
                        val isEpub = outcome == IndexOutcome.INDEXED_EPUB
                        navController.navigate(nextRouteAfterIndexing(args, isEpub)) {
                            // Classic's finish() after startActivity.
                            popUpTo(NavRoutes.SEARCH_INDEX_PROGRESS_PATTERN) { inclusive = true }
                        }
                    }
                    // Classic: Log.e + showError(), but only once every job has stopped.
                    IndexOutcome.FAILED -> controller.showError()
                    // Classic did nothing here; another jobFinished will arrive.
                    IndexOutcome.STILL_RUNNING -> {}
                }
            }
        }

        val jobs by controller.jobs.collectAsState()
        val noTasks by controller.noTasks.collectAsState()
        val error by controller.error.collectAsState()

        SearchIndexProgressScreen(
            title = d.title,
            jobs = jobs,
            noTasks = noTasks,
            error = error,
            onHide = controller::hide,
            onDismissError = controller::dismissError,
        )
    }
}

/**
 * Classic `jobFinished`'s four-way destination choice (`:141-152`), which is the whole reason the
 * chain's arguments had to be named: an empty `searchText` means the user reached the index prompt
 * from the search FORM (which sends only a document), so indexing returns them to the form; a
 * non-empty one means a link or a re-run already knows what to search for, so it goes straight to
 * the results carrying every argument forward.
 */
private fun nextRouteAfterIndexing(args: SearchArgs, isEpub: Boolean): String =
    if (args.searchText.isNullOrEmpty()) {
        if (isEpub) NavRoutes.EPUB_SEARCH else NavRoutes.searchForm()
    } else {
        if (isEpub) {
            // EPUB_SEARCH_RESULTS_PATTERN has no highlight/translations/strongs arguments at all —
            // classic forwarded them in the bundle but EpubSearchResults never read them.
            NavRoutes.epubSearchResults(
                searchText = args.searchText,
                searchDocument = args.searchDocument,
            )
        } else {
            args.resultsRoute()
        }
    }
