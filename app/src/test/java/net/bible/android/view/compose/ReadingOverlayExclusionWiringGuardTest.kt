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
 * Round 14a/14b merge: `ReadingOverlayExclusion` is TOTAL mutual exclusion, but round 14a could only
 * wire the `Llm` direction — the other two application sites live in `showSpeakSettings()` and
 * `showTextSettingEditor()`, inside the edit region the sibling container (`compose-14a-2`) owned,
 * and manufacturing a hunk there is the one thing the two-container fork existed to avoid. Both
 * rounds' status entries name this merge as the place the remaining two directions get applied.
 *
 * `ReadingOverlayExclusionTest` in `:sharedCore` pins the RULE; nothing pinned its APPLICATION. The
 * host closes over live Koin controllers inside `install()`, so there is no way to construct it in a
 * JVM unit test — hence a source scan, the same shape (and the same anti-vacuity guards) as
 * [SpeakEntryPointGuardTest].
 *
 * The overlay list is PARSED from the enum rather than hardcoded, so a fourth modal overlay added
 * later fails here the moment it exists, instead of quietly becoming the first one nobody arbitrates.
 */
class ReadingOverlayExclusionWiringGuardTest {
    private val enumSource =
        File("../sharedCore/src/commonMain/kotlin/net/bible/sharedcore/reading/ReadingOverlayExclusion.kt")
    private val hostSource =
        File("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt")

    @Test fun bothScannedSourcesExist() {
        listOf(enumSource, hostSource).forEach {
            assertTrue("${it.path} no longer exists — this guard would pass vacuously", it.isFile)
        }
    }

    /** The `ReadingOverlay` entries, read off the enum body itself. */
    private fun overlayNames(): List<String> {
        val body = enumSource.readText()
            .substringAfter("enum class ReadingOverlay {")
            .substringBefore("\n}")
        return body.lineSequence()
            .map { it.trim().removeSuffix(",") }
            .filter { it.isNotEmpty() && it.first().isUpperCase() && it.all { c -> c.isLetterOrDigit() } }
            .toList()
    }

    @Test fun theEnumParseFindsTheKnownOverlays() {
        val names = overlayNames()
        assertTrue(
            "the enum parse found $names — it must at least see the three overlays round 14a modelled",
            names.containsAll(listOf("Llm", "SpeakSheet", "TextSettingsEditor")),
        )
    }

    /**
     * Every overlay must have an application site in the host: opening it closes the others. A
     * missing one is the completeness gap the 14a status entry recorded, and it is invisible at
     * runtime today only because a modal scrim happens to cover every route to a second sheet.
     */
    @Test fun everyOverlayHasAnExclusionApplicationSiteInTheHost() {
        val code = codeLinesOf(hostSource)
        val unwired = overlayNames()
            .filterNot { code.contains("ReadingOverlayExclusion.closedBy(ReadingOverlay.$it)") }
        assertEquals(
            "these reading-view modal overlays open without closing the others — add a " +
                "`ReadingOverlayExclusion.closedBy(ReadingOverlay.<name>)` application at the site " +
                "that opens each. The rule is total mutual exclusion (see the enum's kdoc).",
            emptyList<String>(),
            unwired,
        )
    }

    /** Non-prose lines only: a comment describing the wiring must not satisfy the guard. */
    private fun codeLinesOf(file: File): String =
        file.readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")
}
