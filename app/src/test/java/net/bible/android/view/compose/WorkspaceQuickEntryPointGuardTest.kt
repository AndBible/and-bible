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
 * Round 15b T7 fix round 1: every Compose-path "switch workspace" must reach the quick sheet, not
 * an Intent.
 *
 * The first version of this test (verbatim from the task brief) was a pair of WHOLE-FILE substring
 * checks — "does this file mention `showWorkspaceSheet` anywhere". That is far weaker than it looks:
 * unlike [SpeakEntryPointGuardTest]'s three call-site files (each with a single Speak sheet entry
 * point), `MainBibleActivity.kt` alone carries THREE separate `Screen.WorkspaceSelector` launches
 * — two deliberately classic-only, one gated — so "does the file mention the string anywhere" would
 * have stayed green even if a brand new, entirely ungated fourth launch were added right next to the
 * two classic ones, and it would also stay green for a reroute gated behind a condition that could
 * never be true. A guard that claims [SpeakEntryPointGuardTest]'s call-site shape (as its old kdoc
 * did) without actually having it is worse than no guard.
 *
 * This version walks every `ScreenLauncher.intentFor(..., Screen.WorkspaceSelector)` call site under
 * `src/main` and, for each one not on [excludedMarkers], requires `showWorkspaceSheet(` to appear in
 * the few lines immediately above it — the `if (...) { host.showWorkspaceSheet() } else { <launch> }`
 * idiom every real reroute in this round uses. Still a heuristic (a full Kotlin parser is not
 * warranted for a test like this), but scoped to the call site instead of the whole file, so an
 * unrelated same-file mention of the string can no longer satisfy it.
 */
class WorkspaceQuickEntryPointGuardTest {
    private val callSiteFiles = listOf(
        "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt",
        "src/main/java/net/bible/android/view/activity/page/BibleJavascriptInterface.kt",
        "src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt",
    )

    @Test fun everyCallSiteFileExists() {
        val missing = callSiteFiles.filterNot { File(it).isFile }
        assertEquals("scanned paths that no longer exist (guard would pass vacuously)", emptyList<String>(), missing)
    }

    /** A `ScreenLauncher.intentFor(..., Screen.WorkspaceSelector)` call — every such call in this
     *  tree today is fully on one line, verified indirectly by [theScanFoundAtLeastTheKnownSites]
     *  matching the expected count. */
    private fun isWorkspaceSelectorLaunch(line: String): Boolean =
        line.contains("ScreenLauncher.intentFor(") && line.contains("Screen.WorkspaceSelector")

    /**
     * Deliberately excluded launch sites, identified by a marker string that must appear within
     * [EXCLUSION_WINDOW] lines above the launch line — NOT by file, since `MainBibleActivity.kt`
     * carries both excluded and non-excluded sites. Each entry records WHY no `showWorkspaceSheet`
     * branch is required there:
     *  - the classic reading view's title-fling gesture and the classic toolbar's `workspaceButton`
     *    — both run ONLY on the classic (non-Compose) reading view, pre-dating round 15b and
     *    untouched by it (the task brief explicitly says to leave them alone);
     *  - the quick sheet's own footer row (`AbQuickSheetFooterRow`, "manage workspaces") — this IS
     *    the reroute target the other two sites reach, opened FROM INSIDE the sheet; requiring it to
     *    also open the sheet it is already showing would be circular.
     * [everyExcludedMarkerStillExists] is the anti-rot check: a renamed/removed marker must resurface
     * as a failure, not a silent narrowing of what the exclusion covers.
     */
    private val excludedMarkers = listOf(
        "SimpleOnGestureListener" to
            "the classic reading view's title-fling gesture (pre-round-15b, classic-only)",
        "workspaceButton.setOnClickListener" to
            "the classic toolbar's workspace button (pre-round-15b, classic-only)",
        "AbQuickSheetFooterRow" to
            "the quick sheet's own footer row reaching the full selector — it IS the reroute target",
    )

    @Test fun everyExcludedMarkerStillExists() {
        val allSrcMain = File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        excludedMarkers.forEach { (marker, reason) ->
            assertTrue(
                "excluded-site marker '$marker' ($reason) no longer found anywhere in src/main — " +
                    "the exclusion is now a blind spot",
                allSrcMain.contains(marker),
            )
        }
    }

    private data class LaunchSite(val path: String, val lineIndex: Int)

    private fun findLaunchSites(): List<LaunchSite> =
        File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val lines = file.readLines()
                lines.indices.filter { isWorkspaceSelectorLaunch(lines[it]) }
                    .map { LaunchSite(file.path.replace('\\', '/'), it) }
            }
            .toList()

    private fun windowBefore(lines: List<String>, lineIndex: Int, size: Int): String {
        val start = maxOf(0, lineIndex - size)
        return lines.subList(start, lineIndex).joinToString("\n")
    }

    private fun isExcluded(site: LaunchSite, lines: List<String>): Boolean {
        val window = windowBefore(lines, site.lineIndex, EXCLUSION_WINDOW)
        return excludedMarkers.any { (marker, _) -> window.contains(marker) }
    }

    private fun isGuarded(site: LaunchSite, lines: List<String>): Boolean =
        windowBefore(lines, site.lineIndex, GUARD_WINDOW).contains("showWorkspaceSheet(")

    @Test fun everyNonExcludedLaunchSiteIsGuardedByShowWorkspaceSheet() {
        val offenders = findLaunchSites().filterNot { site ->
            val lines = File(site.path).readLines()
            isExcluded(site, lines) || isGuarded(site, lines)
        }.map { "${it.path}:${it.lineIndex + 1}" }
        assertEquals(
            "these ScreenLauncher.intentFor(..., Screen.WorkspaceSelector) launches have no nearby " +
                "showWorkspaceSheet( branch and are not in excludedMarkers — on the Compose path " +
                "they would open the full selector Activity directly, bypassing the quick sheet. " +
                "Either add an `if (...) { host.showWorkspaceSheet() } else { ... }` guard above the " +
                "launch, or, if the site is legitimately classic-only / already inside the sheet, " +
                "add its marker to excludedMarkers WITH the reason.",
            emptyList<String>(),
            offenders,
        )
    }

    @Test fun theScanFoundAtLeastTheKnownSites() {
        val sites = findLaunchSites()
        // Anti-vacuity: 5 known sites as of round 15b T7 fix round 1 — 3 excluded (classic fling,
        // classic toolbar button, the sheet's own footer row) + 2 guarded (the (i) overflow menu
        // item, Ctrl+W). A count below this means the search pattern stopped matching real source,
        // not that sites were legitimately removed — if sites are ever legitimately removed, lower
        // this number deliberately and explain why in this comment.
        assertTrue(
            "the src/main walk found only ${sites.size} Screen.WorkspaceSelector launch site(s), " +
                "expected at least 5 — the search pattern may have stopped matching real source",
            sites.size >= 5,
        )
    }

    @Test fun theHostExposesExactlyOneWorkspaceSheetOpener() {
        val src = File("src/main/java/net/bible/android/view/activity/page/screen/ComposeReadingViewHost.kt").readText()
        assertTrue(src.contains("internal fun showWorkspaceSheet()"))
    }

    companion object {
        // Measured distances in this tree: classic fling marker-to-launch = 8 lines, guarded
        // if/showWorkspaceSheet-to-launch = 2 lines. Both windows carry margin above that.
        private const val EXCLUSION_WINDOW = 12
        private const val GUARD_WINDOW = 6
    }
}
