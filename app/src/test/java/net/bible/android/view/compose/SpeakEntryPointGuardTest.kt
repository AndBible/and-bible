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

    /**
     * The Compose-path Speak entry points a `Screen.BibleSpeak` site may branch to.
     *
     * Round 14b §8 widened this from the single `showSpeakSettings(` round 13a required. The
     * main-menu Speak item now calls `showSpeakTransport()` instead: reaching the SETTINGS from a
     * menu row left the user with no visible way to start playback (the settings sheet has no play
     * control, and transport-bar visibility is a separate state that path never touched), which is
     * the reported defect. `MenuCommandHandler` still MENTIONS `Screen.BibleSpeak` in its classic
     * branch, so it is still scanned — it just satisfies the guard through the other call now.
     *
     * Both names are accepted rather than one being swapped for the other, because the two entry
     * points are both live and both correct: the toolbar's long-press and the transport bar's cog
     * still open the settings sheet.
     */
    private val composeSpeakEntryPoints = listOf("showSpeakSettings(", "showSpeakTransport(")

    private fun branchesToCompose(code: String): Boolean =
        composeSpeakEntryPoints.any { code.contains(it) }

    @Test fun everyBibleSpeakIntentSiteAlsoBranchesToTheSheet() {
        callSites.forEach { path ->
            val code = codeLinesOf(path)
            if (code.contains("Screen.BibleSpeak")) {
                assertTrue(
                    "$path launches Screen.BibleSpeak but calls neither showSpeakSettings() nor " +
                        "showSpeakTransport() — the Compose path would open the CLASSIC Speak activity",
                    branchesToCompose(code),
                )
            }
        }
    }

    /** At least one site must actually branch, or the `if` above could be satisfied by nothing. */
    @Test fun atLeastOneCallSiteBranchesToTheSheet() {
        assertTrue(
            "no call site reaches a Compose Speak entry point at all — the Compose Speak path is gone",
            callSites.any { branchesToCompose(codeLinesOf(it)) },
        )
    }

    /** Round 14b §8: the main-menu route specifically must show the TRANSPORT, not the settings. */
    @Test fun theMainMenuSpeakItemShowsTheTransportBar() {
        val code = codeLinesOf("src/main/java/net/bible/android/view/activity/page/MenuCommandHandler.kt")
        assertTrue(
            "MenuCommandHandler must call showSpeakTransport() — from a menu row the settings sheet " +
                "leaves the user with no way to start playback (round 14b §8)",
            code.contains("showSpeakTransport("),
        )
        assertFalse(
            "MenuCommandHandler must NOT also call showSpeakSettings() — spec D3: the menu row is " +
                "the transport, nothing else",
            code.contains("showSpeakSettings("),
        )
    }

    /**
     * Round 14b whole-branch review, minor: [composeSpeakEntryPoints] widened to accept EITHER name
     * at any call site, so on its own [everyBibleSpeakIntentSiteAlsoBranchesToTheSheet] would let a
     * retarget of the toolbar long-press / transport-bar cog from `showSpeakSettings(` to
     * `showSpeakTransport(` pass every gate here — even though spec §8's entry-point table says both
     * of those are SETTINGS routes and must stay so (only the main-menu row is the transport). This
     * is the symmetric assertion [theMainMenuSpeakItemShowsTheTransportBar] already makes for the
     * menu, applied to `MainBibleActivity.kt`: it must still call `showSpeakSettings(`, and must NOT
     * call `showSpeakTransport(` — that call belongs to `MenuCommandHandler` alone.
     */
    @Test fun mainBibleActivityStillShowsSettingsNotTheTransportBar() {
        val code = codeLinesOf("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt")
        assertTrue(
            "MainBibleActivity.kt must call showSpeakSettings() — the toolbar long-press and the " +
                "transport bar's cog are settings routes (spec §8) and must stay so",
            code.contains("showSpeakSettings("),
        )
        assertFalse(
            "MainBibleActivity.kt must NOT call showSpeakTransport() — that entry point belongs to " +
                "the main-menu row only (spec §8 / MenuCommandHandler)",
            code.contains("showSpeakTransport("),
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
            .filterNot { branchesToCompose(codeLinesOf(it.path)) }
            .map { it.path.replace('\\', '/') }
            .sorted()
        assertEquals(
            "these files launch Screen.BibleSpeak but reach no Compose Speak entry point — on the " +
                "Compose path they would open the CLASSIC Speak activity. Either add a " +
                "`host.showSpeakSettings()` / `host.showSpeakTransport()` branch, or, if the file " +
                "is classic-only, add it to excludedClassicLaunchers WITH the reason.",
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
