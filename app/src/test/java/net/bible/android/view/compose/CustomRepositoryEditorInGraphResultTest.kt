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
import net.bible.sharedcore.download.CustomRepositoryController
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.CustomRepositoryEditorController
import net.bible.sharedcore.download.CustomRepositoryService
import net.bible.sharedcore.download.ManifestResult
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedui.download.nav.CloudDocumentsDeps
import net.bible.sharedui.download.nav.CustomRepositoriesDeps
import net.bible.sharedui.download.nav.CustomRepositoryEditorDeps
import net.bible.sharedui.download.nav.DownloadDeps
import net.bible.sharedui.download.nav.DownloadNavDeps
import net.bible.sharedui.download.nav.ProgressStatusDeps
import net.bible.sharedui.download.nav.downloadNavGraph
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The one COMPOSED test of the Documents/downloads cluster's `CustomRepositories` <->
 * `CustomRepositoryEditor` round trip, driven through the real graph and a real [NavResultChannel]
 * -- the slice 4, Task 3 twin of [BookmarksInGraphResultTest].
 *
 * It exists for the same reason that one does: `NavHostRoutingGuardTest` and
 * `NavResultChannelGuardTest` prove the graph file CONTAINS the right calls by scanning its source
 * text, but neither can tell you that `deliver`'s in-graph branch actually runs once a real back
 * stack is involved, that the list's consuming `LaunchedEffect` applies the result through the real
 * [CustomRepositoryController], that it does so EXACTLY once, or that the destination pops back to
 * the list afterwards. This is also the batch's sharpest edge, design §9 risk 1: `deliver` chooses
 * its branch from `navController.previousBackStackEntry`, and nothing else in the suite drives a
 * real `NavHostController` for this pair.
 *
 * The channel is driven DIRECTLY (`repositoryEditorChannel.deliver(navController, result)`) rather
 * than through a clicked Save/Delete/Cancel button: [CustomRepositoryEditorDeps.controllerFor]
 * deliberately carries no `onResult` callback (see its own kdoc), so -- unlike the label editor,
 * whose `LabelEditController` reports through an `onFinish` this suite can capture -- there is no
 * host-side seam to intercept short of clicking through the screen's own debounced URL validation.
 * The arm's `onSave`/`onDelete`/`onUp` are each a single, direct
 * `deps.repositoryEditorResults.deliver(navController, controller.buildXResult())` call (verified by
 * [net.bible.android.view.nav.NavResultChannelGuardTest.repositoryEditorArmActuallyDeliversThroughTheChannel]),
 * so driving the same channel object with the same `navController` exercises exactly the same
 * `NavResultChannel.deliver` branch those calls would.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CustomRepositoryEditorInGraphResultTest {
    @get:Rule val compose = createComposeRule()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** A trivial in-memory stand-in for the Room-backed service, real enough that
     *  [CustomRepositoryController.applyResult] and [CustomRepositoryEditorController] exercise
     *  their real insert/update/delete logic against it. */
    private class FakeCustomRepositoryService(seed: List<CustomRepositoryData> = emptyList()) : CustomRepositoryService {
        val rows = mutableListOf<CustomRepositoryData>().apply { addAll(seed) }
        val upsertCalls = mutableListOf<CustomRepositoryData>()
        val deleteCalls = mutableListOf<CustomRepositoryData>()
        override suspend fun list(): List<CustomRepositoryData> = rows.toList()
        override suspend fun upsert(repo: CustomRepositoryData): Boolean {
            upsertCalls.add(repo)
            val nextId = if (repo.id != 0L) repo.id else (rows.maxOfOrNull { it.id } ?: 0L) + 1
            rows.removeAll { it.id == repo.id || it.id == nextId }
            rows.add(repo.copy(id = nextId))
            return true
        }
        override suspend fun delete(repo: CustomRepositoryData) {
            deleteCalls.add(repo)
            rows.removeAll { it.id == repo.id }
        }
        override suspend fun validateManifest(url: String, existingId: Long): ManifestResult = ManifestResult.Invalid
    }

    private lateinit var service: FakeCustomRepositoryService

    /** The list's own controller, captured from [CustomRepositoriesDeps.controllerFor] so a test
     *  can read the rows the arm's consuming `LaunchedEffect` produced. */
    private var customRepositoriesController: CustomRepositoryController? = null

    /** The editor's own controller, captured from [CustomRepositoryEditorDeps.controllerFor]. */
    private var editorController: CustomRepositoryEditorController? = null

    /** The channel the editor delivers through -- held directly so a test can drive it exactly the
     *  way the arm's onSave/onDelete/onUp do (see this class's own kdoc for why). */
    private lateinit var repositoryEditorChannel: NavResultChannel<RepositoryResult>

    private var exitHostCalls = 0

    /** How often the channel took its EXIT branch (`exitWithResult`) instead of publish-and-pop. */
    private var channelExitWithResultCalls = 0

    private lateinit var navController: NavHostController

    private fun deps(seed: List<CustomRepositoryData> = emptyList()): DownloadNavDeps {
        service = FakeCustomRepositoryService(seed)
        return DownloadNavDeps(
            exitHost = { exitHostCalls++ },
            setWindowTitle = {},
            repositoryEditorResults = NavResultChannel<RepositoryResult> { channelExitWithResultCalls++ }
                .also { repositoryEditorChannel = it },
            customRepositories = CustomRepositoriesDeps(
                controllerFor = { onDuplicate ->
                    CustomRepositoryController(service, scope)
                        .apply { this.onDuplicate = onDuplicate }
                        .also { customRepositoriesController = it }
                },
                title = "Custom repositories",
                onDuplicate = {},
            ),
            customRepositoryEditor = CustomRepositoryEditorDeps(
                controllerFor = { initial ->
                    CustomRepositoryEditorController(service, scope, initial).also { editorController = it }
                },
                title = "Custom repositories",
                initialFor = { id ->
                    if (id == null) {
                        RepositoryResult()
                    } else {
                        service.list().find { it.id == id }?.let { RepositoryResult(repository = it) }
                            ?: RepositoryResult()
                    }
                },
                readClipboard = { null },
            ),
            // This test drives only the CustomRepositories/CustomRepositoryEditor pair; ProgressStatus
            // and Download are exercised by their own tests (nav-graph slice 4, Tasks 4 and 7a), so
            // these are unexercised stubs.
            progressStatus = ProgressStatusDeps(
                title = "Progress",
                requestNotificationPermission = {},
                observeJobs = { { } },
            ),
            download = DownloadDeps(
                controllerFor = {
                    DocumentSelectionController(
                        langComparator = { _, _ -> 0 },
                        onSelect = {}, onDelete = {}, onDeleteIndex = {},
                        onAbout = {}, onUnlock = {}, onStickyLanguage = {},
                    )
                },
                sessionToken = { "session" },
                title = "Download",
                topBarActions = {},
                askIfWantToProceed = { true },
                requestNotificationPermission = {},
                refreshCatalogue = {},
                onAutoDownload = { _, _ -> },
                reloadCatalogueIfRequested = {},
                onCancelDownload = {},
                hasBible = MutableStateFlow(false),
                subscribeDownloadProgress = { { } },
                subscribeMonitoring = { { } },
                persistTypeFilter = {},
                initialTypeFilter = { DocTypeFilter.ALL },
            ),
            // Task 8's destination -- also unexercised by this test.
            cloudDocuments = CloudDocumentsDeps(
                controllerFor = { error("not exercised here") },
                title = "Cloud documents",
                topBarActions = {},
                openOrGate = { error("not exercised here") },
                seedItems = { error("not exercised here") },
                refreshFromNetwork = { error("not exercised here") },
                subscribeProgress = { error("not exercised here") },
                statusFilterLabels = { error("not exercised here") },
                categoryFilterLabels = { error("not exercised here") },
                confirmRemove = { _, _, _ -> error("not exercised here") },
                confirmPurge = { _, _, _ -> error("not exercised here") },
                countLabel = { _, _ -> error("not exercised here") },
            ),
        )
    }

    private fun setGraph(d: DownloadNavDeps, startDestination: String) {
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        downloadNavGraph(navController, d)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    /**
     * The whole point of the task: a result produced by `CustomRepositoryEditor` while
     * `CustomRepositories` is its PARENT must reach the parent and pop, not exit the host --
     * mirrors [BookmarksInGraphResultTest.aManageLabelsResultProducedInsideTheGraphReachesBookmarksAndPops].
     */
    @Test
    fun aSavedResultReachesCustomRepositoriesAndPops() {
        setGraph(deps(), startDestination = NavRoutes.customRepositories())
        assertEquals(NavRoutes.CUSTOM_REPOSITORIES_PATTERN, currentRoute)

        compose.runOnIdle { navController.navigate(NavRoutes.customRepositoryEditor(null)) }
        compose.waitForIdle()
        assertEquals(NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN, currentRoute)

        val saved = RepositoryResult(repository = CustomRepositoryData(name = "Grace", manifestUrl = "https://x"))
        compose.runOnIdle { repositoryEditorChannel.deliver(navController, saved) }
        compose.waitForIdle()

        // The BRANCH first: a `deliver` that wrongly took the exit branch would fail every
        // assertion below too, and "nothing was upserted" names the symptom rather than the cause.
        assertEquals(
            0,
            channelExitWithResultCalls,
            "deliver() took its EXIT branch: with CustomRepositories on the stack below it, it must " +
                "publish to pending and pop instead",
        )
        assertEquals(0, exitHostCalls, "no destination should have called deps.exitHost")
        assertEquals(NavRoutes.CUSTOM_REPOSITORIES_PATTERN, currentRoute)
        assertEquals(listOf("Grace"), service.upsertCalls.map { it.name })
        assertTrue(service.deleteCalls.isEmpty())

        // The full loop, not just the service call: the list's own controller must have refreshed
        // its observable state too, or the user would see a stale list after the round trip.
        val listController = assertNotNull(customRepositoriesController, "the list arm never built a controller")
        assertEquals(listOf("Grace"), listController.state.value.rows.map { it.name })
    }

    /**
     * `consume()` clears the channel in the same breath as reading it, so the consuming
     * `LaunchedEffect` cannot apply the same result a second time when the parent recomposes --
     * mirrors [BookmarksInGraphResultTest.aDeliveredResultIsAppliedExactlyOnceAcrossRecompositions].
     */
    @Test
    fun aDeliveredResultIsAppliedExactlyOnceAcrossRecompositions() {
        setGraph(deps(), startDestination = NavRoutes.customRepositories())

        compose.runOnIdle { navController.navigate(NavRoutes.customRepositoryEditor(null)) }
        compose.waitForIdle()
        val saved = RepositoryResult(repository = CustomRepositoryData(name = "Grace"))
        compose.runOnIdle { repositoryEditorChannel.deliver(navController, saved) }
        compose.waitForIdle()

        // Force further recompositions of the parent arm the same way a user would: go into the
        // child again and come back without delivering anything new from the second visit.
        compose.runOnIdle { navController.navigate(NavRoutes.customRepositoryEditor(null)) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(1, service.upsertCalls.size, "the saved result was applied more than once")
    }

    /**
     * Plan D9, the arm-level half: entering directly at `NavRoutes.customRepositoryEditor(null)` --
     * i.e. with NO parent entry at all -- must build a BLANK editor, never pop. There is
     * deliberately no pop-on-null backstop (see `DownloadNavGraph.kt`'s own comment on the arm):
     * null means "new repository", a valid state, not a missing argument.
     */
    @Test
    fun enteringTheEditorWithNoIdBuildsABlankEditorRatherThanPopping() {
        setGraph(deps(), startDestination = NavRoutes.customRepositoryEditor(null))

        assertEquals(
            NavRoutes.CUSTOM_REPOSITORY_EDITOR_PATTERN,
            currentRoute,
            "an id-less entry must render the blank editor, not pop or exit",
        )
        assertEquals(0, channelExitWithResultCalls)
        assertEquals(0, exitHostCalls)

        val controller = assertNotNull(editorController, "the editor arm never built a controller")
        assertFalse(controller.state.value.isExisting, "a null id must resolve to a NEW, not existing, repository")
    }
}
