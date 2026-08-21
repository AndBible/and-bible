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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 13a T4: the two Compose Speak activities were deleted — the Compose Speak entry point
 * moves to a bottom sheet over the reading view (Task 13). This guard makes the deletion durable:
 * a regression that re-adds either file would otherwise only be noticed if something else broke.
 *
 * Deliberately a source scan (relative to the `:app` module dir, the working directory for its
 * unit tests) rather than a reflective "class not found", matching [MenuSeamGuardTest]'s pattern.
 */
class SpeakEntryPointGuardTest {
    @Test fun theComposeSpeakActivitiesAreGone() {
        listOf(
            "src/main/java/net/bible/android/view/activity/speak/BibleSpeakComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/speak/SpeakSettingsComposeActivity.kt",
        ).forEach { assertFalse("$it should have been deleted in round 13a", File(it).exists()) }
    }

    /**
     * Round 13a T13: `Screen.BibleSpeak` now resolves to the CLASSIC activity only (T4), so every
     * Compose-path call site must branch to [net.bible.android.view.activity.page.screen
     * .ComposeReadingViewHost.showSpeakSettings] instead. A forgotten branch would silently drop the
     * user into the classic Speak screen — a regression no golden and no unit test would notice, and
     * one nothing else here would fail on.
     *
     * Same source-scan shape (and the same two traps avoided) as [MenuSeamGuardTest]: prose lines
     * are filtered so an `import` or a comment cannot satisfy the guard, and the path list is
     * asserted to exist so the scan can never pass vacuously.
     */
    private val callSites = listOf(
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt",
        "src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
    )

    @Test fun everyScannedCallSiteExists() {
        val missing = callSites.filterNot { File(it).isFile }
        assertEquals("scanned paths that no longer exist (guard would pass vacuously)", emptyList<String>(), missing)
    }

    @Test fun everyBibleSpeakIntentSiteAlsoBranchesToTheSheet() {
        callSites.forEach { path ->
            val code = codeLinesOf(path)
            if (code.contains("Screen.BibleSpeak")) {
                assertTrue(
                    "$path launches Screen.BibleSpeak but never calls showSpeakSettings() — " +
                        "the Compose path would open the CLASSIC Speak activity",
                    code.contains("showSpeakSettings("),
                )
            }
        }
    }

    /** At least one site must actually branch, or the `if` above could be satisfied by nothing. */
    @Test fun atLeastOneCallSiteBranchesToTheSheet() {
        assertTrue(
            "no call site calls showSpeakSettings() at all — the Compose Speak entry point is gone",
            callSites.any { codeLinesOf(it).contains("showSpeakSettings(") },
        )
    }

    /** Non-prose lines only: an `import` line or a comment mentioning either name must not count. */
    private fun codeLinesOf(path: String): String =
        File(path).readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")
}
