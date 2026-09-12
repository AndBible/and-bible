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

package net.bible.android.view.nav

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Replaces `NavHostRoutingGuardTest.noGraphNavigatesToTheReadingProgressRoute`, deleted in the same
 * change that made it obsolete -- as that test's own kdoc required ("delete this test as part of
 * that change -- not loosen it").
 *
 * That guard forbade in-graph navigation to the one destination that produced a result, because the
 * result was produced by `finish()`, which is only correct from a start destination. With
 * `NavResultChannel` a destination handles both entries, so the prohibition is gone and what needs
 * guarding is the opposite: that every result-producing arm goes THROUGH the channel, and no arm
 * quietly grows a second exit-with-result path beside it.
 *
 * **Fix round 1, Findings 1+2.** The original version of this class matched a regex
 * (`onFinishWithResult|exitWithResult|finishWith[A-Za-z]*Result`) against `:sharedUi` sources --
 * but none of those identifiers has EVER existed in `:sharedUi`; the two functions this guard was
 * meant to catch a regression of lived only in `:app` (`NavHostComposeActivity`), which this class
 * does not scan. That guard could never go red, so it was decorative, not a ratchet. Replaced with a
 * genuinely POSITIVE guard: [everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered] discovers
 * every `NavResultChannel<...>` field declared in `:sharedUi/commonMain` and asserts the ONLY member
 * access on it, anywhere in a `*NavGraph.kt` file, is `.deliver(`; the anti-vacuity half,
 * [readingProgressArmActuallyDeliversThroughTheChannel], asserts the wiring this batch actually
 * added is still present, so the first test cannot pass merely because nobody uses the field at all.
 *
 * **slice 2, Task 5 widened the allowed member set from `deliver` alone to
 * `deliver`/`pending`/`consume`**, and that is a correction rather than a loosening. The original
 * wording ("or calling `.consume()` from an arm rather than letting the channel decide") described
 * a producer reaching around its own channel -- but consuming `pending` is not the producer's move,
 * it is the PARENT's, and it is half of what [net.bible.sharedui.nav.NavResultChannel] exists for:
 * its kdoc says in so many words that a destination entered from inside the graph "publishes to
 * [pending] and pops. The parent arm consumes it once in a `LaunchedEffect`." The `ManageLabels`
 * arm is the first parent there has ever been, so the guard had never had to distinguish the two
 * uses. What is still forbidden is everything else -- most pointedly `publishForTest`, which would
 * be a way to fake a result into a graph, and any future member -- and the pairing is pinned
 * positively by [manageLabelsArmConsumesTheLabelEditChannel] below, so "allowed" does not become
 * "unused".
 *
 * Dependency-free on purpose (no `@RunWith(RobolectricTestRunner::class)`): every test here is a
 * plain text walk over `.kt` sources and touches no Android type. The two tests that DO need
 * Robolectric -- `NavResultIntents.forReadingProgress`'s `Intent` extras -- moved out to
 * `net.bible.android.view.activity.nav.NavResultIntentsTest` (fix round 1, Finding 3).
 */
class NavResultChannelGuardTest {

    @Test
    fun everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered() {
        val fieldDeclaration = Regex("""\bval\s+(\w+)\s*:\s*NavResultChannel<""")
        val files = navGraphSources().associateWith { withoutComments(it.readText()) }

        val fields = mutableSetOf<String>()
        for (text in files.values) {
            fieldDeclaration.findAll(text).forEach { fields += it.groupValues[1] }
        }
        assertTrue(
            fields.isNotEmpty(),
            "no `NavResultChannel<...>` field was found declared in any *NavGraph.kt -- this guard " +
                "would pass vacuously",
        )

        val offenders = mutableListOf<String>()
        for ((file, text) in files) {
            val path = file.path.replace('\\', '/')
            for (field in fields) {
                // Any member access on the field outside ALLOWED_MEMBERS is a second exit
                // mechanism beside the channel -- most pointedly `publishForTest`, the test seam,
                // which would fake a result into a live graph. `deliver` is the PRODUCER's move and
                // `pending`/`consume` are the PARENT's, and those three are the whole of the
                // channel's contract; anything else is reaching around it.
                Regex("""\b${Regex.escape(field)}\.(\w+)""").findAll(text).forEach { m ->
                    val member = m.groupValues[1]
                    if (member !in ALLOWED_MEMBERS) {
                        offenders.add("$path: $field.$member")
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "a NavResultChannel field is used some way outside its contract " +
                "($ALLOWED_MEMBERS) -- a second exit path beside the channel. " +
                "Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    /**
     * The anti-vacuity half: without this, the previous test would pass just as well if
     * `SettingsNavGraph.kt` stopped calling `.deliver(...)` on `readingProgressResults` entirely --
     * "used nowhere but `.deliver`" is trivially true of a field used nowhere. This asserts the real
     * wiring this batch added is actually THERE.
     */
    @Test
    fun readingProgressArmActuallyDeliversThroughTheChannel() {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "SettingsNavGraph.kt" }
        assertTrue(file != null, "cannot find SettingsNavGraph.kt among ${sources.map { it.path }}")
        val text = withoutComments(file.readText())
        assertTrue(
            text.contains("readingProgressResults.deliver("),
            "SettingsNavGraph.kt no longer calls readingProgressResults.deliver(...) -- the " +
                "reading-progress arm's result would silently stop reaching NavResultChannel",
        )
    }

    /**
     * The bookmark cluster's twin of [readingProgressArmActuallyDeliversThroughTheChannel], and it
     * matters more here: [net.bible.sharedui.bookmark.nav.BookmarkNavDeps] declares THREE channels
     * (see its kdoc for why they are created together) and — after slice 2, Task 5 — two of them
     * have destinations, so `bookmarkResults` is still legitimately used nowhere. That makes
     * [everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered] vacuous for it by design — these
     * tests pin the ones that are not, so a result cannot silently stop reaching its channel. The
     * `Bookmarks` arm brings its own line here.
     */
    @Test
    fun labelEditArmActuallyDeliversThroughTheChannel() {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "BookmarkNavGraph.kt" }
        assertTrue(file != null, "cannot find BookmarkNavGraph.kt among ${sources.map { it.path }}")
        val text = withoutComments(file.readText())
        assertTrue(
            text.contains("labelEditResults.deliver("),
            "BookmarkNavGraph.kt no longer calls labelEditResults.deliver(...) -- the label editor " +
                "would pop without ever handing its result back",
        )
    }

    /**
     * slice 2, Task 5's producer half. `ManageLabels` has two exits -- `saveAndExit` and the
     * HIDELABELS reset -- and both are the HOST's (they need Room and the workspace DAO), so what
     * the arm owns is the one line that hands the host's finished `ManageLabelsResult` to the
     * channel. Without it the label manager would leave with the user's whole session -- deletes,
     * renames, auto-assign changes -- committed to the database but never reported back to the
     * caller that asked for them.
     */
    @Test
    fun manageLabelsArmActuallyDeliversThroughTheChannel() {
        val text = bookmarkNavGraphSource()
        assertTrue(
            text.contains("manageLabelsResults.deliver("),
            "BookmarkNavGraph.kt no longer calls manageLabelsResults.deliver(...) -- the label " +
                "manager would leave without handing its result back",
        )
    }

    /**
     * slice 2, Task 5's CONSUMER half, and the reason [ALLOWED_MEMBERS] has three entries rather
     * than one.
     *
     * This is the first place the channel's in-graph branch actually runs: `ManageLabels` navigates
     * to `LabelEdit`, so a save there publishes to `pending` and pops instead of exiting the host.
     * If the `ManageLabels` arm did not collect and consume that, editing a label from inside the
     * graph would pop back with the user's edit silently dropped -- and nothing else in the suite
     * would notice, since every other path through `LabelEdit` takes the exit branch. Both halves
     * are asserted: the collection (so the result is seen) and the `consume()` (so a recomposition
     * cannot apply it twice).
     */
    @Test
    fun manageLabelsArmConsumesTheLabelEditChannel() {
        val text = bookmarkNavGraphSource()
        assertTrue(
            text.contains("labelEditResults.pending"),
            "BookmarkNavGraph.kt does not collect labelEditResults.pending -- a label edited from " +
                "inside the graph would pop with the user's changes dropped",
        )
        assertTrue(
            text.contains("labelEditResults.consume()"),
            "BookmarkNavGraph.kt does not clear labelEditResults with consume() -- a pending " +
                "result would be re-applied on every recomposition",
        )
    }

    @Test
    fun theWalkActuallySeesSource() {
        val total = navGraphSources().sumOf { it.readText().length }
        assertTrue(total > 10_000, "the graph-source walk found almost nothing ($total chars)")
    }

    private fun navGraphSources(): List<File> {
        val root = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui")
        assertTrue(root.isDirectory, "cannot find :sharedUi sources at ${root.absolutePath}")
        val files = root.walkTopDown().filter { it.isFile && it.name.endsWith("NavGraph.kt") }.toList()
        assertTrue(files.isNotEmpty(), "no *NavGraph.kt found under ${root.absolutePath}")
        return files
    }

    private fun bookmarkNavGraphSource(): String {
        val sources = navGraphSources()
        val file = sources.firstOrNull { it.name == "BookmarkNavGraph.kt" }
        assertTrue(file != null, "cannot find BookmarkNavGraph.kt among ${sources.map { it.path }}")
        return withoutComments(file.readText())
    }

    private fun withoutComments(source: String): String =
        source.lineSequence()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")

    private companion object {
        /**
         * The whole of [net.bible.sharedui.nav.NavResultChannel]'s public contract: `deliver` is
         * what a producing destination calls, `pending`/`consume` are what a PARENT destination
         * calls to pick up what a child published before it popped. Anything else -- the
         * `publishForTest` seam, or any member added later -- is a way around the channel and fails
         * [everyNavResultChannelFieldOnADepsClassIsOnlyEverDelivered].
         */
        val ALLOWED_MEMBERS = setOf("deliver", "pending", "consume")
    }
}
