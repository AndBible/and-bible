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

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentSelectionController
import net.bible.sharedcore.download.RepositoryResult
import net.bible.sharedui.ProvideAppLocals
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The COMPOSED test of nav-graph slice 4 Task 7a's `Download` arm, driven through the real graph the
 * way [MyDocumentsInGraphResultTest] drives the my-documents cluster's.
 *
 * `NavHostRoutingGuardTest` proves the destination is REGISTERED and `SearchHostBackRoutingGuardTest`
 * that its back routing exists as source text. Neither can tell you that the route's five arguments
 * arrive where classic's five Intent extras used to, that the "do you want to proceed" gate still
 * sequences everything behind it -- and still leaves on a no, now that it can no longer refuse to
 * compose -- or that the two lifecycle seams an Activity got for free are actually released when the
 * destination goes away. Those are the whole of what this task's port rests on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DownloadInGraphArmTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    /** Everything the arm asked the host to do, in order -- the gate's sequencing IS the contract. */
    private val calls = mutableListOf<String>()

    private var exitHostCalls = 0
    private var windowTitle: String? = null
    private var gateAnswer = true

    private var addonsSeenByTypeFilter: Boolean? = null
    private var firstDownloadSeenByMonitoring: Boolean? = null
    private var autoDownloadArgs: Pair<String?, Boolean>? = null
    private var progressStops = 0
    private var monitoringStops = 0

    /** Host-memoised the way `NavHostComposeActivity.downloadControllerFor` memoises it. */
    private var controller: DocumentSelectionController? = null

    private val hasBible = MutableStateFlow(false)

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
        // The neighbouring destination this test navigates to in order to dispose the Download
        // arm's composition; its own behaviour is Task 4's test's subject, not this one's.
        progressStatus = ProgressStatusDeps(
            title = "Progress",
            requestNotificationPermission = {},
            observeJobs = { { } },
        ),
        download = DownloadDeps(
            controllerFor = { initialTypeFilter ->
                controller ?: DocumentSelectionController(
                    langComparator = { _, _ -> 0 },
                    onSelect = {}, onDelete = {}, onDeleteIndex = {},
                    onAbout = {}, onUnlock = {}, onStickyLanguage = {},
                ).also { it.setTypeFilter(initialTypeFilter); controller = it }
            },
            title = "Download",
            topBarActions = {},
            askIfWantToProceed = { calls.add("gate"); gateAnswer },
            requestNotificationPermission = { calls.add("permission") },
            refreshCatalogue = { refresh -> calls.add("refresh=$refresh") },
            onAutoDownload = { documentIds, downloadRecommended ->
                calls.add("autoDownload")
                autoDownloadArgs = documentIds to downloadRecommended
            },
            onCancelDownload = {},
            hasBible = hasBible,
            subscribeDownloadProgress = {
                calls.add("progress+")
                val stop: () -> Unit = { progressStops++ }
                stop
            },
            subscribeMonitoring = { firstDownload ->
                firstDownloadSeenByMonitoring = firstDownload
                calls.add("monitoring+")
                val stop: () -> Unit = { monitoringStops++ }
                stop
            },
            persistTypeFilter = {},
            initialTypeFilter = { addons -> addonsSeenByTypeFilter = addons; DocTypeFilter.ALL },
        ),
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

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(resId)

    /**
     * All five arguments at once, because all five used to be Intent extras on the SAME launch and
     * a port that dropped one would still look right with the other four.
     */
    @Test
    fun theRoutesFiveArgumentsAllReachTheArm() {
        setGraph(
            NavRoutes.download(
                firstDownload = true,
                downloadRecommended = true,
                search = "ESV",
                addons = true,
                documentIds = """[{"initials":"ESV"}]""",
            ),
        )

        // addons -> the initial type filter; firstDownload -> the monitoring seam AND the OK gate;
        // search -> the controller's query and open search bar; the last two -> onAutoDownload.
        assertEquals(true, addonsSeenByTypeFilter)
        assertEquals(true, firstDownloadSeenByMonitoring)
        assertEquals("ESV", controller?.query?.value)
        assertEquals(true, controller?.searchModeActive?.value)
        assertEquals("""[{"initials":"ESV"}]""" to true, autoDownloadArgs)

        // The firstDownload OK gate is composed, and disabled until a Bible is installed.
        compose.onNodeWithText(string(R.string.okay)).assertIsNotEnabled()
        assertEquals("Download", windowTitle)
    }

    /**
     * The argument-free route is the one every non-onboarding caller uses, and "absent" has to mean
     * false, not "missing" -- flags are emitted only when true (plan D2).
     */
    @Test
    fun anArgumentFreeRouteMeansEveryFlagIsOffAndNothingIsAutoDownloaded() {
        setGraph(NavRoutes.download())

        assertEquals(false, addonsSeenByTypeFilter)
        assertEquals(false, firstDownloadSeenByMonitoring)
        assertEquals("", controller?.query?.value)
        assertEquals(false, controller?.searchModeActive?.value)
        assertEquals(null to false, autoDownloadArgs)
        // No OK gate outside the onboarding variant -- the contrast that makes the assertion above
        // mean something.
        compose.onNodeWithText(string(R.string.okay)).assertDoesNotExist()
    }

    /**
     * The gate ran FIRST and everything else behind it, in classic's `onCreate` order
     * (`DownloadComposeActivity.kt:233-246`). The two lifecycle seams are independent of it: classic
     * subscribed them in `onCreate`/`onStart` regardless of the answer.
     */
    @Test
    fun everythingTheGateGuardsRunsBehindItAndInOrder() {
        setGraph(NavRoutes.download())

        assertEquals(
            listOf("gate", "permission", "refresh=false", "autoDownload"),
            calls.filterNot { it.endsWith("+") },
        )
        assertTrue("progress+" in calls && "monitoring+" in calls, "both lifecycle seams must subscribe: $calls")
    }

    /**
     * Plan D3: an arm cannot refuse to compose, so a refused gate has to LEAVE instead. Nothing the
     * gate guards may have run -- a user who says "no downloads" must not have a download started
     * nor a notification permission asked for on the way out.
     */
    @Test
    fun aRefusedGateLeavesTheHostAndRunsNothingBehindIt() {
        gateAnswer = false
        setGraph(NavRoutes.download())

        assertEquals(listOf("gate"), calls.filterNot { it.endsWith("+") })
        assertNull(autoDownloadArgs)
        // Download is the start destination here, so the pop fails and popOrExit leaves the host --
        // exactly what classic's finish() did.
        assertEquals(1, exitHostCalls)
    }

    /**
     * Classic's `onBackPressed` override (`DownloadComposeActivity.kt:396-403`) as ONE gated
     * handler. The ORDER is the whole point: the selection bar covers the search bar, so back must
     * dismiss the selection first -- closing the search bar underneath a visible selection bar would
     * clear the query and re-filter the list invisibly. Two stacked handlers would pass this only by
     * declaration order, which is why the arm has one.
     */
    @Test
    fun backDismissesTheSelectionBarBeforeTheSearchBar() {
        setGraph(NavRoutes.download())
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
     * The seams an Activity got for free. A destination's composition is disposed while a child sits
     * on top of it, so a subscription that is never released leaks a listener holding this
     * destination's controller -- and the classic `onStop`/`onDestroy` that used to release it is
     * gone.
     */
    @Test
    fun bothLifecycleSeamsAreReleasedWhenTheDestinationGoesAway() {
        setGraph(NavRoutes.download())
        assertEquals(0, progressStops)
        assertEquals(0, monitoringStops)

        compose.runOnIdle { navController.navigate(NavRoutes.progressStatus()) }
        compose.waitForIdle()

        assertEquals(1, progressStops, "the progress collector was not released")
        assertEquals(1, monitoringStops, "the monitoring pair was not released")
        assertFalse(exitHostCalls > 0, "navigating to a child must not leave the host")
    }
}
