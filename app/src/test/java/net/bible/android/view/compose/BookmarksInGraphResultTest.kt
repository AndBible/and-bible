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
package net.bible.android.view.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedcore.bookmark.BookmarkRow
import net.bible.sharedcore.bookmark.BookmarkSortMode
import net.bible.sharedcore.bookmark.BookmarksController
import net.bible.sharedcore.bookmark.BookmarksService
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.LabelEditResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.nav.BookmarkNavDeps
import net.bible.sharedui.bookmark.nav.BookmarksDeps
import net.bible.sharedui.bookmark.nav.LabelEditDeps
import net.bible.sharedui.bookmark.nav.ManageLabelsDeps
import net.bible.sharedui.bookmark.nav.bookmarkNavGraph
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The one COMPOSED test of the bookmark cluster's navigation: the `Bookmarks` -> `ManageLabels`
 * round trip, driven through the real graph and the real [NavResultChannel].
 *
 * It exists because that round trip is what slice 2, Task 6 made reachable, and nothing else in the
 * suite can see it. `NavResultChannelGuardTest` proves the arm CONTAINS the right calls by walking
 * the source text; `NavResultChannelTest` proves `deliver`'s branch in isolation. Neither can tell
 * you that the branch actually takes the in-graph path once a real back stack is involved, that the
 * consuming `LaunchedEffect` runs, that it applies the result EXACTLY once, or that the destination
 * pops back to the list afterwards — and those four are the whole of what this task added.
 *
 * The two host halves are stood in for, not faked away: the `ManageLabels` controller here reports
 * through the same `onResult` the real host passes, and the payload travels through
 * [NavRoutes.manageLabels]' own percent-encoding, so the JSON round trip is real too. What is NOT
 * exercised is the host's own Room work behind `onManageLabelsResult`, which is why the assertion is
 * on the payload that reaches that slot rather than on anything it would do with it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarksInGraphResultTest {
    @get:Rule val compose = createComposeRule()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private class FakeBookmarksService : BookmarksService {
        override fun filterLabels(): List<BookmarkFilterLabel> =
            listOf(BookmarkFilterLabel(0, "All"), BookmarkFilterLabel(1, "Unlabeled"))
        override suspend fun loadRows(
            filterIndex: Int,
            sort: BookmarkSortMode,
            search: String?,
            showNotes: Boolean,
        ): List<BookmarkRow> = emptyList()
        override fun loadSortMode(): BookmarkSortMode = BookmarkSortMode.BIBLE_ORDER
        override fun saveSortMode(mode: BookmarkSortMode) {}
        override fun loadShowNotes(): Boolean = true
        override fun saveShowNotes(v: Boolean) {}
    }

    private class FakeManageLabelsService : ManageLabelsService {
        override fun assignableLabels(): List<LabelItem> =
            listOf(LabelItem("L1", "Grace", 0, false, false, false, null))
        override fun unlabeledLabel(): LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null)
        override fun recentLabelIds(): List<String> = emptyList()
        override fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle> = emptyMap()
        override fun randomColorArgb(): Int = 0
        override suspend fun searchStudyPadsByContent(text: String): List<ManageLabelsRow.SearchResult> = emptyList()
        override fun styleTagsVisible(): Boolean = false
        override fun setStyleTagsVisible(visible: Boolean) {}
    }

    /** What the graph handed back to the host, in the order it handed it. */
    private val appliedResults = mutableListOf<String>()

    /** The arm's own `navController.navigate(NavRoutes.manageLabels(payload))`, captured when the
     *  bookmark list's controller is built — this is the edge classic reached with `awaitIntent`. */
    private var navigateToManageLabels: ((String) -> Unit)? = null

    /** The label manager's own delivery lambda — `deps.manageLabelsResults.deliver(navController, …)`. */
    private var deliverManageLabelsResult: ((ManageLabelsResult) -> Unit)? = null

    private var exitHostCalls = 0
    private lateinit var navController: NavHostController

    private fun deps(): BookmarkNavDeps = BookmarkNavDeps(
        exitHost = { exitHostCalls++ },
        setWindowTitle = {},
        bookmarkResults = NavResultChannel<BookmarkResult> {},
        manageLabelsResults = NavResultChannel<ManageLabelsResult> {},
        labelEditResults = NavResultChannel<LabelEditResult> {},
        bookmarks = BookmarksDeps(
            controllerFor = { initialFilterIndex, onSelectBookmark, navigate ->
                navigateToManageLabels = navigate
                BookmarksController(
                    service = FakeBookmarksService(),
                    scope = scope,
                    initialFilterIndex = initialFilterIndex,
                    onSelectBookmark = { id, position ->
                        onSelectBookmark(
                            BookmarkResult(verse = id, description = "d", labelNo = 0, listPosition = position),
                        )
                    },
                    onAssignLabels = {},
                    onDeleteSelected = {},
                    onExportCsv = {},
                    onImportCsv = {},
                    onManageLabels = {},
                )
            },
            title = "Bookmarks",
            onManageLabelsResult = { result -> appliedResults.add(result.data) },
            subscribeSyncEvents = { { } },
        ),
        manageLabels = ManageLabelsDeps(
            controllerFor = { _, _, onResult ->
                deliverManageLabelsResult = onResult
                ManageLabelsController(
                    mode = ManageLabelsMode.WORKSPACE,
                    service = FakeManageLabelsService(),
                    scope = scope,
                    initialSelected = emptySet(),
                    initialAutoAssign = emptySet(),
                    initialAutoAssignPrimary = null,
                    initialBookmarkPrimary = null,
                    highlightLabelId = null,
                    onEditLabel = {},
                    onSelectStudyPad = { _, _ -> },
                    onSave = {},
                    onReset = {},
                )
            },
            titleFor = { "Labels" },
            onLabelEditResult = {},
            initialSearchMode = { 0 },
            iconSlot = { _, _, _ -> },
            actions = { },
            searchActions = { },
        ),
        labelEdit = LabelEditDeps(
            controllerFor = { _, _ -> error("the label editor is not part of this test") },
            title = "Edit label",
            iconKeys = emptyList(),
            iconSlot = { _, _ -> },
            actions = { _, _, _, _ -> },
            deletePromptSlot = { _, _, _, _ -> },
            confirmDiscard = {},
        ),
    )

    private fun setGraph() {
        val d = deps()
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = NavRoutes.bookmarks()) {
                        bookmarkNavGraph(navController, d)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    /**
     * The whole point of the task: a result produced by `ManageLabels` while `Bookmarks` is its
     * PARENT must reach the parent and pop, not exit the host. Before this arm existed, the label
     * manager was always the host's start destination, so `deliver` had only ever taken its other
     * branch in production.
     */
    @Test
    fun aManageLabelsResultProducedInsideTheGraphReachesBookmarksAndPops() {
        setGraph()
        assertEquals(NavRoutes.BOOKMARKS_PATTERN, currentRoute)

        val payload = """{"mode":"WORKSPACE","note":"100% of it"}"""
        compose.runOnIdle { assertNotNull(navigateToManageLabels)(payload) }
        compose.waitForIdle()
        assertEquals(NavRoutes.MANAGE_LABELS_PATTERN, currentRoute)

        val returned = """{"mode":"WORKSPACE","selected":["L1"]}"""
        compose.runOnIdle { assertNotNull(deliverManageLabelsResult)(ManageLabelsResult(returned)) }
        compose.waitForIdle()

        assertEquals(listOf(returned), appliedResults)
        assertEquals(NavRoutes.BOOKMARKS_PATTERN, currentRoute)
        assertEquals(0, exitHostCalls, "the in-graph branch must not exit the host")
    }

    /**
     * `consume()` clears the channel in the same breath as reading it, so the consuming
     * `LaunchedEffect` cannot apply the same result a second time when the parent recomposes. This
     * is the failure the guard test cannot see: a `pending` read without a `consume()` leaves the
     * value in place and every recomposition re-applies it — which, for the assign round trip, means
     * re-writing the user's labels over and over.
     */
    @Test
    fun aDeliveredResultIsAppliedExactlyOnceAcrossRecompositions() {
        setGraph()

        val payload = """{"mode":"WORKSPACE"}"""
        compose.runOnIdle { assertNotNull(navigateToManageLabels)(payload) }
        compose.waitForIdle()
        compose.runOnIdle { assertNotNull(deliverManageLabelsResult)(ManageLabelsResult("once")) }
        compose.waitForIdle()

        // Force further recompositions of the parent arm the same way a user would: go into the
        // child again and come back without delivering anything new from the second visit.
        compose.runOnIdle { assertNotNull(navigateToManageLabels)(payload) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(listOf("once"), appliedResults)
    }

    /**
     * The payload survives [NavRoutes.manageLabels]' percent-encoding and the navigation library's
     * single decode. `100%` in the JSON is the case that matters: the arm reads its argument PLAINLY
     * for exactly this reason (a second `NavRoutes.decodeArg` pass would corrupt it), and that
     * reasoning has until now been a comment with no test behind it.
     */
    @Test
    fun theManageLabelsPayloadSurvivesTheRouteRoundTrip() {
        val seen = mutableListOf<String>()
        val base = deps()
        val d = BookmarkNavDeps(
            exitHost = base.exitHost,
            setWindowTitle = base.setWindowTitle,
            bookmarkResults = base.bookmarkResults,
            manageLabelsResults = base.manageLabelsResults,
            labelEditResults = base.labelEditResults,
            bookmarks = base.bookmarks,
            manageLabels = ManageLabelsDeps(
                controllerFor = { data, onEditLabel, onResult ->
                    seen.add(data)
                    base.manageLabels.controllerFor(data, onEditLabel, onResult)
                },
                titleFor = base.manageLabels.titleFor,
                onLabelEditResult = base.manageLabels.onLabelEditResult,
                initialSearchMode = base.manageLabels.initialSearchMode,
                iconSlot = base.manageLabels.iconSlot,
                actions = base.manageLabels.actions,
                searchActions = base.manageLabels.searchActions,
            ),
            labelEdit = base.labelEdit,
        )
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = NavRoutes.bookmarks()) {
                        bookmarkNavGraph(navController, d)
                    }
                }
            }
        }
        compose.waitForIdle()

        val payload = """{"labels":["100% grace","a&b"]}"""
        compose.runOnIdle { assertNotNull(navigateToManageLabels)(payload) }
        compose.waitForIdle()

        assertTrue(seen.isNotEmpty(), "the ManageLabels arm never built a controller")
        assertEquals(payload, seen.single())
    }
}
