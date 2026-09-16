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
 * R4 fix round 1: the window chrome exists TWICE — `MainBibleActivity.hideSystemUI`/`showSystemUI`
 * and the copy R4 ported into `NavHostComposeActivity` — and its entire value is that the two are
 * identical. Both hosts are live at once until slice 7 Task 13 deletes `MainBibleActivity`, so a
 * fullscreen or system-bar fix applied to one and not the other is a divergence no other test in
 * this repo can see: the chrome is a `WindowInsetsController` write with no observable return value,
 * and Robolectric supplies no controller at all.
 *
 * The copied comments are themselves the evidence that this code keeps being edited — they record an
 * A/B batch 3 review fix, round 12b §3 and a whole-branch review Important — so "ported verbatim"
 * needs to be a property of the branch, not a claim about one commit.
 *
 * **The one sanctioned difference** is the repository lookup: `MainBibleActivity` has its own
 * `windowRepository` field and the nav host reads `windowControl.windowRepository`. The guard
 * normalises exactly that, and pins the count, so the normalisation cannot grow into a blanket
 * rewrite that hides real drift.
 *
 * This is a SOURCE-level guard. It is not evidence about runtime behaviour; the device pass is.
 */
class ReadingChromePortDriftTest {
    private val classicPath = "src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt"
    private val navHostPath = "src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt"

    /** The chrome's first line in both files. */
    private val startAnchor = "    private fun hideSystemUI() {"

    /** The chrome's second function, whose closing brace ends the region. */
    private val tailAnchor = "    private fun showSystemUI(setNavBarColor: Boolean=true) {"

    private fun sourceOf(path: String): String {
        val f = File(path)
        // Anti-vacuity: a path that stops existing must FAIL the guard, not quietly contribute "".
        require(f.exists()) { "$path not found — ReadingChromePortDriftTest scans it" }
        return f.readText()
    }

    /**
     * The chrome region: from [startAnchor] through the closing brace of [tailAnchor]'s function,
     * found by brace matching rather than by a trailing-context anchor or a line number — R6, R7 and
     * Task 8 all still edit both files around it.
     *
     * The matcher strips `//` comments line by line and relies on this region having no block
     * comment and no brace inside a string literal; both hold today and the equality assertion
     * itself would break loudly if a future edit changed that, since the two copies are cut by the
     * same code.
     */
    private fun chromeRegionOf(path: String): String {
        val source = sourceOf(path)
        val start = source.indexOf(startAnchor)
        require(start >= 0) { "$path: the chrome's start anchor is gone — re-anchor this guard: $startAnchor" }
        val tail = source.indexOf(tailAnchor, start)
        require(tail >= 0) { "$path: the chrome's tail anchor is gone — re-anchor this guard: $tailAnchor" }

        var depth = 0
        var i = tail
        var end = -1
        while (i < source.length) {
            val lineEnd = source.indexOf('\n', i).let { if (it < 0) source.length else it }
            val line = source.substring(i, lineEnd)
            val code = line.substringBefore("//")
            for ((offset, c) in code.withIndex()) {
                if (c == '{') depth++
                if (c == '}') {
                    depth--
                    if (depth == 0) { end = i + offset + 1; break }
                }
            }
            if (end >= 0) break
            i = lineEnd + 1
        }
        require(end > start) { "$path: could not find the end of showSystemUI — re-anchor this guard" }
        return source.substring(start, end)
    }

    private val classicChrome get() = chromeRegionOf(classicPath)
    private val navHostChrome get() = chromeRegionOf(navHostPath)

    @Test
    fun bothChromeRegionsAreActuallyThereAndAreRealCode() {
        // Positive anti-vacuity: without this, a normalisation bug that reduced BOTH regions to ""
        // would satisfy the equality test below against nothing at all.
        for ((path, region) in listOf(classicPath to classicChrome, navHostPath to navHostChrome)) {
            assertTrue(
                "$path: the chrome region is too small to be the chrome (${region.lines().size} lines)",
                region.lines().size > 100,
            )
            assertTrue(
                "$path: the region does not look like the system-bar chrome",
                region.contains("WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE") &&
                    region.contains("APPEARANCE_LIGHT_NAVIGATION_BARS") &&
                    region.contains("isAppearanceLightNavigationBars"),
            )
        }
    }

    @Test
    fun theOnlyDifferenceBetweenTheTwoCopiesIsTheRepositoryLookup() {
        val classic = classicChrome
        val navHost = navHostChrome

        // Pin the sanctioned substitution's SHAPE, so the normalisation below cannot quietly become
        // a blanket rewrite that swallows real drift.
        assertEquals(
            "the nav host reads the repository through windowControl exactly twice (visibleWindows, " +
                "textDisplaySettings); anything else is drift, not the sanctioned adaptation",
            2, Regex("windowControl\\.windowRepository").findAll(navHost).count(),
        )
        assertEquals(
            "MainBibleActivity has its own windowRepository field and must not read it through windowControl",
            0, Regex("windowControl\\.windowRepository").findAll(classic).count(),
        )

        val normalised = navHost.replace("windowControl.windowRepository", "windowRepository")
        if (normalised != classic) {
            val a = classic.lines()
            val b = normalised.lines()
            val i = a.zip(b).indexOfFirst { (x, y) -> x != y }
            val detail = if (i >= 0) {
                "first difference at region line ${i + 1}:\n  MainBibleActivity: ${a[i]}\n  NavHostComposeActivity: ${b[i]}"
            } else {
                "the regions have different lengths: ${a.size} vs ${b.size} lines"
            }
            throw AssertionError(
                "the reading view's window chrome has drifted between its two hosts. Both are live " +
                    "until slice 7 Task 13 deletes MainBibleActivity, so a fix applied to one must be " +
                    "applied to the other — port the change across rather than weakening this guard.\n" +
                    detail,
            )
        }
    }
}
