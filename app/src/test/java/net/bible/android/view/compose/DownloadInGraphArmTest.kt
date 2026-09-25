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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
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

    /** How often the arm asked the host whether a catalogue reload was armed -- once per (re)composition. */
    private var reloadChecks = 0

    /**
     * Host-memoised the way `NavHostComposeActivity.downloadControllerFor` memoises it -- and, like
     * the host's own `downloadSession` field, NOT saved state: a test that models an Activity
     * recreation drops this before restoring.
     */
    private var controller: DocumentSelectionController? = null

    /**
     * `NavHostComposeActivity.downloadSessionToken` modelled exactly: regenerated ONLY when a new
     * session (here, a new controller) is built, never otherwise. Unique per build rather than a
     * per-instance counter, for the same reason the host uses a UUID -- a counter restarting at zero
     * after a recreation would hand a rebuilt session the identity of the one it replaced.
     */
    private var sessionToken: String = "session-0"
    private var sessionBuilds = 0

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
                    onSelect = {}, onDelete = {},
                    onAbout = {}, onUnlock = {}, onStickyLanguage = {},
                ).also {
                    it.setTypeFilter(initialTypeFilter)
                    controller = it
                    sessionToken = "session-${++sessionBuilds}"
                }
            },
            sessionToken = { sessionToken },
            title = "Download",
            topBarActions = {},
            askIfWantToProceed = { calls.add("gate"); gateAnswer },
            requestNotificationPermission = { calls.add("permission") },
            refreshCatalogue = { refresh -> calls.add("refresh=$refresh") },
            onAutoDownload = { documentIds, downloadRecommended ->
                calls.add("autoDownload")
                autoDownloadArgs = documentIds to downloadRecommended
            },
            reloadCatalogueIfRequested = { reloadChecks++ },
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
            initialTypeFilter = { addons ->
                addonsSeenByTypeFilter = addons
                if (addons) DocTypeFilter.ADDON else DocTypeFilter.ALL
            },
        ),
        // Task 8's destination -- not exercised by this test, which is scoped to the Download arm.
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
            countLabel = { _, _ -> error("not exercised here") },
        ),
    )

    private fun setGraph(startDestination: String) {
        val d = deps()
        compose.setContent {
            graph(d, startDestination)
        }
        compose.waitForIdle()
    }

    /**
     * The same graph, hosted by a [StateRestorationTester] so the test can emulate the Activity
     * recreation [aRebuiltSessionUnderRestoredSaveableStateRerunsTheEntrySetup] is about: saveable
     * state is saved, the composition is thrown away and rebuilt, and the saved state is restored
     * into it -- exactly what a dark-mode toggle, a font-size change or a low-memory kill does to
     * this host (its manifest `configChanges` covers neither `uiMode` nor `fontScale`).
     */
    private fun setGraphWithStateRestoration(startDestination: String): StateRestorationTester {
        val d = deps()
        val tester = StateRestorationTester(compose)
        tester.setContent {
            graph(d, startDestination)
        }
        compose.waitForIdle()
        return tester
    }

    @Composable
    private fun graph(d: DownloadNavDeps, startDestination: String) {
        navController = rememberNavController()
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                NavHost(navController = navController, startDestination = startDestination) {
                    downloadNavGraph(navController, d)
                }
            }
        }
    }

    /**
     * The ONLY shape in which a route reaches a live host in production:
     * `NavHostComposeActivity.navigateToRoute` (`:706`) is
     * `controller.navigate(route) { launchSingleTop = true }`, and every inbound route --
     * `onNewIntent`'s and the pending-route replay's alike -- goes through it. A bare
     * `navController.navigate(route)` is NOT that contract: it always stacks a new entry with fresh
     * saveable state, which is the easy case. `launchSingleTop` onto an already-top `Download` takes
     * `launchSingleTopInternal`, which rebuilds the entry from the old one -- same id, same saved
     * state, composition never disposed -- which is the case the arm's one-shots actually have to
     * survive.
     */
    private fun navigateAsHostWould(route: String) {
        compose.runOnIdle { navController.navigate(route) { launchSingleTop = true } }
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
     * The defect this destination is most exposed to: its OWN overflow menu navigates to
     * `CustomRepositories`, which disposes this entry's composition
     * ([bothLifecycleSeamsAreReleasedWhenTheDestinationGoesAway] proves that), so anything in a
     * plain `LaunchedEffect(Unit)` runs again on the way back. Classic ran all of this in `onCreate`
     * and that round trip did not recreate the Activity -- re-running it would re-ask the download
     * question, re-fetch the catalogue and RE-ENQUEUE every auto-download.
     *
     * The reload check is the deliberate exception, and counting it is what proves the composition
     * genuinely re-ran rather than the test navigating nowhere.
     */
    @Test
    fun aRoundTripToAChildDestinationRerunsNothingButTheReloadCheck() {
        setGraph(NavRoutes.download(search = "ESV", documentIds = """[]"""))
        val c = assertNotNull(controller)
        assertEquals(1, reloadChecks)

        // The user moves off what the route seeded; coming back must not undo either.
        compose.runOnIdle { c.setQuery("mine") }
        compose.runOnIdle { navController.navigate(NavRoutes.progressStatus()) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(
            listOf("gate", "permission", "refresh=false", "autoDownload"),
            calls.filterNot { it.endsWith("+") },
            "the onCreate block ran again on the way back",
        )
        assertEquals("mine", c.query.value, "the route's search seed was re-applied over the user's query")
        assertTrue(c.searchModeActive.value, "the search bar the user left open was re-seeded shut")
        assertEquals(2, reloadChecks, "the entry did not actually recompose, so this test proves nothing")
    }

    /**
     * The other half of the same contract, driven through the shape production actually uses. The
     * host is `singleTop`, so a second `NavRoutes.download(...)` arrives through
     * `NavHostComposeActivity.navigateToRoute`, i.e. `navigate(route) { launchSingleTop = true }`
     * -- see [navigateAsHostWould]. That does NOT stack a fresh entry: `launchSingleTopInternal`
     * rebuilds the entry from the old one with the SAME id and the SAME saved state, and `NavHost`
     * keys its `AnimatedContent` on that id, so the arm is never disposed and its `rememberSaveable`
     * one-shots survive verbatim. A bare `navigate(route)` (what this test used before the final
     * review) sidesteps all of that and proves only the easy case.
     *
     * The route's own arguments DO change, so the entry is not equal to the old one and the arm
     * recomposes with them -- which is why keying the one-shots on (session, arguments) rather than
     * on a bare boolean is what makes this pass: the new route's filter, search state and
     * `onCreate` work must all apply, exactly as classic got by being launched afresh.
     */
    @Test
    fun aSecondEntryAppliesItsOwnTypeFilterAndSearchState() {
        setGraph(NavRoutes.download())
        val c = assertNotNull(controller)
        assertEquals(DocTypeFilter.ALL, c.selectedTypeFilter.value)

        compose.runOnIdle { c.setQuery("mine"); c.openSearch() }
        navigateAsHostWould(NavRoutes.download(addons = true))

        assertEquals(DocTypeFilter.ADDON, c.selectedTypeFilter.value, "the new entry's addons argument was dropped")
        assertEquals("", c.query.value, "the previous entry's query survived into a fresh launch")
        assertFalse(c.searchModeActive.value, "the previous entry's search bar survived into a fresh launch")
        // A re-delivered route IS a fresh launch, so classic's onCreate work runs for it -- exactly once.
        assertEquals(2, calls.count { it == "gate" })
    }

    /**
     * The same keying, seen from its OTHER symptom: an Activity recreation that preserves saved
     * state while the host rebuilds its (unsaved) `DownloadSession` field.
     *
     * `NavHostComposeActivity.downloadSession` is a plain Activity field -- not saved state -- while
     * the arm's one-shots are `rememberSaveable`. A recreation therefore restores the flags `true`
     * over a session rebuilt EMPTY, and a bare boolean would skip the gate, the permission request,
     * the catalogue refresh and the auto-download, leaving a blank list. This is NOT a narrow
     * process-death window: the host declares
     * `configChanges="keyboardHidden|orientation|screenSize|locale"` (`AndroidManifest.xml:137`),
     * so a system dark-mode toggle or a font-size change while this screen is open recreates it,
     * as do "Don't keep activities" and an ordinary low-memory kill. The worst instance is
     * first-run onboarding, where the dropped auto-download is the whole point of the screen.
     *
     * Dropping the memoised controller alongside the restore is what makes this the REAL defect
     * rather than a composition round trip: it models the host field that recreation does not carry.
     */
    @Test
    fun aRebuiltSessionUnderRestoredSaveableStateRerunsTheEntrySetup() {
        val tester = setGraphWithStateRestoration(
            NavRoutes.download(downloadRecommended = true, search = "ESV", documentIds = """[{"initials":"ESV"}]"""),
        )
        assertEquals(
            listOf("gate", "permission", "refresh=false", "autoDownload"),
            calls.filterNot { it.endsWith("+") },
        )
        assertEquals("""[{"initials":"ESV"}]""" to true, autoDownloadArgs)

        // The recreation: saveable state survives, the host's session field does not.
        controller = null
        autoDownloadArgs = null
        calls.clear()

        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(
            listOf("gate", "permission", "refresh=false", "autoDownload"),
            calls.filterNot { it.endsWith("+") },
            "the rebuilt session was left unseeded: the restored one-shots skipped classic's onCreate work",
        )
        assertEquals(
            """[{"initials":"ESV"}]""" to true,
            autoDownloadArgs,
            "the onboarding auto-download was silently dropped by the recreation",
        )
        assertEquals("ESV", assertNotNull(controller).query.value, "the route's search seed was not re-applied")
    }

    /**
     * The guard on the fix's other edge: keying on (session, arguments) must NOT make the arm
     * re-run its `onCreate` work on an ordinary round trip to a child destination, where the session
     * and the arguments are both unchanged. [aRoundTripToAChildDestinationRerunsNothingButTheReloadCheck]
     * proves that for a bare boolean; this pins it for the key, so a key that accidentally varied
     * per composition (a fresh token per call, say) could not pass.
     */
    @Test
    fun aRedeliveryOfTheSAMERouteChangesNothing() {
        setGraph(NavRoutes.download())
        val c = assertNotNull(controller)

        compose.runOnIdle { c.setQuery("mine"); c.openSearch() }
        navigateAsHostWould(NavRoutes.download())

        assertEquals(1, calls.count { it == "gate" }, "an identical re-delivery re-ran classic's onCreate work")
        assertEquals("mine", c.query.value, "an identical re-delivery clobbered the user's query")
        assertTrue(c.searchModeActive.value, "an identical re-delivery closed the user's search bar")
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
