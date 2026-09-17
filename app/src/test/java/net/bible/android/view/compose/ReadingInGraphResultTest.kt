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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.nav.ReadingResultKind
import net.bible.android.view.activity.nav.ReadingResultRequests
import net.bible.android.view.activity.nav.readingResultCollector
import net.bible.sharedcore.nav.BookmarkResult
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.MyDocumentPagesResult
import net.bible.sharedcore.nav.MyDocumentsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.ReadingProgressResult
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.reading.nav.ReadingResultCollector
import net.bible.sharedui.reading.nav.readingNavGraph
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The regression test for reading-host re-typing T8c**, driven over a real `NavHost`, real
 * [NavResultChannel]s and the production [readingResultCollector]/[ReadingResultRequests] pair —
 * the twin of [WorkspaceInGraphResultTest], and it exists for the same reason.
 *
 * **What was broken, and why 2903 green tests could not see it.** T8b flipped the launcher onto
 * `NavHostComposeActivity`, so the reading view's host and the seven screens it opens for a result
 * became the SAME `android:launchMode="singleTop"` component. A `startActivityForResult`/
 * `awaitIntent` aimed at it is therefore answered by `onNewIntent` on the live instance: no Activity
 * result is ever produced, the requested route is PUSHED onto the live graph above `reading`, the
 * child publishes its answer into its channel's `pending` slot and pops back to the reading
 * destination — which collected nothing. Assign labels, Hide labels, workspace auto-assign,
 * StudyPads, the StudyPad and my-document key choosers, Reading progress, the bookmark list and the
 * my-documents list all silently discarded what the user picked. Nothing in the suite composed the
 * reading destination with a child on top of it, so nothing could notice.
 *
 * **Against the pre-fix tree every test below fails**, in the most direct way there is: the arm
 * composed no collector at all, so `applied` stays empty and the channel's `pending` stays set
 * forever. [theArmIsWhatCollects] is the mutation that proves it — remove the `deps.results` loop
 * from `readingNavGraph` and all five flows go red together.
 *
 * Design §1.1 is pinned as in the two sibling tests: the host-side `exitWithResult` branch must
 * never be taken for an in-graph delivery, so [channelExits] must stay 0 everywhere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingInGraphResultTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    /** How often ANY channel took its `exitWithResult` branch. Must stay 0 for an in-graph answer. */
    private var channelExits = 0

    /** Every Ruling-D line the production collector logged. */
    private val logged = mutableListOf<String>()

    /** `kind to requestCode` for every answer that reached an applier. */
    private val applied = mutableListOf<Pair<ReadingResultKind, Int>>()

    /** The payloads, so "applied" is not satisfied by an applier that threw the answer away. */
    private val payloads = mutableListOf<Any>()

    private val requests = ReadingResultRequests()

    private val manageLabelsResults = NavResultChannel<ManageLabelsResult> { channelExits++ }
    private val myDocumentPagesResults = NavResultChannel<MyDocumentPagesResult> { channelExits++ }
    private val readingProgressResults = NavResultChannel<ReadingProgressResult> { channelExits++ }
    private val bookmarkResults = NavResultChannel<BookmarkResult> { channelExits++ }
    private val myDocumentsResults = NavResultChannel<MyDocumentsResult> { channelExits++ }

    /** Exactly `NavHostComposeActivity.readingResultCollectors`' five entries, minus the packing. */
    private fun collectors(): List<ReadingResultCollector<*>> = listOf(
        collector(manageLabelsResults, ReadingResultKind.ManageLabels),
        collector(myDocumentPagesResults, ReadingResultKind.MyDocumentPages),
        collector(readingProgressResults, ReadingResultKind.ReadingProgress),
        collector(bookmarkResults, ReadingResultKind.Bookmarks),
        collector(myDocumentsResults, ReadingResultKind.MyDocuments),
    )

    private fun <T : Any> collector(channel: NavResultChannel<T>, kind: ReadingResultKind) =
        readingResultCollector(
            resultChannel = channel,
            kind = kind,
            requests = requests,
            log = { logged += it },
        ) { result, requestCode ->
            applied += kind to requestCode
            payloads += result
        }

    // ——— harness ————————————————————————————————————————————————————————————————————————————————

    private fun setGraph(results: List<ReadingResultCollector<*>> = collectors()) {
        val deps = ReadingNavDeps(
            host = this,
            windowTitle = "AndBible",
            content = { Text("reading view") },
            onKey = { false },
            onScreenTurnedOn = {},
            onScreenTurnedOff = {},
            setWindowTitle = {},
            results = results,
        )
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = NavRoutes.READING) {
                readingNavGraph(navController, deps)
                // Stand-ins for the five result-producing destinations. What is under test is that
                // the READING arm collects their channels, not what any of them draws -- and a
                // stand-in is what makes the back stack the real thing it has to be: `reading`
                // underneath, the producer on top, which is exactly what the host's own
                // `onNewIntent` -> `navigateToRoute` builds.
                composable(
                    route = NavRoutes.MANAGE_LABELS_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_MANAGE_LABELS_DATA) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                    ),
                ) { Text("labels") }
                composable(
                    route = NavRoutes.MY_DOCUMENT_PAGES_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_DOCUMENT_ID) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                        navArgument(NavRoutes.ARG_DOCUMENT_INITIALS) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                        navArgument(NavRoutes.ARG_DOCUMENT_NAME) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                    ),
                ) { Text("pages") }
                composable(
                    route = NavRoutes.READING_PROGRESS_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_TAB) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                    ),
                ) { Text("progress") }
                composable(
                    route = NavRoutes.BOOKMARKS_PATTERN,
                    arguments = listOf(
                        navArgument(NavRoutes.ARG_LABEL_NO) {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        },
                    ),
                ) { Text("bookmarks") }
                composable(route = NavRoutes.MY_DOCUMENTS_PATTERN) { Text("documents") }
            }
        }
        compose.waitForIdle()
    }

    private val currentRoute: String?
        get() = navController.currentBackStackEntry?.destination?.route

    /**
     * What the host does for a self-launch: record the request at the code the caller asked under,
     * then push the route — `startActivityForResult` + `onNewIntent` + `navigateToRoute`, minus the
     * platform.
     */
    private fun launchForResult(kind: ReadingResultKind, route: String, requestCode: Int) {
        requests.record(kind, requestCode)
        compose.runOnIdle { navController.navigate(route) }
        compose.waitForIdle()
    }

    private fun <T> deliver(channel: NavResultChannel<T>, result: T) {
        compose.runOnUiThread { channel.deliver(navController, result) }
        compose.waitForIdle()
    }

    // ——— the five channels, one flow each ————————————————————————————————————————————————————————

    /**
     * **Assign labels** (`BibleView.kt`'s `assignLabels`) — the worst of the set, because it is a
     * common gesture: long-press a verse, pick labels, confirm, and pre-fix
     * `bookmarkControl.addOrUpdateBookmark` never ran.
     *
     * The other four `manageLabels` entry points (Hide labels, workspace auto-assign, the StudyPads
     * menu row, `CurrentGeneralBookPage`'s StudyPad arm) are the SAME channel and the same request
     * code family; what distinguishes them is only which applier the `awaitIntent` continuation
     * resumes into, which `ReadingResultRequestTest` pins per call site.
     */
    @Test
    fun anAssignLabelsAnswerReachesTheCallerThatAskedForIt() {
        setGraph()
        launchForResult(ReadingResultKind.ManageLabels, NavRoutes.manageLabels(LABEL_PAYLOAD), ASYNC_CODE)
        assertEquals(NavRoutes.MANAGE_LABELS_PATTERN, currentRoute, "the label manager must be on top")

        deliver(manageLabelsResults, ManageLabelsResult(LABEL_ANSWER))

        assertEquals(NavRoutes.READING, currentRoute, "the answer pops back to the reading view")
        assertEquals(listOf(ReadingResultKind.ManageLabels to ASYNC_CODE), applied.toList())
        assertEquals(listOf<Any>(ManageLabelsResult(LABEL_ANSWER)), payloads.toList())
        assertNull(manageLabelsResults.pending.value, "a consumed answer must be cleared")
        assertEquals(0, channelExits, "an in-graph answer must not take the host's exit branch")
        assertEquals(emptyList<String>(), logged.toList())
    }

    /** **The my-document page chooser** (`CurrentGeneralBookPage`'s my-document arm). */
    @Test
    fun aMyDocumentPageAnswerReachesTheCallerThatAskedForIt() {
        setGraph()
        launchForResult(
            ReadingResultKind.MyDocumentPages,
            NavRoutes.myDocumentPages("doc-1", "MyDoc", "My document"),
            ASYNC_CODE,
        )
        deliver(myDocumentPagesResults, MyDocumentPagesResult.Selected("MyDoc", "page-3"))

        assertEquals(listOf(ReadingResultKind.MyDocumentPages to ASYNC_CODE), applied.toList())
        assertEquals(listOf<Any>(MyDocumentPagesResult.Selected("MyDoc", "page-3")), payloads.toList())
        assertEquals(0, channelExits)
    }

    /**
     * **Reading progress** — the menu row and `BibleJavascriptInterface.openReadingProgress`, both
     * at `STD_REQUEST_CODE`. This is the flow whose pending slot had no consumer ANYWHERE in the
     * tree: classic handled both its shapes in `MainBibleActivity.onActivityResult`.
     */
    @Test
    fun aReadingProgressAnswerReachesTheCallerThatAskedForIt() {
        setGraph()
        launchForResult(ReadingResultKind.ReadingProgress, NavRoutes.readingProgress(), STD_CODE)
        deliver(readingProgressResults, ReadingProgressResult.Chapter("Gen", 3))

        assertEquals(listOf(ReadingResultKind.ReadingProgress to STD_CODE), applied.toList())
        assertEquals(listOf<Any>(ReadingProgressResult.Chapter("Gen", 3)), payloads.toList())
        assertEquals(0, channelExits)
    }

    /**
     * **The bookmark list** (`MenuCommandHandler`'s bookmarks row) — the fourth channel, which the
     * review's three-channel inventory did not list. `BookmarkNavGraph`'s own `onUp` comment names
     * this exact hazard in advance: "`deps.bookmarkResults` has no `pending` consumer anywhere … so
     * if bookmarks were ever pushed onto a non-empty back stack … the selection would be silently
     * dropped. Whoever adds the first in-graph edge into this destination must therefore also add
     * the consuming `LaunchedEffect` in the PARENT arm." T8b added that edge.
     */
    @Test
    fun aBookmarkAnswerReachesTheCallerThatAskedForIt() {
        setGraph()
        launchForResult(ReadingResultKind.Bookmarks, NavRoutes.bookmarks(), STD_CODE)
        val row = BookmarkResult(verse = "Gen.1.1", description = "row", labelNo = 0, listPosition = 2)
        deliver(bookmarkResults, row)

        assertEquals(listOf(ReadingResultKind.Bookmarks to STD_CODE), applied.toList())
        assertEquals(listOf<Any>(row), payloads.toList())
        assertEquals(0, channelExits)
    }

    /** **The my-documents list** (`MenuCommandHandler`'s my-documents row) — the fifth channel. */
    @Test
    fun aMyDocumentsAnswerReachesTheCallerThatAskedForIt() {
        setGraph()
        launchForResult(ReadingResultKind.MyDocuments, NavRoutes.myDocuments(), STD_CODE)
        deliver(myDocumentsResults, MyDocumentsResult.Selected("MyDoc", "page-1"))

        assertEquals(listOf(ReadingResultKind.MyDocuments to STD_CODE), applied.toList())
        assertEquals(0, channelExits)
    }

    // ——— the gate ————————————————————————————————————————————————————————————————————————————————

    /**
     * Once-only, and the re-entry is what makes it visible: the collector re-runs on every fresh
     * composition of this destination, so a consumer that did not clear BOTH the channel and the
     * request would apply the same answer again. Applying a label set twice is not idempotent — the
     * second application rewrites the workspace's recent-labels list over whatever the user has done
     * since.
     *
     * Mutations this catches: `consume()` -> `pending.value`, and `requests.claim` -> a read that
     * does not remove.
     */
    @Test
    fun anAnswerIsAppliedOnceEvenAfterTheDestinationIsReEntered() {
        setGraph()
        launchForResult(ReadingResultKind.ManageLabels, NavRoutes.manageLabels(LABEL_PAYLOAD), ASYNC_CODE)
        deliver(manageLabelsResults, ManageLabelsResult(LABEL_ANSWER))
        assertEquals(1, applied.size)

        // Leave and come back without arming anything: a fresh composition of the same arm.
        compose.runOnIdle { navController.navigate(NavRoutes.myDocuments()) }
        compose.waitForIdle()
        compose.runOnIdle { navController.popBackStack() }
        compose.waitForIdle()

        assertEquals(1, applied.size, "the answer was applied a second time")
    }

    /**
     * Ruling D: an answer that arrives with no request behind it is DROPPED LOUDLY, not applied and
     * not left lying in the channel.
     *
     * Leaving it would be the worse bug of the two: the next request that did arrive would find a
     * stale `pending` and spend somebody else's answer.
     */
    @Test
    fun anAnswerNobodyAskedForIsDroppedLoudlyRatherThanApplied() {
        setGraph()
        compose.runOnIdle { navController.navigate(NavRoutes.bookmarks()) }
        compose.waitForIdle()

        deliver(bookmarkResults, BookmarkResult(verse = "Gen.1.1", description = "x", labelNo = 0, listPosition = 0))

        assertEquals(emptyList<Pair<ReadingResultKind, Int>>(), applied.toList())
        assertNull(bookmarkResults.pending.value, "an unclaimed answer must still be cleared")
        assertEquals(1, logged.size, "the drop must be logged; Ruling D forbids a silent one")
        assertTrue(logged.single().contains("nothing asked for"), logged.single())
    }

    /**
     * The anti-vacuity half, and the mutation that proves every test above sees its subject: with no
     * collectors composed — which IS the pre-T8c tree, where the arm had no `results` at all — the
     * answer is published and never read.
     */
    @Test
    fun theArmIsWhatCollects() {
        setGraph(results = emptyList())
        launchForResult(ReadingResultKind.ManageLabels, NavRoutes.manageLabels(LABEL_PAYLOAD), ASYNC_CODE)
        deliver(manageLabelsResults, ManageLabelsResult(LABEL_ANSWER))

        assertEquals(NavRoutes.READING, currentRoute)
        assertEquals(emptyList<Pair<ReadingResultKind, Int>>(), applied.toList())
        assertEquals(
            ManageLabelsResult(LABEL_ANSWER),
            manageLabelsResults.pending.value,
            "this is the pre-fix behaviour the whole task exists to end: the user's answer sits in " +
                "the channel and nothing ever reads it",
        )
    }

    private companion object {
        const val LABEL_PAYLOAD = """{"mode":"ASSIGN"}"""
        const val LABEL_ANSWER = """{"mode":"ASSIGN","selectedLabels":["1"]}"""

        /** `ActivityBase.STD_REQUEST_CODE`. */
        const val STD_CODE = 1

        /** An `ActivityBase.awaitIntent` code (`ASYNC_REQUEST_CODE_START` + 0). */
        const val ASYNC_CODE = 1900
    }
}
