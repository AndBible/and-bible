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
 * it — from exactly one call site per host, at most once per host.
 *
 * **Why this counts construction SITES rather than asserting that a repository exists.** §2.3's
 * failure mode is silent. `WindowControl.kt`'s `windowRepository` getter falls back to
 * `WindowRepository(CoroutineScope(Dispatchers.Main))` — an *uninitialised* repository on a scope
 * nobody owns — instead of throwing, so "a repository exists" is true even when the bootstrap never
 * ran, and equally true when TWO of them ran and the second quietly replaced the first. The visible
 * symptom is not a crash: it is an empty workspace on a screen that looks fully loaded.
 *
 * Measured, not assumed: when a second `WindowRepository(lifecycleScope)` is put back into
 * `MainBibleActivity.onCreate`, [theBootstrapPublishesAnInitialisedRepository] and
 * [theRepositoryTheBootstrapCreatedIsTheOneWindowControlPublishes] BOTH still pass, because
 * `WindowRepository.activeWindow`'s getter is `if(!initialized) initialize()` and quietly loads
 * whatever object it is handed. Only the counts below go red. That is why they are the
 * load-bearing assertions.
 *
 * **Why a source scan and not a runtime counter.** The spec's wording ("the test must count
 * constructions") was written against a test-only counter incremented from `WindowRepository`'s
 * `init` behind a debug hook. That hook would be production code existing solely for a test. A
 * static count needs none, and sees a second site on a code path no unit test happens to execute.
 *
 * **Three counts, because each sees a failure the others cannot** (fix round 1, review items a/b):
 *  - [windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode] — one *construction* site
 *    outside `WindowControl`'s documented fallback, over EVERY non-test Kotlin source set (`main`
 *    and `debug`; `standard`/`discrete` carry no Kotlin), not just `src/main/java`.
 *  - [createWindowRepositoryIsCalledFromExactlyOneSitePerHost] — a construction-site count cannot
 *    see ONE site called from two places.
 *  - [callingItTwiceReturnsTheSameRepositoryRatherThanReplacingIt] — nor can it see one site
 *    EXECUTED twice; that one needs the runtime.
 *
 * `WindowRepository(` appears all over `src/test` (every window fixture builds one by hand); the
 * scans are deliberately confined to non-test source sets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingAppBootstrapTest {

    /**
     * Every non-test Kotlin source directory of `:app`. `src/main/java` alone would miss
     * `src/debug/java` (`DebugApp.kt`) and any future flavour source set — a construction there is
     * production code on at least one variant.
     */
    private val productionSrcRoots: List<File> =
        File("src").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "test" && it.name != "androidTest" }
            .map { File(it, "java") }
            .filter { it.isDirectory }
            .sortedBy { it.path }

    /** The only two places production code may construct a [net.bible.android.control.page.window.WindowRepository]. */
    private val allowedConstructionSites = setOf(
        "net/bible/android/control/page/window/WindowControl.kt",
        "net/bible/android/view/activity/page/ReadingAppBootstrap.kt",
    )

    private val mainBibleActivity =
        File("src/main/java/net/bible/android/view/activity/page/MainBibleActivity.kt")
    private val navHostActivity =
        File("src/main/java/net/bible/android/view/activity/nav/NavHostComposeActivity.kt")

    /** Source lines with comment-only lines dropped, so a mention in a KDoc is not a call site. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines()
            .filterNot { val t = it.trim(); t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }

    /** Every non-test file that CALLS the constructor (the declaration itself is not a call). */
    private fun constructionSites(): List<String> =
        productionSrcRoots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    codeLinesOf(file)
                        .filterNot { it.contains("class WindowRepository(") }
                        .any { Regex("""(?<![\w.])WindowRepository\(""").containsMatchIn(it) }
                }
                .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
        }.sorted()

    @Test
    fun theScansCanSeeTheirSubjects() {
        assertTrue(
            "no non-test Kotlin source root found — every scan below would pass vacuously",
            productionSrcRoots.isNotEmpty(),
        )
        assertTrue(
            "src/debug/java is a production source set on the debug variant and must be scanned " +
                "too; if it has genuinely gone away, say so here rather than silently narrowing",
            productionSrcRoots.any { it.path.replace(File.separatorChar, '/') == "src/debug/java" },
        )
        assertTrue("MainBibleActivity.kt not found", mainBibleActivity.isFile)
        assertTrue("NavHostComposeActivity.kt not found", navHostActivity.isFile)
        assertTrue(
            "no WindowRepository construction found anywhere in production sources — the regex has " +
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

    /**
     * Fix round 1, review item (b). One construction SITE called from two places constructs two
     * repositories, and [windowRepositoryIsConstructedInExactlyTwoPlacesInProductionCode] cannot
     * see it: the site count is still two.
     */
    @Test
    fun createWindowRepositoryIsCalledFromExactlyOneSitePerHost() {
        fun calls(file: File) =
            codeLinesOf(file).count { it.contains("readingAppBootstrap.createWindowRepository(") }
        assertEquals(
            "MainBibleActivity.onCreate must ask the bootstrap for its repository exactly once",
            1, calls(mainBibleActivity),
        )
        assertEquals(
            "NavHostComposeActivity must ask exactly once — from bootstrapIfNeeded(), which is the " +
                "one-shot both of its reading-route entry points share",
            1, calls(navHostActivity),
        )
    }

    /**
     * Fix round 1, review Important 3. `bootstrapIfNeeded()` must be reachable from BOTH
     * reading-route entry points: `onCreate` (the start route) and `onNewIntent` (a later
     * `EXTRA_ROUTE` navigating the live graph onto reading, on a host that started elsewhere and
     * therefore never bootstrapped — the composition would read `WindowControl`'s uninitialised
     * lazy fallback).
     */
    @Test
    fun theReadingBootstrapIsReachedFromBothRouteEntryPoints() {
        val lines = codeLinesOf(navHostActivity)
        val callLines = lines.withIndex().filter { (_, l) -> l.contains("bootstrapIfNeeded()") && !l.contains("fun ") }
        assertEquals(
            "exactly two call sites expected — onCreate's start-route gate and onNewIntent's; " +
                "found: " + callLines.map { it.value.trim() },
            2, callLines.size,
        )
        val decl = lines.indexOfFirst { it.contains("private fun bootstrapIfNeeded()") }
        assertTrue("bootstrapIfNeeded() is not declared in NavHostComposeActivity", decl >= 0)
        val onNewIntent = lines.indexOfFirst { it.contains("override fun onNewIntent(") }
        val onCreate = lines.indexOfFirst { it.contains("override fun onCreate(") }
        assertTrue("onNewIntent not found", onNewIntent >= 0)
        assertTrue("onCreate not found", onCreate >= 0)
        assertTrue(
            "one call must be in onNewIntent — a host that started on download or a settings route " +
                "reaches reading only through it",
            callLines.any { it.index > onNewIntent && it.index < onNewIntent + 40 },
        )
        assertTrue(
            "one call must be in onCreate, before setContent",
            callLines.any { it.index > onCreate && it.index < onCreate + 20 },
        )
    }

    /**
     * Fix round 1, review Important 3. Two entry points means the second one must be free. The
     * guard is the first statement, and the flag is set before anything else can throw.
     */
    @Test
    fun theReadingBootstrapIsAOneShot() {
        val body = codeLinesOf(navHostActivity)
            .dropWhile { !it.contains("private fun bootstrapIfNeeded()") }
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(
            "bootstrapIfNeeded()'s FIRST statement must be the early return — registerNetworkCallback " +
                "has no unregister, so a second run doubles a leak",
            "if (readingAppBootstrapped) return", body[0],
        )
        assertEquals(
            "…and the flag must be set immediately after it, before anything that can throw",
            "readingAppBootstrapped = true", body[1],
        )
    }

    /**
     * Fix round 1, review Important 1. `ReadingViewVisibility.setActivityVisible(true)` must run
     * BEFORE the deep-link dispatch, on this host as on classic. `openDeepLink` ->
     * `windowControl.showLink` -> `setKey(addHistoryItem = true)` posts `AddHistoryItem`
     * SYNCHRONOUSLY, and with the flag false `createHistoryItem` records a wrong `IntentHistoryItem`
     * carrying the deep-link intent instead of the verse. Deferring the flag to the reading
     * destination's `DisposableEffect` cannot work: an effect inside the graph runs after
     * `setContent`, i.e. after this has already fired.
     */
    @Test
    fun theReadingBootstrapSetsTheVisibilityFlagBeforeDispatchingADeepLink() {
        val body = codeLinesOf(navHostActivity)
            .dropWhile { !it.contains("private fun bootstrapIfNeeded()") }
            .takeWhile { !it.trim().startsWith("/** [ReadingHostActivity.hostContext]") }
        val visible = body.indexOfFirst { it.contains("ReadingViewVisibility.setActivityVisible(true)") }
        val deepLink = body.indexOfFirst { it.contains("readingAppBootstrap.openDeepLink(") }
        assertTrue(
            "bootstrapIfNeeded() must call ReadingViewVisibility.setActivityVisible(true) — without " +
                "it the deep link below records a wrong IntentHistoryItem, silently",
            visible >= 0,
        )
        assertTrue("bootstrapIfNeeded() must dispatch the openLink deep link", deepLink >= 0)
        assertTrue(
            "the visibility flag must be set BEFORE the deep-link dispatch, not after: the " +
                "AddHistoryItem the dispatch posts is handled synchronously",
            visible < deepLink,
        )
    }

    @Test
    fun theRepositoryTheBootstrapCreatedIsTheOneWindowControlPublishes() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            assertSame(
                "WindowControl must publish the object the bootstrap created, or every collaborator " +
                    "that reads windowControl.windowRepository is looking at a different workspace",
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

    /**
     * Fix round 1, review item (b): the failure shape no static count can see — one call site
     * EXECUTED twice. A second `createWindowRepository()` must hand back the repository this host
     * already has rather than replacing it, because a replacement does not crash: it strands every
     * collaborator holding the first one on a workspace `WindowControl` no longer publishes.
     */
    @Test
    fun callingItTwiceReturnsTheSameRepositoryRatherThanReplacingIt() {
        val controller = Robolectric.buildActivity(MainBibleActivity::class.java)
        try {
            val activity = controller.create().get()
            val first = activity.readingAppBootstrap.windowRepository
            val second = activity.readingAppBootstrap.createWindowRepository()
            assertSame("a second createWindowRepository() must not build another one", first, second)
            assertSame(
                "…and it must not have re-pointed WindowControl at a new object either",
                first,
                activity.windowControl.windowRepository,
            )
        } finally {
            controller.close()
        }
    }
}
