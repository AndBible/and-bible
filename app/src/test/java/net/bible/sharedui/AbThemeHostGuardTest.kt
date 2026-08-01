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
import org.junit.Test
import java.io.File

/**
 * `AbTheme` must be called from exactly one place in `:app` — [AbAppTheme] — so that the app-global
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
    @Test
    fun `AbTheme is called only from AbAppTheme`() {
        val root = File("src/main/java")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "AbAppTheme.kt" }
            .filter { file ->
                file.readLines()
                    .map { it.substringBefore("//") }
                    .any { it.contains("AbTheme(") }
            }
            .map { it.relativeTo(root).path }
            .sorted()
            .toList()
        assertEquals(
            "AbTheme( may only be called from AbAppTheme.kt; use AbAppTheme { } in hosts",
            emptyList<String>(),
            offenders,
        )
    }
}
