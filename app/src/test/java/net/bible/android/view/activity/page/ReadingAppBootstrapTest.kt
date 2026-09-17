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
package net.bible.android.view.activity.page

import java.io.File
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reading-host re-typing R7 (design spec §3.4 and §6 item 1): the app bootstrap is extracted ONCE
 * and called by both Activities, and `WindowRepository` is constructed in exactly one place inside
 * it.
 *
 * **Why this counts construction SITES rather than asserting that a repository exists.** §2.3's
 * failure mode is silent. `WindowControl.kt`'s `windowRepository` getter falls back to
 * `WindowRepository(CoroutineScope(Dispatchers.Main))` — an *uninitialised* repository on a scope
 * nobody owns — instead of throwing, so "a repository exists" is true even when the bootstrap never
 * ran, and equally true when TWO of them ran and the second quietly replaced the first. The visible
 * symptom is not a crash: it is an empty workspace on a screen that looks fully loaded, which no
 * other test in the suite would notice.
 *
 * **Why a source scan and not a runtime counter.** The spec's wording ("the test must count
 * constructions") was written against a test-only counter incremented from `WindowRepository`'s
 * `init` behind a debug hook. That hook would be production code existing solely for a test, and
 * the brief's own Step 1 says to prefer the honest route if one exists. This is that route, and it
 * is strictly the stronger guard for the property R7 actually owes: the extraction's invariant is
 * "one creation site, in `ReadingAppBootstrap`", which a static count over `src/main/java` sees
 * directly — including a second site on a code path no unit test happens to execute, which a
 * runtime counter would miss. [theBootstrapPublishesAnInitialisedRepository] and
 * [theRepositoryTheBootstrapCreatedIsTheOneWindowControlPublishes] carry the runtime half: the
 * object `WindowControl` hands out after a real `onCreate` is the bootstrap's, and it is loaded.
 *
 * `WindowRepository(` appears all over `src/test` (every window fixture builds one by hand); this
 * scan is deliberately confined to `src/main/java`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingAppBootstrapTest {

    private val mainSrc = File("src/main/java")

    /** The only two places production code may construct a [net.bible.android.control.page.window.WindowRepository]. */
    private val allowedConstructionSites = setOf(
        "net/bible/android/control/page/window/WindowControl.kt",
        "net/bible/android/view/activity/page/ReadingAppBootstrap.kt",
    )

    /** Source lines with comment-only lines dropped, so a mention in a KDoc is not a call site. */
    private fun codeLinesOf(file: File): String =
        file.readLines()
            .filterNot { val t = it.trim(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
            .joinToString("\n")

    /** Every `src/main/java` file that CALLS the constructor (the declaration itself is not a call). */
    private fun constructionSites(): List<String> =
        mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                codeLinesOf(file)
                    .lineSequence()
                    .filterNot { it.contains("class WindowRepository(") }
                    .any { Regex("""(?<![\w.])WindowRepository\(""").containsMatchIn(it) }
            }
            .map { it.relativeTo(mainSrc).path.replace(File.separatorChar, '/') }
            .sorted()
            .toList()

    @Test
    fun theScanCanSeeItsSubject() {
        assertTrue("src/main/java not found — the scan below would pass vacuously", mainSrc.isDirectory)
        assertTrue(
            "no WindowRepository construction found anywhere in src/main/java — the regex has " +
                "drifted off its subject and every assertion below is vacuous",
            constructionSites().isNotEmpty(),
        )
    }

    @Test
    fun windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode() {
        assertEquals(
            "production code may construct a WindowRepository only in ReadingAppBootstrap (the one " +
                "app bootstrap both Activities call) and in WindowControl's documented lazy " +
                "fallback. A third site would not crash — WindowControl hands out an uninitialised " +
                "repository rather than throwing — it would give a silently empty workspace on a " +
                "screen that looks loaded",
            allowedConstructionSites.sorted(),
            constructionSites(),
        )
    }

    @Test
    fun theActivityItselfNoLongerConstructsOne() {
        assertTrue(
            "MainBibleActivity must get its repository from ReadingAppBootstrap, not build a " +
                "second one of its own",
            "net/bible/android/view/activity/page/MainBibleActivity.kt" !in constructionSites(),
        )
    }

    @Test
    fun theRepositoryTheBootstrapCreatedIsTheOneWindowControlPublishes() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertSame(
                "the Activity's repository must BE the bootstrap's",
                activity.readingAppBootstrap.windowRepository,
                activity.windowRepository,
            )
            assertSame(
                "and WindowControl must publish that same object, or every collaborator that reads " +
                    "windowControl.windowRepository is looking at a different workspace",
                activity.readingAppBootstrap.windowRepository,
                activity.windowControl.windowRepository,
            )
        } finally {
            controller.close()
        }
    }

    @Test
    fun theBootstrapPublishesAnInitialisedRepository() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertTrue(
                "the published repository must have been through initialize() — WindowControl's " +
                    "lazy fallback hands out one that has NOT, and it looks identical until the " +
                    "workspace turns out to be empty",
                activity.windowControl.windowRepository.initialized,
            )
        } finally {
            controller.close()
        }
    }
}
