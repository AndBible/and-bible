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

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compile-time parity between `ReadingHostDeps` and the Android wiring was lost when the 71-parameter,
 * default-less `ReadingViewContent` became `ReadingHostDeps`, which defaults most members to inert so
 * tests wire only what they exercise. A member added later and not passed by
 * `ComposeReadingViewHost.readingHostDeps()` would silently render nothing on Android; this guard is
 * what replaces the compiler's check. Every constructor parameter must appear as a named argument
 * there, or be allow-listed in [deliberatelyUnwired] with a reason.
 */
class ReadingHostDepsWiringGuardTest {
    private val depsFile = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/host/ReadingHost.kt")
    private val hostFile = net.bible.test.ProductionSources.resolve(
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
    )

    /** Parameter name -> why the Android host leaves it at the default. Empty: every member is wired. */
    private val deliberatelyUnwired = emptyMap<String, String>()

    /** The names declared by `class ReadingHostDeps(...)`: its 4-space-indented `val name:` lines. */
    private fun declaredParams(source: String): List<String> {
        val start = source.indexOf("class ReadingHostDeps(")
        assertTrue("ReadingHostDeps must be declared in ${depsFile.path}", start >= 0)
        val end = source.indexOf("\n)\n", start)
        return source.substring(start, end).lines()
            .mapNotNull { Regex("""^ {4}val (\w+):""").find(it)?.groupValues?.get(1) }
    }

    /** The names passed by `readingHostDeps()`: its 8-space-indented `name = ` lines. */
    private fun wiredArgs(source: String): Set<String> {
        val start = source.indexOf("private fun readingHostDeps() = ReadingHostDeps(")
        assertTrue("readingHostDeps() must exist in ${hostFile.path}", start >= 0)
        val end = source.indexOf("\n    )\n", start)
        return source.substring(start, end).lines()
            .mapNotNull { Regex("""^ {8}(\w+) = """).find(it)?.groupValues?.get(1) }
            .toSet()
    }

    @Test fun everyDepsMemberIsWiredByTheAndroidHostOrAllowListedWithAReason() {
        val declared = declaredParams(depsFile.readText())
        // Vacuity floor: a scan that finds almost nothing is a scan that broke, not a clean result.
        assertTrue("expected the full ReadingHostDeps parameter list, found ${declared.size}", declared.size > 40)
        val unwired = declared.filter { it !in wiredArgs(hostFile.readText()) && it !in deliberatelyUnwired }
        assertEquals("ReadingHostDeps members not passed by readingHostDeps(): $unwired", emptyList<String>(), unwired)
    }

    @Test fun theAllowListHoldsNoStaleOrReasonlessEntries() {
        val declared = declaredParams(depsFile.readText())
        deliberatelyUnwired.forEach { (name, reason) ->
            assertTrue("allow-listed '$name' is not a ReadingHostDeps member any more", name in declared)
            assertTrue("allow-listed '$name' needs a reason", reason.isNotBlank())
            assertTrue("allow-listed '$name' is wired after all; drop the entry", name !in wiredArgs(hostFile.readText()))
        }
    }
}
