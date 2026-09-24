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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
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

    private class FakeSession(private val onFinished: (InstallZipResult) -> Unit) : InstallZipSession {
        override val state: StateFlow<InstallUiState?> = MutableStateFlow(InstallUiState.FormatInfo("formats"))
        var starts = 0
        override fun start() { starts++ }
        override fun confirm() = Unit
        override fun dismiss() = onFinished(InstallZipResult.CANCELED)
        override fun back() = onFinished(InstallZipResult.CANCELED)
    }

    private fun setGraph() {
        val deps = InstallZipNavDeps(
            setWindowTitle = {},
            windowTitle = "Install",
            installZipResults = results,
            sessionFor = { action, uris, onFinished -> FakeSession(onFinished).also { sessions += Triple(action, uris, it) } },
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
}
