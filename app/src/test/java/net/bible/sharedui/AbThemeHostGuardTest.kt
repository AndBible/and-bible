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
package net.bible.sharedui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `AbTheme` must be called from exactly two places — [AbAppTheme] (`:app`, adds the Android locals) and
 * `AbHostTheme` (`:sharedUi`, the common wrapper [AbAppTheme] delegates to) — so that the app-global
 * theme inputs (night mode, colour mode, animations, the workspace-colour seed) are read in ONE
 * place. Before A/B batch 4b those four lines were copy-pasted into 47 hosts, and a new host that
 * forgot one got a silently wrong theme.
 *
 * Walks the whole of `src/main/java` rather than a hardcoded file list: batch 4a's F1 shipped with
 * a hand-enumerated list of call sites that named three of twelve, and the next new host is exactly
 * what a fixed list would miss.
 *
 * No exemptions: `ComposeReadingViewHost.kt` was briefly exempted (Task 3) because its `darkTheme`
 * comes from a live `nightModeState`, not the static `ScreenSettings.nightMode` read — but the fix
 * was to give [AbAppTheme] an optional `darkTheme` override (Task 3 fix round 1) rather than carve
 * out an exception here, so every host — including that one — goes through `AbAppTheme` and this
 * guard stays a strict, single, un-exempted rule.
 */
class AbThemeHostGuardTest {
    companion object {
        /** The only files that may call `AbTheme(`: the Android wrapper and the common one. */
        val ALLOWED = setOf("AbAppTheme.kt", "AbHostTheme.kt")

        /**
         * Relative paths (per root) of every `.kt` file under [roots] that calls `AbTheme(`, other than
         * [ALLOWED]. The `fun AbTheme(` declaration itself is not a call.
         */
        fun offenders(roots: List<File>): List<String> = roots.flatMap { root -> root.walkTopDown().map { root to it }.toList() }
            .asSequence()
            .filter { (_, f) -> f.isFile && f.extension == "kt" && f.name !in ALLOWED }
            .filter { (_, file) ->
                file.readLines()
                    .map { it.substringBefore("//") }
                    .any { it.contains("AbTheme(") && !it.contains("fun AbTheme(") }
            }
            .map { (root, f) -> f.relativeTo(root).path }
            .sorted()
            .toList()
    }

    @Test
    fun `AbTheme is called only from AbAppTheme and AbHostTheme`() {
        // The `:sharedUi` tree is not walked (it holds the `AbTheme` declaration, the iOS PoC host and
        // KDoc mentions); the common wrapper is pinned explicitly instead so its allowance is not vacuous.
        val common = File("../sharedUi/src/commonMain/kotlin/net/bible/sharedui/theme/AbHostTheme.kt")
        assertTrue("AbHostTheme.kt must exist and be the common AbTheme( call site", common.isFile && common.readText().contains("AbTheme("))
        val roots = net.bible.test.ProductionSources.roots
        assertEquals(
            "AbTheme( may only be called from AbAppTheme.kt and AbHostTheme.kt; use AbAppTheme { } in hosts",
            emptyList<String>(),
            offenders(roots),
        )
    }

    @Test
    fun `a planted third AbTheme call site is reported`() {
        val dir = Files.createTempDirectory("abtheme-guard").toFile()
        try {
            File(dir, "Rogue.kt").writeText("fun rogue() { AbTheme(darkTheme = true) { } }\n")
            File(dir, "AbHostTheme.kt").writeText("fun ok() { AbTheme() { } }\n")
            assertEquals(listOf("Rogue.kt"), offenders(listOf(dir)))
        } finally {
            dir.deleteRecursively()
        }
    }
}
