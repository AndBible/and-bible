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

    /**
     * Round 13a whole-branch review, F9: [callSites] names three files, so a FOURTH Compose-path
     * launcher — a new file, or an old one that grows a Speak entry point — would be unpoliced by
     * every test above. This walks all of `src/main` instead of a list, so no new file can escape it.
     *
     * Two files are excluded by name, and both are CLASSIC-path code that legitimately has no
     * `showSpeakSettings()` branch:
     *  - `ScreenLauncher.kt` — the classic router itself; `Screen.BibleSpeak` is the mapping it
     *    exists to declare.
     *  - `SpeakTransportWidget.kt` — the classic transport widget, explicitly untouched by round 13a
     *    (spec §5). `ComposeReadingViewHost` hides it (`binding.speakTransport.visibility = GONE`)
     *    and renders `SpeakTransportBar` instead, whose `onConfig` DOES go to the sheet — so this
     *    widget's config button is unreachable on the Compose path.
     * [excludedClassicLaunchers] is asserted to exist for the same anti-vacuity reason as
     * [everyScannedCallSiteExists]: a renamed exclusion must resurface as a failure, not a silence.
     */
    private val excludedClassicLaunchers = listOf(
        "src/main/java/net/bible/android/view/ScreenLauncher.kt",
        "src/main/java/net/bible/android/view/util/widget/SpeakTransportWidget.kt",
    )

    @Test fun everyExcludedClassicLauncherStillExists() {
        val missing = excludedClassicLaunchers.filterNot { File(it).isFile }
        assertEquals("excluded paths that no longer exist (the exclusion is now a blind spot)", emptyList<String>(), missing)
    }

    @Test fun noUnscannedSourceFileLaunchesBibleSpeakWithoutBranchingToTheSheet() {
        val excludedNames = excludedClassicLaunchers.map { File(it).name }.toSet()
        val candidates = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in excludedNames }
            .filter { codeLinesOf(it.path).contains("Screen.BibleSpeak") }
            .toList()
        val offenders = candidates
            .filterNot { codeLinesOf(it.path).contains("showSpeakSettings(") }
            .map { it.path.replace('\\', '/') }
            .sorted()
        assertEquals(
            "these files launch Screen.BibleSpeak but never call showSpeakSettings() — on the " +
                "Compose path they would open the CLASSIC Speak activity. Either add the " +
                "`host.showSpeakSettings()` branch, or, if the file is classic-only, add it to " +
                "excludedClassicLaunchers WITH the reason.",
            emptyList<String>(),
            offenders,
        )
        // Anti-vacuity: the walk must actually be finding the known Compose-path launchers. If this
        // ever drops to zero the scan has stopped seeing source at all and proves nothing.
        assertTrue("the src/main walk found no Screen.BibleSpeak site at all", candidates.isNotEmpty())
    }

    /** Non-prose lines only: an `import` line or a comment mentioning either name must not count. */
    private fun codeLinesOf(path: String): String =
        File(path).readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("import ") || trimmed.startsWith("//") ||
                trimmed.startsWith("*") || trimmed.startsWith("/*")
        }.joinToString("\n")
}
