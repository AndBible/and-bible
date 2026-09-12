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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.nav.NavResultIntents
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.mydocuments.nav.MyDocumentPagesDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps
import net.bible.sharedui.mydocuments.nav.myDocumentsNavGraph
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
 * The one COMPOSED test of `MyDocumentPages`'s navigation, driven through the real graph and the
 * real [NavResultChannel] -- modelled verbatim on [BookmarksInGraphResultTest], which is the
 * precedent for exactly this shape.
 *
 * `MyDocumentPages` is the batch's SECOND dual-entry destination (`ManageLabels` was the first, and
 * is `BookmarksInGraphResultTest`'s own subject) and, unlike `ManageLabels`, its parent --
 * `MyDocuments` -- does not exist yet: nav-graph slice 4 Task 6 builds it. So the in-graph mode here
 * is proved against a SYNTHETIC stand-in parent destination, registered in the SAME `NavHost` this
 * test builds, that consumes `myDocumentPagesResults.pending`/`consume()` in exactly the shape
 * Task 6's real `MyDocuments` arm will use (see nav-graph slice 4 Task 6's own brief, which quotes
 * that consuming `LaunchedEffect` verbatim). What is being proved is [NavResultChannel]'s own
 * publish-and-pop branch and `MyDocumentsNavGraph.kt`'s `myDocumentPagesResults.deliver(...)` call
 * site -- both real production code -- not `MyDocuments` itself, which this task does not build.
 *
 * `NavResultChannelGuardTest` proves the arm CONTAINS the right calls by walking the source text;
 * `NavResultChannelTest` proves `deliver`'s branch in isolation. Neither can tell you that the
 * branch actually takes the in-graph path once a real back stack is involved, that it applies the
 * result EXACTLY once, or that the destination pops back to its caller afterwards -- and those are
 * the whole of what this task's dual-entry claim rests on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class MyDocumentsInGraphResultTest {
    @get:Rule val compose = createComposeRule()

    /** What the stand-in parent destination received off the channel, in the order it received it. */
    private val appliedResults = mutableListOf<MyDocumentPagesResult>()

    private var exitHostCalls = 0
    private var channelExitWithResultCalls = 0

    /** The result the channel's `exitWithResult` branch was handed, so a test can pin the packed
     *  Intent [NavResultIntentsTest] pins in isolation -- this is what proves the SAME result
     *  reaches the SAME packing when driven through the real graph. */
    private var deliveredExitResult: MyDocumentPagesResult? = null

    /** The controller `MyDocumentPages` built for the most recent entry, captured the way
     *  `BookmarksInGraphResultTest` captures its `manageLabelsController` -- bypasses the real
     *  screen UI so a test can drive `openPage` directly. */
    private var pagesController: MyDocumentPagesController? = null

    private lateinit var navController: NavHostController

    private fun deps(): MyDocumentsNavDeps = MyDocumentsNavDeps(
        exitHost = { exitHostCalls++ },
        setWindowTitle = {},
        myDocumentPagesResults = NavResultChannel<MyDocumentPagesResult> { result ->
            channelExitWithResultCalls++
            deliveredExitResult = result
        },
        myDocumentPages = MyDocumentPagesDeps(
            controllerFor = { _, documentInitials, onResult ->
                MyDocumentPagesController(
                    onOpenPage = { id -> onResult(MyDocumentPagesResult.Selected(documentInitials, "page-$id")) },
                    onImport = {},
                    onExport = {},
                    onCreatePage = { _, _ -> },
                    onExportSelected = {},
                    onSave = { _, _, _ -> },
                ).also { pagesController = it }
            },
            titleFor = { documentName -> documentName },
            onImport = {},
            onExportSelected = {},
            onExportPage = {},
        ),
    )

    private fun setGraph(
        d: MyDocumentsNavDeps = deps(),
        startDestination: String = TEST_PARENT_ROUTE,
    ) {
        compose.setContent {
            navController = rememberNavController()
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    NavHost(navController = navController, startDestination = startDestination) {
                        // The stand-in for nav-graph slice 4 Task 6's MyDocuments arm: the same
                        // myDocumentPagesResults.pending/consume() shape that arm will use.
                        composable(TEST_PARENT_ROUTE) {
                            val pending by d.myDocumentPagesResults.pending.collectAsState()
                            LaunchedEffect(pending) {
                                val result = pending ?: return@LaunchedEffect
                                d.myDocumentPagesResults.consume()
                                appliedResults.add(result)
                            }
                        }
                        myDocumentsNavGraph(navController, d)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    private fun myDocumentPagesRoute(): String =
        NavRoutes.myDocumentPages(documentId = "1", documentInitials = "MyDoc_1", documentName = "My Doc")

    /**
     * The whole point of the task: a result produced by `MyDocumentPages` while a parent is on the
     * stack must reach that parent and pop, not exit the host.
     */
    @Test
    fun aMyDocumentPagesResultProducedInsideTheGraphReachesTheParentAndPops() {
        setGraph()
        assertEquals(TEST_PARENT_ROUTE, currentRoute)

        compose.runOnIdle { navController.navigate(myDocumentPagesRoute()) }
        compose.waitForIdle()
        assertEquals(NavRoutes.MY_DOCUMENT_PAGES_PATTERN, currentRoute)

        compose.runOnIdle { assertNotNull(pagesController).openPage(1L) }
        compose.waitForIdle()

        // The BRANCH first, then its consequences -- BookmarksInGraphResultTest's own discipline.
        assertEquals(
            0,
            channelExitWithResultCalls,
            "deliver() took its EXIT branch: with a parent on the stack it must publish to pending " +
                "and pop instead",
        )
        assertEquals(0, exitHostCalls, "no destination should have called deps.exitHost")
        assertEquals(listOf<MyDocumentPagesResult>(MyDocumentPagesResult.Selected("MyDoc_1", "page-1")), appliedResults)
        assertEquals(TEST_PARENT_ROUTE, currentRoute)
    }

    /**
     * `consume()` clears the channel in the same breath as reading it, so the consuming
     * `LaunchedEffect` cannot apply the same result a second time when the parent recomposes.
     */
    @Test
    fun aDeliveredResultIsAppliedExactlyOnceAcrossRecompositions() {
        setGraph()

        compose.runOnIdle { navController.navigate(myDocumentPagesRoute()) }
        compose.waitForIdle()
        compose.runOnIdle { assertNotNull(pagesController).openPage(1L) }
        compose.waitForIdle()

        // Force further recompositions of the parent the same way a user would: go into the child
        // again and come back without delivering anything new from the second visit.
        compose.runOnIdle { navController.navigate(myDocumentPagesRoute()) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(listOf<MyDocumentPagesResult>(MyDocumentPagesResult.Selected("MyDoc_1", "page-1")), appliedResults)
    }

    /**
     * [NavResultChannel]'s OTHER branch, end to end -- the one `CurrentGeneralBookPage` takes today.
     * With no parent entry on the stack, `deliver` must `exitWithResult`, and the result it is
     * handed must be exactly what [NavResultIntents.forMyDocumentPages] packs for
     * `MainBibleActivity` to read -- pinning that entering at `NavRoutes.myDocumentPages(...)` and
     * picking a page produces the Step-1 Intent, driven through the real graph rather than called
     * directly.
     */
    @Test
    fun aMyDocumentPagesResultProducedAsTheStartDestinationExitsTheHost() {
        setGraph(startDestination = myDocumentPagesRoute())
        assertEquals(NavRoutes.MY_DOCUMENT_PAGES_PATTERN, currentRoute)

        compose.runOnIdle { assertNotNull(pagesController).openPage(1L) }
        compose.waitForIdle()

        assertEquals(
            1,
            channelExitWithResultCalls,
            "deliver() did not take its EXIT branch. As the START destination there is no parent " +
                "entry, so the result must leave through exitWithResult",
        )
        assertTrue(appliedResults.isEmpty(), "nothing may be applied in-graph: appliedResults=$appliedResults")
        assertEquals(0, exitHostCalls, "the exit must carry the RESULT, not be a bare deps.exitHost()")

        val result = assertNotNull(deliveredExitResult)
        assertEquals(MyDocumentPagesResult.Selected("MyDoc_1", "page-1"), result)

        val activityResult = NavResultIntents.forMyDocumentPages(result)
        val extras = assertNotNull(activityResult.data?.extras)
        assertEquals(android.app.Activity.RESULT_OK, activityResult.resultCode)
        assertEquals(ActivityResultKind.MyDocumentPages.name, extras.getString(ActivityResultKind.EXTRA))
        assertEquals("MyDoc_1", extras.getString("documentInitials"))
        assertEquals("page-1", extras.getString("pageKey"))
    }

    private companion object {
        const val TEST_PARENT_ROUTE = "test-parent"
    }
}
