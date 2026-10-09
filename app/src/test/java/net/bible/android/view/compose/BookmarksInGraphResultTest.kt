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
import net.bible.sharedcore.bookmark.LabelEditController
import net.bible.sharedcore.bookmark.LabelEditService
import net.bible.sharedcore.bookmark.LabelEditState
import net.bible.sharedcore.bookmark.LabelItem
import net.bible.sharedcore.bookmark.ManageLabelsController
import net.bible.sharedcore.bookmark.ManageLabelsMode
import net.bible.sharedcore.bookmark.ManageLabelsRow
import net.bible.sharedcore.bookmark.ManageLabelsService
import net.bible.sharedcore.bookmark.OverrideMode
import net.bible.sharedcore.bookmark.SearchMode
import net.bible.sharedcore.bookmark.LabelEditResult as CoreLabelEditResult
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
        override suspend fun filterLabels(): List<BookmarkFilterLabel> =
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
        override suspend fun assignableLabels(): List<LabelItem> =
            listOf(LabelItem("L1", "Grace", 0, false, false, false, null))
        override suspend fun unlabeledLabel(): LabelItem = LabelItem("UNL", "Unlabeled", 0, false, true, true, null)
        override fun recentLabelIds(): List<String> = emptyList()
        override suspend fun overriddenLabelStyles(): Map<String, BookmarkDisplayStyle> = emptyMap()
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

    /** The arm's `navController.navigate(NavRoutes.labelEdit(payload))`, captured the same way. */
    private var navigateToLabelEdit: ((String) -> Unit)? = null

    /**
     * The live `ManageLabelsController`s, MEMOISED ON THE PAYLOAD — which is not a convenience but a
     * faithfulness requirement. The real host memoises them the same way
     * (`NavHostComposeActivity.manageLabelsSession`, keyed on the route's data), precisely so the
     * label manager's working set survives the editor sitting on top of its destination. A test
     * factory that minted a fresh controller per composition would hand every "did the state
     * survive?" assertion a brand-new object to be satisfied by, and pass no matter what the arm did.
     */
    private val manageLabelsControllers = mutableMapOf<String, ManageLabelsController>()

    /** The controller for the most recent payload, so a test can read and drive its search mode. */
    private var manageLabelsController: ManageLabelsController? = null

    /** The channel the label manager delivers through, so a test can inspect what it published. */
    private lateinit var manageLabelsChannel: NavResultChannel<ManageLabelsResult>

    /** How often the arm asked the host for the persisted search-mode setting. */
    private var searchModeSeedReads = 0

    /** The editor's `controller.save()`, captured so a test can finish the editor the way a user does. */
    private var saveLabelEdit: (() -> Unit)? = null

    private var exitHostCalls = 0

    /**
     * How often a channel took its EXIT branch, i.e. `exitWithResult`. Counted separately from
     * [exitHostCalls] because the two are different failures with the same symptom: the arm calling
     * `deps.exitHost()` directly, and `NavResultChannel.deliver` deciding there is no parent entry to
     * publish to. An empty `exitWithResult` would have made the assertion below unfalsifiable.
     */
    private var channelExitWithResultCalls = 0

    private lateinit var navController: NavHostController

    /**
     * A label the editor arm can be built around. Its CONTENT is irrelevant to every test here —
     * what matters is that a second destination really composes on top of `ManageLabels`, which is
     * what disposes the parent arm's composition.
     */
    private fun labelEditState() = LabelEditState(
        labelId = "L1",
        name = "Grace",
        color = 0,
        customIcon = null,
        selectionStyle = BookmarkDisplayStyle.HIGHLIGHT,
        wholeVerseStyle = null,
        favourite = false,
        isAssigning = false,
        thisBookmarkSelected = false,
        thisBookmarkPrimary = false,
        hasWorkspaceContext = false,
        autoAssign = false,
        autoAssignPrimary = false,
        overrideMode = OverrideMode.NONE,
        isSpecialLabel = false,
        isSpeakLabel = false,
    )

    private fun deps(
        manageLabelsMode: ManageLabelsMode = ManageLabelsMode.WORKSPACE,
        initialSearchMode: () -> Int = { 0 },
    ): BookmarkNavDeps = BookmarkNavDeps(
        exitHost = { exitHostCalls++ },
        setWindowTitle = {},
        bookmarkResults = NavResultChannel<BookmarkResult> { channelExitWithResultCalls++ },
        manageLabelsResults = NavResultChannel<ManageLabelsResult> { channelExitWithResultCalls++ }
            .also { manageLabelsChannel = it },
        labelEditResults = NavResultChannel<LabelEditResult> { channelExitWithResultCalls++ },
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
            controllerFor = { data, onEditLabel, onResult ->
                deliverManageLabelsResult = onResult
                navigateToLabelEdit = onEditLabel
                manageLabelsControllers.getOrPut(data) {
                    ManageLabelsController(
                        mode = manageLabelsMode,
                        service = FakeManageLabelsService(),
                        scope = scope,
                        initialSelected = emptySet(),
                        initialAutoAssign = emptySet(),
                        initialAutoAssignPrimary = null,
                        initialBookmarkPrimary = null,
                        highlightLabelId = null,
                        // The host's own shape: the controller reports a nullable label id (null =
                        // "new"), the host turns it into a payload, the graph turns THAT into a route.
                        onEditLabel = { id -> onEditLabel(id ?: "new") },
                        onSelectStudyPad = { _, _ -> },
                        onSave = {},
                        onReset = {},
                    )
                }.also { manageLabelsController = it }
            },
            titleFor = { "Labels" },
            onLabelEditResult = {},
            initialSearchMode = { searchModeSeedReads++; initialSearchMode() },
            iconSlot = { _, _, _ -> },
            actions = { },
            searchActions = { },
        ),
        labelEdit = LabelEditDeps(
            // A REAL controller, unlike the `error(...)` stub this started as: the editor has to
            // actually compose for the round trip the search-mode test drives, since what disposes
            // the ManageLabels arm's composition is a second destination being on top of it.
            controllerFor = { _, onResult ->
                LabelEditController(
                    initial = labelEditState(),
                    service = object : LabelEditService {
                        override suspend fun orphanedBookmarkCount(labelId: String): Int = 0
                    },
                    scope = scope,
                    onFinish = { outcome ->
                        onResult(
                            when (outcome) {
                                is CoreLabelEditResult.Save -> LabelEditResult.Saved("saved")
                                is CoreLabelEditResult.Delete -> LabelEditResult.Saved("deleted")
                                CoreLabelEditResult.Cancel -> LabelEditResult.Cancelled
                            },
                        )
                    },
                ).also { saveLabelEdit = it::save }
            },
            title = "Edit label",
            iconKeys = emptyList(),
            iconSlot = { _, _ -> },
            actions = { _, _, _, _ -> },
            deletePromptSlot = { _, _, _, _ -> },
        ),
    )

    private fun setGraph(
        d: BookmarkNavDeps = deps(),
        startDestination: String = NavRoutes.bookmarks(),
    ) {
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = startDestination) {
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

        // The BRANCH first, then its consequences: a `deliver` that took the exit branch would fail
        // every assertion below too, and "appliedResults was empty" names the symptom rather than the
        // cause. Mutation-proved by forcing `hasParentEntry = false`.
        assertEquals(
            0,
            channelExitWithResultCalls,
            "deliver() took its EXIT branch: with Bookmarks on the stack below it, it must publish " +
                "to pending and pop instead",
        )
        assertEquals(0, exitHostCalls, "no destination should have called deps.exitHost")
        assertEquals(listOf(returned), appliedResults)
        assertEquals(NavRoutes.BOOKMARKS_PATTERN, currentRoute)
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

    /**
     * [NavResultChannel]'s OTHER branch, end to end — the one every EXTERNAL entry into `ManageLabels`
     * takes, and the one nothing in this suite could see until this test.
     *
     * Six rewritten production call sites reach the label manager as the host's START destination
     * (`MenuCommandHandler`, both `OptionsMenuItems` preferences, `BibleView.assignLabels`,
     * `CurrentGeneralBookPage`, `TextDisplaySettingsComposeActivity`). With no parent entry on the
     * stack, `deliver` must `exitWithResult` — the host's `setResult` + `finish`. The derivation that
     * decides this, `navController.previousBackStackEntry != null`, had NO test: `NavResultChannelTest`
     * exercises the boolean-consuming function with a boolean handed to it, and the three tests above
     * only ever drive the in-graph branch. Mutating that one property to `currentBackStackEntry`
     * (never null) would have turned all six live call sites into silent `RESULT_CANCELED` no-ops with
     * the whole suite still green.
     */
    @Test
    fun aManageLabelsResultProducedAsTheStartDestinationExitsTheHost() {
        val payload = """{"mode":"WORKSPACE"}"""
        setGraph(startDestination = NavRoutes.manageLabels(payload))
        assertEquals(NavRoutes.MANAGE_LABELS_PATTERN, currentRoute)

        compose.runOnIdle { assertNotNull(deliverManageLabelsResult)(ManageLabelsResult("""{"selected":[]}""")) }
        compose.waitForIdle()

        assertEquals(
            1,
            channelExitWithResultCalls,
            "deliver() did not take its EXIT branch. As the START destination there is no parent " +
                "entry, so the result must leave through exitWithResult (setResult + finish) — " +
                "publishing it in-graph instead is what every external caller would silently lose",
        )
        assertTrue(appliedResults.isEmpty(), "nothing may be applied in-graph: appliedResults=$appliedResults")
        assertEquals(0, exitHostCalls, "the exit must carry the RESULT, not be a bare deps.exitHost()")

        // THE assertion of this test, and the one the obvious pair above cannot make. `deliver`'s
        // publish-then-pop branch FALLS THROUGH to exitWithResult when the pop fails (plan D2), and
        // popping the start destination does fail -- so a `hasParentEntry` that wrongly says "true"
        // here still ends at exitWithResult, with the count at 1 and appliedResults empty, and both
        // assertions above pass. What it cannot undo is the publish it made on the way: `pending`
        // is left holding a result nobody will ever consume. That residue is the only visible
        // difference between the right branch and the wrong one taken twice.
        assertEquals(
            null,
            manageLabelsChannel.pending.value,
            "deliver() published to pending before falling through to the exit: it took the IN-GRAPH " +
                "branch and only reached exitWithResult because popping the start destination failed",
        )
    }

    /**
     * The StudyPad search mode the user chose must survive the label-editor round trip.
     *
     * The arm seeds the controller's search mode from the persisted setting in a `LaunchedEffect`,
     * and its composition is DISPOSED while the editor sits on top of it — so the effect runs again
     * on the way back. Seeding must therefore be gated on "have we seeded at all", not on "do we know
     * the seed value": the earlier `persistedSearchMode` remembered only the VALUE, which stopped the
     * setting being re-READ but not the original seed being re-APPLIED over a mode the user had
     * changed since. Four taps reproduced it (StudyPads → switch to CONTENT → open the editor → save)
     * and the mode flipped back to NAME_START, `dispatchSearchOrRebuild`ing the visible result set
     * with it.
     *
     * STUDYPAD mode because that is the only mode the seeding runs in at all.
     */
    @Test
    fun theStudyPadSearchModeSurvivesTheLabelEditorRoundTrip() {
        setGraph(
            d = deps(
                manageLabelsMode = ManageLabelsMode.STUDYPAD,
                initialSearchMode = { SearchMode.NAME_START.ordinal },
            ),
        )
        compose.runOnIdle { assertNotNull(navigateToManageLabels)("""{"mode":"STUDYPAD"}""") }
        compose.waitForIdle()

        val controller = assertNotNull(manageLabelsController)
        assertEquals(SearchMode.NAME_START, controller.searchMode.value, "the arm never seeded the mode")
        assertEquals(1, searchModeSeedReads)

        compose.runOnIdle { controller.setSearchMode(SearchMode.CONTENT) }
        compose.waitForIdle()

        compose.runOnIdle { assertNotNull(navigateToLabelEdit)("""{"id":"L1"}""") }
        compose.waitForIdle()
        assertEquals(NavRoutes.LABEL_EDIT_PATTERN, currentRoute)

        compose.runOnIdle { assertNotNull(saveLabelEdit)() }
        compose.waitForIdle()
        assertEquals(NavRoutes.MANAGE_LABELS_PATTERN, currentRoute)

        assertEquals(
            SearchMode.CONTENT,
            controller.searchMode.value,
            "returning from the editor re-applied the ORIGINAL seed over the mode the user chose",
        )
        assertEquals(1, searchModeSeedReads, "the setting was re-read on the way back")
    }
}
