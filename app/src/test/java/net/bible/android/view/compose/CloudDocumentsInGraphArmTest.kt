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

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.cloud.CloudDocFilter
import net.bible.sharedcore.cloud.CloudDocItem
import net.bible.sharedcore.cloud.CloudDocumentsController
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedui.ProvideAppLocals
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
 * The COMPOSED test of nav-graph slice 4 Task 8's `CloudDocuments` arm, [DownloadInGraphArmTest]'s
 * shape for the batch's last (and only gated) destination.
 *
 * `NavHostRoutingGuardTest` proves the destination is REGISTERED and `SearchHostBackRoutingGuardTest`
 * that its back routing exists as source text; neither can tell you that a refused `openOrGate`
 * actually leaves the host with nothing behind it, that the back handler's precedence is right, that
 * the progress-bridge subscription is released when the destination goes away, or that a genuine
 * leave-and-reopen -- reachable here, unlike `Download`, because this destination has TWO distinct
 * entry points (`Download`'s overflow row, Settings' sync row) and no covering child of its own --
 * gets a FRESH controller rather than silently reusing the previous entry's filters/selection/search
 * (task-8 fix round 1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class CloudDocumentsInGraphArmTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    /** Everything the arm asked the host to do, in order. */
    private val calls = mutableListOf<String>()

    private var exitHostCalls = 0
    private var windowTitle: String? = null
    private var gateAnswer = true

    private var controllerBuildCount = 0
    private var controller: CloudDocumentsController? = null

    private var progressOnRunning: ((Boolean) -> Unit)? = null
    private var progressStops = 0

    private fun cloudDocumentsDeps() = CloudDocumentsDeps(
        controllerFor = {
            controllerBuildCount++
            // A per-entry factory -- see the class kdoc: each call must return a genuinely FRESH
            // controller, exactly the property [aLeaveAndReopenGetsAFreshControllerNotThePreviousEntrysState]
            // exercises.
            CloudDocumentsController(
                syncEnabled = { true },
                onAction = { _, _ -> },
                onBulkAction = { _, _ -> },
                onSyncNow = { _, _, _ -> },
                onRescan = {},
                onShowRemovedChange = {},
            ).also { controller = it }
        },
        title = "Cloud documents",
        topBarActions = {},
        openOrGate = { calls.add("gate"); gateAnswer },
        // Never called by the arm directly (see the task report): reached only from the HOST's own
        // openOrGate/show-removed-toggle implementations, which this test replaces wholesale with the
        // trivial controllerFor above. A stray arm-side call would fail loudly here instead of
        // silently passing.
        seedItems = { error("seedItems is not exercised by the arm directly") },
        refreshFromNetwork = { calls.add("refresh") },
        subscribeProgress = { onRunning ->
            calls.add("progress+")
            progressOnRunning = onRunning
            val stop: () -> Unit = { progressStops++ }
            stop
        },
        statusFilterLabels = { showRemoved -> List(if (showRemoved) 8 else 7) { "status$it" } },
        categoryFilterLabels = { List(7) { "category$it" } },
        countLabel = { _, _ -> error("not exercised here") },
    )

    private fun deps() = DownloadNavDeps(
        exitHost = { exitHostCalls++ },
        setWindowTitle = { windowTitle = it },
        repositoryEditorResults = NavResultChannel<RepositoryResult> { },
        customRepositories = CustomRepositoriesDeps(
            controllerFor = { error("not exercised here") },
            title = "Custom repositories",
            onDuplicate = {},
        ),
        customRepositoryEditor = CustomRepositoryEditorDeps(
            controllerFor = { error("not exercised here") },
            title = "Custom repositories",
            initialFor = { RepositoryResult() },
            readClipboard = { null },
        ),
        // Only used as the round-trip's other destination (a sibling to navigate to and back from);
        // its own behaviour is Task 4's test's subject, not this one's.
        progressStatus = ProgressStatusDeps(
            title = "Progress",
            requestNotificationPermission = {},
            observeJobs = { { } },
        ),
        download = DownloadDeps(
            controllerFor = { error("not exercised here") },
            sessionToken = { "session" },
            title = "Download",
            topBarActions = {},
            askIfWantToProceed = { error("not exercised here") },
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
        cloudDocuments = cloudDocumentsDeps(),
    )

    private fun setGraph(startDestination: String) {
        val d = deps()
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

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * Plan D3, the way [DownloadInGraphArmTest.aRefusedGateLeavesTheHostAndRunsNothingBehindIt]
     * proves it for `Download`: a refused gate must leave (`popOrExit`, which exits the host here
     * since this is the start destination) and nothing past the gate may run. `subscribeProgress` is
     * the one thing that legitimately fires regardless -- classic's own `bridge.register()` runs in
     * `onCreate` BEFORE `openOrGate()` is even called -- so it is asserted present, not absent.
     */
    @Test
    fun aRefusedGateLeavesTheHostAndRunsNothingBehindIt() {
        gateAnswer = false
        setGraph(NavRoutes.cloudDocuments())

        assertEquals(listOf("gate", "progress+"), calls, "nothing past the refused gate may run")
        assertEquals(1, exitHostCalls)
    }

    /** The happy path's mirror: an accepted gate does not leave, and the destination composes. */
    @Test
    fun anAcceptedGateComposesAndLeavesNothingPending() {
        setGraph(NavRoutes.cloudDocuments())

        assertTrue("gate" in calls)
        assertEquals(0, exitHostCalls)
        assertNotNull(controller)
    }

    /**
     * Classic's `onBackPressed` override (`CloudDocumentsComposeActivity.kt:183-189`) as ONE gated
     * handler -- [DownloadInGraphArmTest.backDismissesTheSelectionBarBeforeTheSearchBar]'s shape.
     * The selection bar covers the search bar, so back must dismiss the selection first; two stacked
     * handlers would only pass this by declaration order.
     */
    @Test
    fun backDismissesTheSelectionBarBeforeTheSearchBar() {
        setGraph(NavRoutes.cloudDocuments())
        val c = assertNotNull(controller)

        compose.runOnIdle { c.openSearch(); c.setQuery("es"); c.enterSelection() }
        compose.waitForIdle()

        pressBack()
        assertFalse(c.selectionMode.value, "back must dismiss the selection bar first")
        assertTrue(c.searchModeActive.value, "the search bar underneath must survive that press")
        assertEquals("es", c.query.value, "dismissing the selection must not clear the query")

        pressBack()
        assertFalse(c.searchModeActive.value, "the second press must close the search bar")
        assertEquals(0, exitHostCalls, "neither press may leave the host while there is something to dismiss")
    }

    /**
     * Classic's `bridge.running.drop(1).collect { ... }` body (`:111-116`), reproduced as
     * `deps.subscribeProgress`'s `onRunning` callback: flip `transferRunning`, and on the FALSE
     * (transfer-finished) edge only, trigger a network refresh. The TRUE edge must never refresh --
     * that would refetch on every progress tick, not just on completion.
     */
    @Test
    fun theProgressCallbackFlipsTransferRunningAndRefreshesOnlyOnTheFalseEdge() {
        setGraph(NavRoutes.cloudDocuments())
        val c = assertNotNull(controller)
        val onRunning = assertNotNull(progressOnRunning)
        assertFalse(c.transferRunning.value)

        compose.runOnIdle { onRunning(true) }
        compose.waitForIdle()
        assertTrue(c.transferRunning.value, "the true edge must flip transferRunning")
        assertFalse("refresh" in calls, "the true (still-running) edge must never refresh")

        compose.runOnIdle { onRunning(false) }
        compose.waitForIdle()
        assertFalse(c.transferRunning.value, "the false edge must flip transferRunning back")
        assertTrue("refresh" in calls, "the false (finished) edge must trigger a network refresh")
    }

    /**
     * The seam an Activity got for free (`bridge.register()`/`unregister()`,
     * `CloudDocumentsComposeActivity.kt:105`/`:180`). A destination's composition is disposed while a
     * sibling sits on top of it, so a subscription that is never released leaks a listener holding
     * this destination's controller -- [DownloadInGraphArmTest.bothLifecycleSeamsAreReleasedWhenTheDestinationGoesAway]'s
     * shape, for this destination's one seam.
     */
    @Test
    fun theProgressSubscriptionIsReleasedWhenTheDestinationGoesAway() {
        setGraph(NavRoutes.cloudDocuments())
        assertEquals(0, progressStops)

        compose.runOnIdle { navController.navigate(NavRoutes.progressStatus()) }
        compose.waitForIdle()

        assertEquals(1, progressStops, "the progress subscription was not released")
        assertFalse(exitHostCalls > 0, "navigating to a sibling must not leave the host")
    }

    /**
     * **Honest scope (task-8 fix round 2, finding B corrected an overclaiming name here).** This
     * test drives the arm with a FAKE `CloudDocumentsDeps` whose `controllerFor` (below) builds a
     * new controller UNCONDITIONALLY on every call -- so what this actually proves is that the
     * arm's own `remember { d.controllerFor() }` re-invokes the factory on a genuine new back-stack
     * entry rather than caching the controller at some wider scope. That property was never broken
     * and this test would pass unchanged even if the REAL host's `controllerFor` were reverted to a
     * `by lazy` singleton -- it exercises the arm's contract with whatever `controllerFor` the host
     * supplies, not the host's own implementation of it.
     *
     * The host-side behaviour fix round 1 restored (`NavHostComposeActivity.cloudDocumentsControllerRef`
     * + `buildCloudDocumentsController()`, a real per-entry factory rather than a `by lazy` field) is
     * covered separately by `CloudDocumentsControllerRebuildIsolationTest`, which reflects into the
     * real Activity because the arm-level fake here has no equivalent shared-mutable-ref bug to
     * reproduce -- the bug (and its fix) live entirely in the host's own field, never in the arm.
     */
    @Test
    fun theArmAsksControllerForAfreshOnEveryGenuineNewEntry() {
        setGraph(NavRoutes.cloudDocuments())
        val first = assertNotNull(controller)
        assertEquals(1, controllerBuildCount)

        // Order matters: setStatusFilter/setQuery each reset selection (refilter(resetSelection =
        // true), the controller's own "a filter/query change strands a selection" rule) -- so both
        // run BEFORE enterSelection/toggle, never after.
        compose.runOnIdle {
            first.setStatusFilter(CloudDocFilter.BLOCKED)
            first.setQuery("es")
            first.openSearch()
            first.setItems(listOf(sampleItem("esv")))
            first.enterSelection()
            first.toggle("esv")
        }
        compose.waitForIdle()
        assertTrue(first.searchModeActive.value)
        assertEquals("es", first.query.value)
        assertTrue(first.selectionMode.value)
        assertEquals(CloudDocFilter.BLOCKED, first.statusFilter.value)
        assertEquals(setOf("esv"), first.selectedIds.value)

        // Leave ENTIRELY -- pop CloudDocuments off the back stack, not merely cover it -- then
        // reopen it as a brand new entry, exactly the round trip Download's own overflow row (or
        // Settings' sync row) drives in the real app.
        compose.runOnIdle {
            navController.navigate(NavRoutes.progressStatus()) {
                popUpTo(NavRoutes.cloudDocuments()) { inclusive = true }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { navController.navigate(NavRoutes.cloudDocuments()) }
        compose.waitForIdle()

        val second = assertNotNull(controller)
        assertEquals(2, controllerBuildCount, "the reopen must build a genuinely new controller")
        assertTrue(second !== first, "the reopened entry must not reuse the previous controller instance")
        assertFalse(second.searchModeActive.value, "the previous entry's search mode survived the round trip")
        assertEquals("", second.query.value, "the previous entry's query survived the round trip")
        assertFalse(second.selectionMode.value, "the previous entry's selection survived the round trip")
        assertEquals(CloudDocFilter.ALL, second.statusFilter.value, "the previous entry's status filter survived the round trip")
        assertEquals(emptySet(), second.selectedIds.value, "the previous entry's selected ids survived the round trip")
    }

    private fun sampleItem(initials: String) = CloudDocItem(
        initials = initials, name = initials, category = null,
        cloudVersion = null, localVersion = null,
        cloudOnly = false, localOnly = false, updateAvailable = false, localNewer = false,
        blocked = false, canDeleteLocal = true, cloudDeleted = false,
        sizeLabel = null, sizeBytes = null,
    )
}
