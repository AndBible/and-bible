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

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.android.view.activity.nav.NavResultIntents
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.mydocuments.MyDocItem
import net.bible.sharedcore.mydocuments.MyDocumentPagesController
import net.bible.sharedcore.mydocuments.MyDocumentsController
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.mydocuments.nav.MyDocumentPagesDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsDeps
import net.bible.sharedui.mydocuments.nav.MyDocumentsNavDeps
import net.bible.sharedui.mydocuments.nav.myDocumentsNavGraph
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.theme.AbTheme
import kotlinx.coroutines.flow.MutableStateFlow
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
 * The one COMPOSED test of the My-Documents cluster's navigation, driven through the real graph
 * and the real [NavResultChannel] -- modelled verbatim on [BookmarksInGraphResultTest], which is
 * the precedent for exactly this shape.
 *
 * `MyDocumentPages` is the batch's SECOND dual-entry destination (`ManageLabels` was the first, and
 * is `BookmarksInGraphResultTest`'s own subject). Nav-graph slice 4 Task 5 built it alone, before
 * its real parent `MyDocuments` existed, and proved the in-graph mode against a SYNTHETIC stand-in
 * parent destination ([TEST_PARENT_ROUTE], still registered below) that consumed
 * `myDocumentPagesResults.pending`/`consume()` in exactly the shape the real arm now uses. Task 6
 * built that real arm, so this class also proves the TWO-LEVEL round trip end to end: a page picked
 * in `MyDocumentPages`, relayed by the real `MyDocuments` arm, bubbling all the way out of the host
 * -- since `MyDocuments` is itself the graph's start destination and has no parent of its own.
 *
 * `NavResultChannelGuardTest` proves the arms CONTAIN the right calls by walking the source text;
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

    /** The result the pages channel's `exitWithResult` branch was handed, so a test can pin the
     *  packed Intent [NavResultIntentsTest] pins in isolation -- this is what proves the SAME
     *  result reaches the SAME packing when driven through the real graph. */
    private var deliveredExitResult: MyDocumentPagesResult? = null

    /** `MyDocuments`' own channel: how many times its `exitWithResult` branch fired, and what it was
     *  handed most recently -- [myDocumentPagesRoute]'s twin for the PARENT destination, since
     *  `MyDocuments` is itself the graph's start destination and has no parent of its own. */
    private var myDocumentsExitCalls = 0
    private var deliveredMyDocumentsResult: MyDocumentsResult? = null

    /** The controller `MyDocumentPages` built for the most recent entry, captured the way
     *  `BookmarksInGraphResultTest` captures its `manageLabelsController` -- bypasses the real
     *  screen UI so a test can drive `openPage` directly. */
    private var pagesController: MyDocumentPagesController? = null

    /** The `MyDocuments` arm's own controller, captured the same way, plus what its `onSave` seam
     *  (classic `applyChanges`) actually received -- the subject of the D6 latch test below. */
    private var myDocumentsController: MyDocumentsController? = null
    private val myDocumentsSaves = mutableListOf<Set<Long>>()

    private lateinit var navController: NavHostController

    private fun deps(): MyDocumentsNavDeps = MyDocumentsNavDeps(
        exitHost = { exitHostCalls++ },
        setWindowTitle = {},
        myDocumentPagesResults = NavResultChannel<MyDocumentPagesResult> { result ->
            channelExitWithResultCalls++
            deliveredExitResult = result
        },
        myDocumentsResults = NavResultChannel<MyDocumentsResult> { result ->
            myDocumentsExitCalls++
            deliveredMyDocumentsResult = result
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
        // See MyDocumentsDeps.controllerFor's own kdoc: nothing in MyDocumentsController itself
        // calls onResult, so it is unused here too -- the test drives the two-level relay by
        // navigating directly to myDocumentPagesRoute(), the same technique the other tests below
        // use, rather than through the arm's own onOpen (which this fake controller never reaches).
        myDocuments = MyDocumentsDeps(
            controllerFor = {
                MyDocumentsController(
                    onOpen = {},
                    onImport = {},
                    onExport = {},
                    onCreate = {},
                    onExportSelected = {},
                    onSave = { _, changed, _ -> myDocumentsSaves.add(changed) },
                ).also { myDocumentsController = it }
            },
            title = "My Documents",
            onImport = {},
            onExport = {},
            onExportSelected = {},
            importNamePrompt = MutableStateFlow(null),
            onConfirmImport = {},
            onDismissImport = {},
            routeForPages = { myDocumentPagesRoute() },
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

    // F60 fix round 1 -- the switch-document guard below.
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val switchDocumentText: String get() = context.getString(R.string.my_documents_switch_document)

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

    /**
     * Nav-graph slice 4, Task 6's own proof: the real `MyDocuments` arm, not the synthetic stand-in
     * above, consuming `myDocumentPagesResults.pending`/`consume()` and re-delivering
     * [MyDocumentsResult.Selected] on `myDocumentsResults`. `MyDocuments` is entered here as the
     * graph's START destination -- the only way it is ever reached (see
     * `MyDocumentsNavDeps.myDocumentsResults`'s own kdoc) -- so once the relayed result reaches it,
     * it has no parent of its OWN either, and must exit the HOST in turn: the two-level bubble-up
     * `MyDocumentPages` -> `MyDocuments` -> host that this task's whole design rests on.
     */
    @Test
    fun aPageSelectedTwoLevelsInBubblesUpThroughMyDocumentsAndExitsTheHost() {
        setGraph(startDestination = NavRoutes.MY_DOCUMENTS_PATTERN)
        assertEquals(NavRoutes.MY_DOCUMENTS_PATTERN, currentRoute)

        compose.runOnIdle { navController.navigate(myDocumentPagesRoute()) }
        compose.waitForIdle()
        assertEquals(NavRoutes.MY_DOCUMENT_PAGES_PATTERN, currentRoute)

        compose.runOnIdle { assertNotNull(pagesController).openPage(1L) }
        compose.waitForIdle()

        // First level: MyDocumentPages had a parent (MyDocuments) on the stack, so it must publish
        // to pending and pop, not exit the host itself.
        assertEquals(
            0,
            channelExitWithResultCalls,
            "MyDocumentPages must not exit directly -- MyDocuments was on the stack to receive it",
        )
        assertEquals(NavRoutes.MY_DOCUMENTS_PATTERN, currentRoute, "MyDocumentPages did not pop back to MyDocuments")

        // Second level: MyDocuments itself has NO parent (it is the graph's start destination), so
        // its own re-delivery of the relayed Selected must exit the HOST.
        assertEquals(0, exitHostCalls, "the exit must carry the result, not be a bare deps.exitHost()")
        assertEquals(1, myDocumentsExitCalls, "MyDocuments did not exit the host with the relayed result")

        val result = assertNotNull(deliveredMyDocumentsResult)
        assertEquals(MyDocumentsResult.Selected("MyDoc_1", "page-1"), result)

        val activityResult = NavResultIntents.forMyDocuments(result)
        val extras = assertNotNull(activityResult.data?.extras)
        assertEquals(android.app.Activity.RESULT_OK, activityResult.resultCode)
        assertEquals(ActivityResultKind.MyDocuments.name, extras.getString(ActivityResultKind.EXTRA))
        assertEquals("MyDoc_1", extras.getString("documentInitials"))
        assertEquals("page-1", extras.getString("pageKey"))
    }

    /**
     * Plan D6's latch, on the ONE path the batch left unlatched: "open a document while dirty".
     *
     * `onOpen` auto-saves before navigating (classic `MyDocumentsComposeActivity:212-213`), and
     * `save()` deliberately does NOT clear `dirty` -- that is D6's whole premise. So unless `onOpen`
     * also latches `finished`, the arm's `DisposableEffect(controller)` fires on the way in to
     * `MyDocumentPages` and runs a SECOND `applyChanges`: a second delete pass and a second
     * `AiDocPagesChangedEvent` on top of a save the user already got. Every other exit from this arm
     * (Save, Dismiss, the relayed page selection) latches; this one did not, which is exactly the
     * asymmetry D6 exists to prevent. The batch never wrote a dispose-while-dirty test for D6 at
     * all -- this is it.
     */
    @Test
    fun openingADocumentWhileDirtyLatchesFinishedSoTheDisposeDoesNotSaveAgain() {
        setGraph(startDestination = NavRoutes.MY_DOCUMENTS_PATTERN)
        val c = assertNotNull(myDocumentsController)

        compose.runOnIdle {
            c.setDocuments(
                listOf(
                    MyDocItem(
                        id = 1L,
                        initials = "MyDoc_1",
                        name = "My Doc",
                        description = "",
                        isAiGenerated = false,
                        canDelete = true,
                    ),
                ),
            )
        }
        // A pending edit: dirty, and save() will not clear it.
        compose.runOnIdle { c.rename(1L, "Renamed") }
        compose.waitForIdle()
        assertTrue(c.dirty.value, "the arm's premise: an edit leaves the controller dirty")

        // The row click IS onOpen -- the arm's own lambda, not the controller's onOpen seam.
        compose.onNodeWithText("Renamed").performClick()
        compose.waitForIdle()

        assertEquals(NavRoutes.MY_DOCUMENT_PAGES_PATTERN, currentRoute, "onOpen did not navigate")
        assertTrue(c.dirty.value, "save() must NOT clear dirty -- if it did, this test proves nothing")
        assertEquals(
            listOf(setOf(1L)),
            myDocumentsSaves,
            "the dispose ran a SECOND applyChanges on top of onOpen's own save (plan D6's latch is missing)",
        )
    }

    /**
     * F60 fix round 1: repeated document switches must not grow the back stack. The original
     * `onSwitchDocument` wiring popped only the LEAVING `MyDocumentPages` entry
     * (`popUpTo(MY_DOCUMENT_PAGES_PATTERN) { inclusive = true }`) with no `launchSingleTop`, so each
     * switch pushed a BRAND NEW `MyDocuments` entry on top of the one already sitting below `Pages`
     * (reached via the normal `MyDocuments -> open doc -> Pages` path) instead of reusing it --
     * the stack grew by one entry per switch, and the comment/commit message claiming otherwise
     * were wrong. `launchSingleTop = true` makes `navigate` reuse the (now-top-of-stack, after the
     * pop) existing `MyDocuments` entry instead of stacking a duplicate.
     */
    @Test
    fun switchingDocumentsRepeatedlyDoesNotGrowTheBackStack() {
        setGraph(startDestination = NavRoutes.MY_DOCUMENTS_PATTERN)

        fun openThenSwitch() {
            compose.runOnIdle { navController.navigate(myDocumentPagesRoute()) }
            compose.waitForIdle()
            assertEquals(NavRoutes.MY_DOCUMENT_PAGES_PATTERN, currentRoute)
            compose.onNodeWithContentDescription(switchDocumentText).performClick()
            compose.waitForIdle()
            assertEquals(NavRoutes.MY_DOCUMENTS_PATTERN, currentRoute, "switch did not land back on MyDocuments")
        }

        openThenSwitch()
        val sizeAfterFirstSwitch = navController.currentBackStack.value.size

        openThenSwitch()
        val sizeAfterSecondSwitch = navController.currentBackStack.value.size

        assertEquals(
            sizeAfterFirstSwitch, sizeAfterSecondSwitch,
            "back stack grew between the first and second switch -- each switch is leaking a MyDocuments entry",
        )

        // One back press from the page list must land directly on MyDocuments -- not on a stray
        // extra MyDocuments entry stacked below a first, which a growing stack would produce.
        compose.runOnIdle { navController.navigate(myDocumentPagesRoute()) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()
        assertEquals(
            NavRoutes.MY_DOCUMENTS_PATTERN, currentRoute,
            "one back press from the page list did not land on MyDocuments",
        )
    }

    private companion object {
        const val TEST_PARENT_ROUTE = "test-parent"
    }
}
