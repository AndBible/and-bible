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
                // Any member access on the field OTHER than `.deliver(` is a second exit mechanism
                // beside the channel -- e.g. reading `.pending` directly instead of collecting it,
                // or calling `.consume()` from an arm rather than letting the channel decide.
                Regex("""\b${Regex.escape(field)}\.(\w+)""").findAll(text).forEach { m ->
                    val member = m.groupValues[1]
                    if (member != "deliver") {
                        offenders.add("$path: $field.$member")
                    }
                }
            }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "a NavResultChannel field is used some way other than `.deliver(...)` -- a second exit " +
                "path beside the channel. Offenders:\n${offenders.joinToString("\n")}",
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

    private fun withoutComments(source: String): String =
        source.lineSequence()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") || it.trimStart().startsWith("/*") }
            .joinToString("\n")
}
