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
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.nav.NavResultIntents
import net.bible.android.view.activity.page.ActivityResultKind
import net.bible.sharedcore.nav.ReadingProgressResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
 * `@RunWith(RobolectricTestRunner::class)` -- not for the text-walk tests, which touch no Android
 * type, but for the two `forReadingProgress...` tests below: `android.content.Intent`'s
 * `putExtra`/`getStringExtra` are no-ops under the plain `isReturnDefaultValues` unit-test jar and
 * need Robolectric's shadow to actually round-trip a value (matching
 * `ReadingProgressServiceImplTest`'s reason for the same runner).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class NavResultChannelGuardTest {

    @Test
    fun noGraphArmExitsWithAResultExceptThroughTheChannel() {
        val offenders = mutableListOf<String>()
        for (file in navGraphSources()) {
            val text = withoutComments(file.readText())
            val path = file.path.replace('\\', '/')
            // A deps slot whose name says it carries a result out of the host, called anywhere
            // other than as `channel.deliver(...)`, is a second mechanism -- the thing this batch
            // exists to prevent.
            Regex("""\b(onFinishWithResult|exitWithResult|finishWith[A-Za-z]*Result)\s*\(""")
                .findAll(text)
                .forEach { offenders.add("$path: ${it.value}") }
        }
        assertEquals(
            emptyList<String>(),
            offenders.sorted(),
            "a graph arm exits with a result without going through NavResultChannel.deliver. " +
                "Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun theWalkActuallySeesSource() {
        val total = navGraphSources().sumOf { it.readText().length }
        assertTrue(total > 10_000, "the graph-source walk found almost nothing ($total chars)")
    }

    /**
     * Step 7: proves the WIRING rather than the channel branch (unit-tested on
     * `NavResultChannel` itself) -- that the reading-progress arm's `onResult` and the host's
     * [NavResultIntents.forReadingProgress] together still produce what
     * `NavHostComposeActivity.finishWithChapterResult` / `finishWithMemorizeResult` produced by
     * hand before this batch: the same [ActivityResultKind.EXTRA] tag and the same `"verse"` /
     * `"action"`/`"startOrdinal"`/`"endOrdinal"` extras.
     */
    @Test
    fun forReadingProgressChapterCarriesTheSameExtrasTheOldFunctionDid() {
        val intent = NavResultIntents.forReadingProgress(ReadingProgressResult.Chapter("GEN", 1))

        assertEquals(ActivityResultKind.ReadingProgress.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("Gen.1.1", intent.getStringExtra("verse"))
    }

    @Test
    fun forReadingProgressMemorizeCarriesTheSameExtrasTheOldFunctionDid() {
        val intent = NavResultIntents.forReadingProgress(ReadingProgressResult.Memorize(start = 3, end = 7))

        assertEquals(ActivityResultKind.ReadingProgress.name, intent.getStringExtra(ActivityResultKind.EXTRA))
        assertEquals("memorize", intent.getStringExtra("action"))
        assertEquals(3, intent.getIntExtra("startOrdinal", -1))
        assertEquals(7, intent.getIntExtra("endOrdinal", -1))
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
