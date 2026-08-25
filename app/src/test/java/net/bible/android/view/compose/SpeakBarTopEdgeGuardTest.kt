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
 * Round 14b §6 is a THREE-way split inside one `Surface` call, and only two thirds of it are the
 * obvious part. `shape` and `shadowElevation` become conditional on `ownsTopEdge`; `tonalElevation
 * = 3.dp` must stay UNCONDITIONAL, because the tonal elevation is what makes the bar resolve to the
 * agent panel's exact colour. Drop it along with the corners and the shadow and the two abutting
 * surfaces stop being one continuous slab — which is the entire defect the round exists to fix, and
 * which would look in a golden like a colour bug rather than a lost parameter.
 *
 * Goldens catch the light-mode symptom; this catches the cause, and it is the only check that can
 * fail when someone "simplifies" the conditional by folding the tonal elevation into it.
 *
 * A source-text scan in the style of [SpeakEntryPointGuardTest] and
 * `net.bible.android.view.activity.settings.SettingsEditorSheetGuardTest`: paths are relative to
 * the `:app` module dir, this test's working directory.
 */
class SpeakBarTopEdgeGuardTest {
    private val barSource = "../sharedUi/src/commonMain/kotlin/net/bible/sharedui/reading/SpeakTransportBar.kt"

    @Test fun theScannedSourceExists() {
        assertTrue("$barSource is gone — this guard would pass vacuously", File(barSource).isFile)
    }

    private fun code(): String =
        File(barSource).readLines().filterNot { line ->
            val t = line.trimStart()
            t.startsWith("import ") || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
        }.joinToString("\n")

    @Test fun theTonalElevationIsNotConditionalOnOwnsTopEdge() {
        val src = code()
        assertEquals(
            "tonalElevation must appear exactly once, as an unconditional `3.dp` — it is what makes " +
                "the bar resolve to the agent panel's colour, so it must NOT be switched by ownsTopEdge",
            1, Regex("""tonalElevation\s*=\s*3\.dp""").findAll(src).count(),
        )
        assertEquals(
            "tonalElevation must not be given a conditional value",
            0, Regex("""tonalElevation\s*=\s*if\s*\(""").findAll(src).count(),
        )
    }

    @Test fun theShapeAndTheShadowAreConditionalOnOwnsTopEdge() {
        val src = code()
        assertEquals(
            "shape must be chosen by ownsTopEdge (rounded when it owns the edge, RectangleShape when not)",
            1, Regex("""shape\s*=\s*if\s*\(ownsTopEdge\)""").findAll(src).count(),
        )
        assertEquals(
            "shadowElevation must be chosen by ownsTopEdge (8.dp when it owns the edge, 0.dp when not)",
            1, Regex("""shadowElevation\s*=\s*if\s*\(ownsTopEdge\)""").findAll(src).count(),
        )
        assertTrue(
            "the squared-off branch must use RectangleShape, not RoundedCornerShape(0.dp)",
            src.contains("RectangleShape"),
        )
    }
}
