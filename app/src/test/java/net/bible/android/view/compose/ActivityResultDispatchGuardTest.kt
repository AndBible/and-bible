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
import net.bible.android.view.activity.page.ActivityResultKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch Z-late phase 1, P1. Activity results used to be dispatched on the result Intent's component
 * class name, which named the CLASSIC Activity — so every Compose screen built a result Intent
 * naming its classic counterpart just to match, and deleting a classic class would have broken its
 * own Compose twin. This guard makes the class-name channel impossible to reintroduce.
 */
class ActivityResultDispatchGuardTest {
    private val sources = File("src/main/java").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    @Test fun theWalkActuallySeesSource() {
        // Anti-vacuity: both assertions below pass trivially on an empty list.
        assertTrue("the src/main/java walk found no Kotlin sources", sources.size > 100)
    }

    @Test fun noFileDispatchesOnAResultIntentsClassName() {
        val offenders = sources
            .filter { codeLinesOf(it).contains("component?.className") }
            .map { it.path.replace('\\', '/') }
            .sorted()
        assertEquals(
            "activity results must be dispatched on ActivityResultKind, not on the result Intent's " +
                "component class name — that channel coupled the reading view to the classic classes",
            emptyList<String>(),
            offenders,
        )
    }

    @Test fun everyKindIsProducedBySomeScreen() {
        // The declaring file plus every CONSUMER: their `ActivityResultKind.X ->` arms name a kind
        // without producing it, so counting them would make every kind look produced. Slice 8 F3
        // added the three that replaced `MainBibleActivity.kt`'s dispatcher -- without them this
        // guard stayed green with Bookmarks' only producer (`NavResultIntents.forBookmarks`) broken.
        // "MainBibleActivity.kt" is still listed only while the file exists (its arms would
        // otherwise count as production too); slice 8 F4 deletes the file and this entry.
        val declaringOrConsuming = setOf(
            "ActivityResultKind.kt",
            "NavHostComposeActivity.kt", "ReadingCommands.kt", "KeyChooserResults.kt",
            "MainBibleActivity.kt",
        )
        val producerText = sources
            .filterNot { it.name in declaringOrConsuming }
            .joinToString("\n") { codeLinesOf(it) }
        val unproduced = ActivityResultKind.entries
            .filterNot { producerText.contains("ActivityResultKind.${it.name}") }
            .map { it.name }
        assertEquals(
            "these kinds are dispatched but no screen ever sets them, so the branch is dead",
            emptyList<String>(),
            unproduced,
        )
    }

    /** Non-prose lines only, so an import or a comment can neither satisfy nor trip a guard. */
    private fun codeLinesOf(file: File): String =
        file.readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")
}
