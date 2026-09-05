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
     * Batch Z-late epilogue, Task 1 (spec decision D3): `Screen.BibleSpeak` is gone from the enum,
     * because its last three launch sites were collapsed away. This guard's subject moves with it —
     * from "every file that LAUNCHES the screen must also branch to the sheet" to "the sheet entry
     * points exist, and these three files are the only ones that hold them".
     *
     * That is the same protection, stated over what survives: a Speak entry that stopped reaching
     * the sheet would silently leave the reading view on screen with nothing opened — a dead-end
     * no-op no golden and no unit test would notice — and a FOURTH file growing an entry point
     * would be unpoliced by every per-file test below.
     *
     * Scope, precisely: every test here scans per FILE, not per call site. `MainBibleActivity.kt`
     * holds TWO `showSpeakSettings(` sites (the toolbar long-press and the transport bar's cog), so
     * losing exactly one of them leaves the file still matching and passes every guard below. What
     * these tests do catch is a file losing its LAST entry point, a file gaining one, and either
     * file swapping settings for transport or the reverse.
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
     * The reading view's two Speak sheet entry points.
     *
     * Round 14b §8 widened this from the single `showSpeakSettings(` round 13a required. The
     * main-menu Speak item now calls `showSpeakTransport()` instead: reaching the SETTINGS from a
     * menu row left the user with no visible way to start playback (the settings sheet has no play
     * control, and transport-bar visibility is a separate state that path never touched), which is
     * the reported defect.
     *
     * Both names are accepted rather than one being swapped for the other, because the two entry
     * points are both live and both correct: the toolbar's long-press and the transport bar's cog
     * still open the settings sheet.
     */
    private val composeSpeakEntryPoints = listOf("showSpeakSettings(", "showSpeakTransport(")

    private fun branchesToCompose(code: String): Boolean =
        composeSpeakEntryPoints.any { code.contains(it) }

    /**
     * EVERY scanned file must reach a sheet entry point — not just one of them, which is all the
     * pre-epilogue form asserted once `Screen.BibleSpeak` stopped appearing in any of them.
     */
    @Test fun everyScannedCallSiteReachesASpeakSheetEntryPoint() {
        val silent = callSites.filterNot { branchesToCompose(codeLinesOf(it)) }.sorted()
        assertEquals(
            "these files hold a Speak entry and reach neither showSpeakSettings() nor " +
                "showSpeakTransport() — the reading view would stay on screen with no sheet opened, " +
                "a dead-end no-op",
            emptyList<String>(),
            silent,
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
     * at any call site, so on its own [everyScannedCallSiteReachesASpeakSheetEntryPoint] would let a
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
     * Round 13a whole-branch review, F9: [callSites] names three files, so a FOURTH Speak entry
     * point — a new file, or an old one that grows one — would be unpoliced by every test above.
     * This walks all of `src/main` instead of a list, so no new file can escape it.
     *
     * Batch Z-late epilogue, Task 1: with `Screen.BibleSpeak` gone there is no launch site left to
     * filter on, so the walk now asserts the exact SET of files holding a sheet entry point. That
     * is strictly stronger than the pre-epilogue form, and it carries its own anti-vacuity: an
     * empty walk cannot equal a three-element list.
     *
     * The two files that used to need excluding no longer do, because neither reaches an entry
     * point: `ScreenLauncher.kt` declared the retired `Screen.BibleSpeak` mapping, and
     * `SpeakTransportWidget.kt` (the classic widget, §2.4-protected, existence asserted by
     * [ClassicSpeakRemovalGuardTest]) had its own config-button route removed in S13, so its cog is
     * visible-but-inert until the epilogue decides the fate of `main_bible_view.xml:201`'s
     * `custom:showConfig="true"`.
     */
    @Test fun theSpeakSheetEntryPointsLiveInExactlyTheScannedFiles() {
        val holders = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { branchesToCompose(codeLinesOf(it.path)) }
            .map { it.path.replace('\\', '/') }
            .sorted()
            .toList()
        assertEquals(
            "the files holding a Speak sheet entry point are not the three scanned call sites. A " +
                "NEW one is an entry point no per-file test here polices — add it to callSites WITH " +
                "the reason; a MISSING one means a Speak entry stopped reaching the sheet.",
            callSites.sorted(),
            holders,
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
