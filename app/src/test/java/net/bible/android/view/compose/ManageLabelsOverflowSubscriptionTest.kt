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

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.io.File
import net.bible.android.TEST_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The label manager's overflow dialogs (Help, Export StudyPads) are raised from a `RowScope` slot
 * inside the nav graph's top app bar but COMPOSED at host level, in `NavHostComposeActivity`'s
 * `setContent` body, outside the `NavHost`. Whether they can appear at all is therefore a question
 * about which scope is subscribed to which state — and the batch that made `ManageLabels` a CHILD of
 * `Bookmarks` broke that subscription without any test noticing.
 *
 * The mechanism, which is what these tests pin:
 *
 * - `manageLabelsSession` is a PLAIN FIELD, not snapshot state. Writing it invalidates nothing.
 * - `manageLabelsHelpOpen` / `manageLabelsExportOpen` ARE snapshot state, but a composable scope
 *   subscribes to a state object only when it actually READS it during composition.
 * - The host's `setContent` body composes ONCE per Activity; `NavHost`'s internal recompositions do
 *   not invalidate it.
 *
 * So `session?.let { if (helpOpen) { ... } }` is a subscription that exists only if the session
 * happens to be non-null on that single pass. Launched on `ManageLabels` it is (the arm builds the
 * session in the same pass, before the block). Launched on `Bookmarks` and navigated from there — the
 * edge `MenuCommandHandler.kt:207` produces — it is NOT, and the overflow's Help and Export items
 * flip a boolean nobody observes.
 *
 * **These are probe tests, deliberately.** The real host injects some thirty services and would need
 * the whole app graph plus Room to compose here, so the two tests below reproduce the SHAPE in
 * miniature against a real `NavHost` and real controllers: [theFixedShapeShowsTheDialogWhenTheHostDidNotStartOnTheChild]
 * proves the fixed ordering subscribes, and [theNestedShapeIsTheBugAndStillFailsToShowTheDialog]
 * proves the broken ordering does not — which is what makes the first test non-vacuous. The third
 * test then holds the production file to the proved ordering, because that is the only part a source
 * scan CAN see. Stated plainly: no test here composes `NavHostComposeActivity` itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ManageLabelsOverflowSubscriptionTest {
    @get:Rule val compose = createComposeRule()

    /** The host's `manageLabelsSession`: a plain field, written by the child arm's composition. */
    private var session: String? = null

    /** The host's `manageLabelsHelpOpen`: snapshot state, flipped by the top bar's overflow. */
    private var helpOpen by mutableStateOf(false)

    private lateinit var navController: NavHostController

    private companion object {
        const val HOME = "home"
        const val CHILD = "child"
        const val DIALOG_TEXT = "help-dialog"
    }

    /**
     * The host body in miniature. [nestSessionOutside] is the whole variable under test: `true` is
     * the shape the batch shipped (`session?.let { if (helpOpen) ... }`), `false` the fixed one
     * (`if (helpOpen) { session?.let { ... } }`).
     */
    @Composable
    private fun Host(nestSessionOutside: Boolean) {
        navController = rememberNavController()
        NavHost(navController = navController, startDestination = HOME) {
            composable(HOME) { Text(HOME) }
            composable(CHILD) {
                // The production equivalent: `remember(data) { d.controllerFor(...) }`, whose host
                // factory memoises into the plain `manageLabelsSession` field during composition.
                remember(Unit) { session = "live" }
                Text(CHILD)
            }
        }
        if (nestSessionOutside) {
            session?.let { _ -> if (helpOpen) Text(DIALOG_TEXT) }
        } else {
            if (helpOpen) session?.let { _ -> Text(DIALOG_TEXT) }
        }
    }

    private fun launchOnHomeThenNavigateToChild(nestSessionOutside: Boolean) {
        compose.setContent { Host(nestSessionOutside = nestSessionOutside) }
        compose.waitForIdle()
        // The session is null on the one pass the host body composes — exactly the state of a host
        // launched with startRoute = NavRoutes.bookmarks().
        assertTrue("the probe started with a session already set", session == null)

        compose.runOnIdle { navController.navigate(CHILD) }
        compose.waitForIdle()
        assertEquals("live", session!!)

        // The overflow item: it can only set the boolean.
        compose.runOnIdle { helpOpen = true }
        compose.waitForIdle()
    }

    @Test
    fun theFixedShapeShowsTheDialogWhenTheHostDidNotStartOnTheChild() {
        launchOnHomeThenNavigateToChild(nestSessionOutside = false)
        compose.onNodeWithText(DIALOG_TEXT).assertIsDisplayed()
    }

    /**
     * The defect itself, kept as an executable statement of it. If this test ever starts FAILING —
     * i.e. the nested shape begins to work — then Compose's subscription rules or the host body's
     * recomposition behaviour changed, and the fix above is no longer load-bearing; find out why
     * before deleting anything.
     */
    @Test
    fun theNestedShapeIsTheBugAndStillFailsToShowTheDialog() {
        launchOnHomeThenNavigateToChild(nestSessionOutside = true)
        assertEquals(
            "the nested shape rendered the dialog — the subscription bug this guards is gone",
            0,
            compose.onAllNodesWithText(DIALOG_TEXT).fetchSemanticsNodes().size,
        )
    }

    /**
     * The production file, held to the ordering the two probes proved. A source scan cannot see a
     * missing recomposition subscription — that is what the probes are for — but it CAN see the one
     * textual property the fix consists of: neither boolean's `if (` may sit inside a
     * `manageLabelsSession` `?.let` block.
     *
     * If the host's session field is ever made snapshot state instead (the other valid fix), this
     * test stops describing the code and must be deleted as part of THAT change, not loosened.
     */
    @Test
    fun theHostReadsBothOverflowBooleansOutsideTheSessionLet() {
        val path = "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"
        val text = File(path).readText()
        assertTrue("cannot find $path", text.isNotEmpty())

        val letMarker = "manageLabelsSession?.second?.let {"
        val letBlocks = mutableListOf<IntRange>()
        var from = 0
        while (true) {
            val at = text.indexOf(letMarker, from)
            if (at < 0) break
            val openBrace = text.indexOf('{', at)
            val close = matchingBraceIndex(text, openBrace)
                ?: error("$path: unbalanced braces after the session let at offset $at")
            letBlocks.add(openBrace..close)
            from = close + 1
        }

        val offenders = mutableListOf<String>()
        var found = 0
        for (name in listOf("manageLabelsHelpOpen", "manageLabelsExportOpen")) {
            val condition = "if ($name"
            var searchFrom = 0
            var seen = 0
            while (true) {
                val at = text.indexOf(condition, searchFrom)
                if (at < 0) break
                searchFrom = at + condition.length
                seen++
                found++
                if (letBlocks.any { at in it }) {
                    offenders.add("$name's `if (` at offset $at is INSIDE a manageLabelsSession let block")
                }
            }
            assertTrue(
                "$path: no `if ($name` found at all — this guard would pass vacuously",
                seen > 0,
            )
        }
        assertTrue("the scan read nothing", found >= 2)
        assertEquals(
            "the host gates an overflow boolean behind the plain `manageLabelsSession` field. That " +
                "field is not snapshot state, so when the host starts on `bookmarks` the boolean is " +
                "never read on the one pass this body composes and the dialog can never open. Read " +
                "the state FIRST, look the session up inside. Offenders:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    /** Index of the `}` matching `text[openBraceIndex]`, or null if it never balances. */
    private fun matchingBraceIndex(text: String, openBraceIndex: Int): Int? {
        if (text.getOrNull(openBraceIndex) != '{') return null
        var depth = 0
        for (i in openBraceIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }
}
