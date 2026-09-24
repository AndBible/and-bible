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
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.backup.BackupController
import net.bible.sharedcore.backup.BackupService
import net.bible.sharedcore.backup.BackupState
import net.bible.sharedcore.backup.ToggleKind
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.backup.nav.BackupNavDeps
import net.bible.sharedui.backup.nav.backupNavGraph
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Slice 8 C3: the Backup destination over a fake [BackupService] -- what `BackupComposeActivity` did in
 * `onCreate`/`onResume` (`load()`), and what leaving it does in-graph (back to whatever was below).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BackupInGraphTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private var loads = 0
    private var exitHostCalls = 0
    private val titles = mutableListOf<String>()

    private inner class FakeBackupService : BackupService {
        override suspend fun load(): BackupState { loads++; return BackupState() }
        override fun setToggle(kind: ToggleKind, value: Boolean) = Unit
        override suspend fun backup() = Unit
        override suspend fun restore() = Unit
        override suspend fun exportFile(token: String) = Unit
        override suspend fun restoreFile(token: String) = Unit
        override suspend fun resetDb(dbFileName: String) = Unit
    }

    private fun setGraph(start: String) {
        val deps = BackupNavDeps(
            exitHost = { exitHostCalls++ },
            setWindowTitle = { titles += it },
            windowTitle = "Backup & Restore",
            controllerFor = { BackupController(FakeBackupService(), CoroutineScope(Dispatchers.Main)) },
        )
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = start) {
                        composable(NavRoutes.READING) { Text("reading") }
                        backupNavGraph(navController, deps)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun enteringBackupLoadsItsStateAndSetsTheWindowTitle() {
        setGraph(NavRoutes.READING)
        compose.runOnIdle { navController.navigate(NavRoutes.BACKUP) }
        compose.waitForIdle()
        assertEquals(NavRoutes.BACKUP, navController.currentBackStackEntry?.destination?.route)
        assertTrue(loads >= 1, "BackupComposeActivity loaded in onCreate/onResume; the destination must load on entry")
        assertEquals("Backup & Restore", titles.last())
    }

    @Test
    fun backLeavesBackupForWhateverWasBelow() {
        setGraph(NavRoutes.READING)
        compose.runOnIdle { navController.navigate(NavRoutes.BACKUP) }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(NavRoutes.READING, navController.currentBackStackEntry?.destination?.route)
        assertEquals(0, exitHostCalls, "an in-graph Backup must not leave the host")
    }

    /**
     * Review minor 2: Backup as the host's START destination (the cross-Activity launch from
     * `StartupActivity`'s crash check). Up has nothing to pop, so it must leave the host exactly once.
     */
    @Test
    fun upOnBackupAsTheStartDestinationExitsTheHostOnce() {
        setGraph(NavRoutes.BACKUP)
        clickUp()
        assertEquals(1, exitHostCalls, "Up on the start destination has nothing to pop, so it leaves the host")
    }

    /**
     * `AbScaffold`'s Up icon has no content description, so this clicks the top-left-most clickable node:
     * the top app bar's navigation icon. Zero-size nodes are skipped because clipped, off-screen list rows
     * report `Rect(0, 0, 0, 0)`. Picking any other button would not call `exitHost`, so a wrong pick fails
     * the test instead of passing it.
     */
    private fun clickUp() {
        val up = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            .minWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        compose.onNode(SemanticsMatcher("the Up button") { it.id == up.id }).performClick()
        compose.waitForIdle()
    }
}
