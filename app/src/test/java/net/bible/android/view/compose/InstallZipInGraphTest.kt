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
import androidx.compose.material3.Text
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.installzip.InstallZipFlow
import net.bible.service.common.DisplayColorMode
import net.bible.service.installzip.InstallJobState
import net.bible.service.installzip.InstallPhase
import net.bible.sharedcore.nav.InstallZipResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.installzip.InstallUiState
import net.bible.sharedui.installzip.nav.InstallZipNavDeps
import net.bible.sharedui.installzip.nav.InstallZipSession
import net.bible.sharedui.installzip.nav.installZipNavGraph
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Slice 8 D1 (spec §3.2): the InstallZip arm over a fake session -- the destination-scoped effects. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class InstallZipInGraphTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private var locks = 0
    private var restores = 0
    private var bound = 0
    private var unbound = 0
    private var channelExits = 0
    private val sessions = mutableListOf<Triple<String?, List<String>, FakeSession>>()
    private val results = NavResultChannel<InstallZipResult> { channelExits++ }

    private val running = InstallJobState("job-1", "module.zip", InstallPhase.Acquiring(10))

    private class FakeSession(private val onFinished: (InstallZipResult) -> Unit) : InstallZipSession {
        override val state: StateFlow<InstallUiState?> = MutableStateFlow(InstallUiState.FormatInfo("formats"))
        var starts = 0
        override fun start() { starts++ }
        override fun confirm() = Unit
        override fun dismiss() = onFinished(InstallZipResult.CANCELED)
        override fun back() = onFinished(InstallZipResult.CANCELED)
        override fun close() = Unit
    }

    private fun fakeSessionFor(action: String?, uris: List<String>, onFinished: (InstallZipResult) -> Unit): InstallZipSession =
        FakeSession(onFinished).also { sessions += Triple(action, uris, it) }

    // ——— the real InstallZipFlow, for the entry-lifetime tests (controller ruling on D1 concern 1) ———
    private val jobs = MutableStateFlow<List<InstallJobState>>(emptyList())
    private val enqueued = mutableListOf<List<Uri>>()
    private var realSessions = 0

    private fun realSessionFor(action: String?, uris: List<String>, onFinished: (InstallZipResult) -> Unit): InstallZipSession {
        realSessions++
        return InstallZipFlow(
            scope = compose.activity.lifecycleScope,
            action = action,
            uris = uris.map(Uri::parse),
            seams = InstallZipFlow.Seams(
                jobs = jobs,
                isStudyPadExport = { false },
                displayName = { "module.zip" },
                formatsText = { "formats" },
                pickFile = { null },
                enqueue = { list, _ -> enqueued += list },
                resolveDecision = { _, _ -> },
                mapPhase = { InstallUiState.Progress(it.displayName, "status", percent = null, indeterminate = true) },
                requestNotificationPermission = {},
                noFileManager = {},
            ),
            onFinished = onFinished,
        )
    }

    private fun setGraph(
        sessionFor: (String?, List<String>, (InstallZipResult) -> Unit) -> InstallZipSession = ::fakeSessionFor,
    ) {
        val deps = InstallZipNavDeps(
            setWindowTitle = {},
            windowTitle = "Install",
            installZipResults = results,
            sessionFor = sessionFor,
            lockOrientation = { locks++; { restores++ } },
            hostBound = { bound++ },
            hostUnbound = { unbound++ },
        )
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = NavRoutes.READING) {
                        composable(NavRoutes.READING) { Text("reading") }
                        composable(OTHER) { Text("other") }
                        installZipNavGraph(navController, deps)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theRouteArgumentsReachTheSessionAndItIsStartedOnce() {
        setGraph()
        compose.runOnIdle {
            navController.navigate(NavRoutes.installZip("android.intent.action.SEND_MULTIPLE", listOf("content://a/1,2.zip", "content://a/b.epub")))
        }
        compose.waitForIdle()
        val (action, uris, session) = sessions.single()
        assertEquals("android.intent.action.SEND_MULTIPLE", action)
        assertEquals(listOf("content://a/1,2.zip", "content://a/b.epub"), uris)
        assertEquals(1, session.starts)
    }

    /**
     * Plan Review Focus #5 / C1's open point: C1 checked the URI list encoding against a SIMULATED single
     * `decodeArg`. This pushes URIs holding `,`, an already-escaped `%xx`, a literal `%`, `&`, `=`, `?`, a
     * space and non-ASCII through the REAL navigation library's route matching and argument decoding.
     * The session must receive them byte-for-byte, as ONE argument that does not split on the comma.
     */
    @Test
    fun urisWithCommasPercentsAndNonAsciiReachTheSessionExactly() {
        val tricky = listOf(
            "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2FDownload%2Fä ö,1.zip",
            "content://x/100% ñ&a=b?c#d.epub",
            "content://x/日本語,テスト.zip",
        )
        setGraph()
        compose.runOnIdle { navController.navigate(NavRoutes.installZip("android.intent.action.SEND_MULTIPLE", tricky)) }
        compose.waitForIdle()
        val (action, uris, _) = sessions.single()
        assertEquals("android.intent.action.SEND_MULTIPLE", action)
        assertEquals(tricky, uris)
    }

    /**
     * The session belongs to the NavBackStackEntry, not to its composition. Covering InstallZip disposes
     * the destination's composition; coming back recomposes it, and that must NOT start a second session
     * (which would enqueue the shared files a second time).
     */
    @Test
    fun coveringInstallZipAndComingBackDoesNotEnqueueTheSharedFilesTwice() {
        setGraph(::realSessionFor)
        compose.runOnIdle { navController.navigate(NavRoutes.installZip("android.intent.action.SEND", listOf("content://x/module.zip"))) }
        compose.waitForIdle()
        jobs.value = listOf(running)
        compose.runOnIdle { navController.navigate(OTHER) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(NavRoutes.INSTALL_ZIP_PATTERN, navController.currentBackStackEntry?.destination?.route)
        assertEquals(listOf(listOf(Uri.parse("content://x/module.zip"))), enqueued, "enqueued exactly once")
        assertEquals(1, realSessions, "one session per back-stack entry")
    }

    /**
     * Popped by someone else (not its own back/finish) while its job runs: the session is closed with its
     * entry, so the job draining later neither answers nor pops whatever destination is current then.
     */
    @Test
    fun anInstallZipEntryPoppedFromOutsideIsClosedAndPopsNothingLater() {
        setGraph(::realSessionFor)
        compose.runOnIdle { navController.navigate(OTHER) }
        compose.waitForIdle()
        compose.runOnIdle { navController.navigate(NavRoutes.installZip("android.intent.action.SEND", listOf("content://x/module.zip"))) }
        compose.waitForIdle()
        jobs.value = listOf(running)
        compose.waitForIdle()

        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()
        assertEquals(OTHER, navController.currentBackStackEntry?.destination?.route)

        compose.runOnIdle { jobs.value = emptyList() }
        compose.waitForIdle()

        assertEquals(OTHER, navController.currentBackStackEntry?.destination?.route, "nothing else was popped")
        assertEquals(null, results.consume(), "a closed session answers nothing")
        assertEquals(0, channelExits)
    }

    /**
     * Fix round 2. A second share while InstallZip is on top: `navigateToRoute`'s `launchSingleTop` REPLACES
     * the top entry with a new object carrying the SAME id and the new arguments (pinned here first, since
     * the fix depends on it). Controller ruling: the re-share replaces the session, as classic started a
     * fresh Activity per share -- the new URIs are enqueued, and back still pops.
     */
    @Test
    fun aReShareWhileInstallZipIsOnTopReplacesTheSession() {
        setGraph(::realSessionFor)
        compose.runOnIdle { navController.navigate(NavRoutes.installZip("android.intent.action.SEND", listOf("content://x/a.zip"))) }
        compose.waitForIdle()
        val first = navController.currentBackStackEntry!!
        compose.runOnIdle {
            navController.navigate(NavRoutes.installZip("android.intent.action.SEND", listOf("content://x/b.zip"))) {
                launchSingleTop = true
            }
        }
        compose.waitForIdle()
        val second = navController.currentBackStackEntry!!
        assertEquals(first.id, second.id, "library behaviour: a singleTop re-navigate keeps the entry id")
        assertTrue(first !== second, "library behaviour: …but the entry object is replaced")
        // Measured (navigation 2.9.2): the replaced object is NOT moved to DESTROYED (it stays RESUMED), so
        // InstallZipEntrySessions closes its session on replacement rather than waiting for its ON_DESTROY.
        assertEquals(1, navController.currentBackStack.value.count { it.destination.route == NavRoutes.INSTALL_ZIP_PATTERN })

        assertEquals(listOf(listOf(Uri.parse("content://x/a.zip")), listOf(Uri.parse("content://x/b.zip"))), enqueued)
        assertEquals(2, realSessions)

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(NavRoutes.READING, navController.currentBackStackEntry?.destination?.route, "back still pops")
        assertEquals(InstallZipResult.CANCELED, results.consume())
    }

    /** An answer given while something covers InstallZip waits for its entry, then pops that entry only. */
    @Test
    fun anAnswerWhileCoveredWaitsForItsOwnEntryToBeOnTop() {
        setGraph()
        compose.runOnIdle { navController.navigate(NavRoutes.installZip()) }
        compose.waitForIdle()
        compose.runOnIdle { navController.navigate(OTHER) }
        compose.waitForIdle()

        compose.runOnIdle { sessions.single().third.dismiss() }
        compose.waitForIdle()
        assertEquals(OTHER, navController.currentBackStackEntry?.destination?.route, "the covering destination is not popped")
        assertEquals(null, results.pending.value)

        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()
        assertEquals(NavRoutes.READING, navController.currentBackStackEntry?.destination?.route)
        assertEquals(InstallZipResult.CANCELED, results.consume())
        assertEquals(1, sessions.size)
    }

    @Test
    fun thePortraitLockAndTheServiceBindingFollowTheDestination() {
        setGraph()
        compose.runOnIdle { navController.navigate(NavRoutes.installZip()) }
        compose.waitForIdle()
        assertEquals(1, locks, "the destination locks portrait while it is on screen")
        assertEquals(1, bound, "DocumentInstallService.hostBound follows the destination's STARTED state")

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(NavRoutes.READING, navController.currentBackStackEntry?.destination?.route)
        assertEquals(1, restores, "…and restores the previous orientation when it leaves")
        assertEquals(1, unbound)
        assertEquals(InstallZipResult.CANCELED, results.consume(), "back is a CANCELED answer, as the Activity's back was")
        assertEquals(0, channelExits, "an in-graph InstallZip publishes and pops; it does not exit the host")
    }

    private companion object {
        const val OTHER = "test/other"
    }
}
