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
 * Batch Z-late phase 1, slice S2: the classic reading-plan cluster was deleted and its three
 * `ScreenLauncher` arms collapsed to the Compose implementations. This guard makes the deletion
 * durable — a regression that re-adds a file, an import, a manifest entry or a flag branch would
 * otherwise only be noticed if something else broke.
 *
 * Source scan rather than a reflective "class not found", matching [ClassicSearchRemovalGuardTest]
 * and sharing its machinery via [ClassicRemovalScan]. Paths are relative to the `:app` module dir,
 * which is the working directory for its unit tests.
 *
 * Task 2 seeds this file with the Up-parent assertion alone; Task 3 adds the deletion, sweep,
 * routing and manifest assertions. The phase-wide guard spec §6 describes (iterate `Screen.entries`
 * and assert every arm resolves into the Compose set) cannot be true until the last slice lands, so
 * it is deliberately not attempted here.
 */
class ClassicReadingPlanRemovalGuardTest {
    /** Fully-qualified names of everything S2 deletes: three screens, two adapters, the actionbar subpackage. */
    private val doomedClassNames = listOf(
        "net.bible.android.view.activity.readingplan.DailyReading",
        "net.bible.android.view.activity.readingplan.DailyReadingList",
        "net.bible.android.view.activity.readingplan.ReadingPlanSelectorList",
        "net.bible.android.view.activity.readingplan.DailyReadingItemAdapter",
        "net.bible.android.view.activity.readingplan.ReadingPlanItemAdapter",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanActionBarManager",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanBibleActionBarButton",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanCommentaryActionBarButton",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanDictionaryActionBarButton",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanPauseActionBarButton",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanQuickDocumentChangeButton",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanStopActionBarButton",
        "net.bible.android.view.activity.readingplan.actionbar.ReadingPlanTitle",
    )

    private val doomedClassRefs = ClassicRemovalScan.refsFor(doomedClassNames)

    /**
     * S2: `parentActivityName` is a plain string that drives the system Up button through
     * `NavUtils`. Nothing compiles against it, so a surviving activity may keep pointing Up at a
     * deleted class with no compile error and no other failing test — which is exactly what the
     * two Compose reading-plan list screens did before this slice (both named classic
     * `DailyReading`). They now point at `DailyReadingComposeActivity`, which preserves today's
     * three-step chain (list -> one-day -> MainBibleActivity) rather than flattening it.
     *
     * Scoped to this slice's names on purpose: the phase-wide version of this assertion belongs to
     * the epilogue, once every classic screen is gone.
     */
    @Test fun noComposeActivityIsParentedToAClassicReadingPlanScreen() {
        val path = "src/main/AndroidManifest.xml"
        assertTrue("$path is missing — this guard would pass vacuously", File(path).isFile)
        val name = Regex("""android:name="([^"]+)"""")
        val parent = Regex("""android:parentActivityName="([^"]+)"""")
        val composeActivities = File(path).readText()
            .split("<activity")
            .drop(1)
            .mapNotNull { block ->
                val openingTag = block.substringBefore(">")
                val declared = name.find(openingTag)?.groupValues?.get(1) ?: return@mapNotNull null
                if (!declared.endsWith("ComposeActivity")) null
                else declared to parent.find(openingTag)?.groupValues?.get(1)
            }
        assertTrue(
            "$path declared ${composeActivities.size} Compose activities — the scan is not " +
                "seeing the manifest, so it would pass vacuously",
            composeActivities.size > 30,
        )
        assertEquals(
            "these SURVIVING Compose activities declare an Up parent that S2 deletes. " +
                "parentActivityName is a string, so nothing would fail to compile and no other " +
                "test would notice — the Up button would simply target a class that is gone.",
            emptyList<String>(),
            composeActivities
                .filter { (_, upParent) ->
                    upParent != null && doomedClassRefs.any { it.containsMatchIn(upParent) }
                }
                .map { (declared, upParent) -> "$declared -> $upParent" }
                .sorted(),
        )
    }

    private val doomedPaths = listOf(
        "src/main/java/net/bible/android/view/activity/readingplan/DailyReading.kt",
        "src/main/java/net/bible/android/view/activity/readingplan/DailyReadingList.kt",
        "src/main/java/net/bible/android/view/activity/readingplan/ReadingPlanSelectorList.kt",
        "src/main/java/net/bible/android/view/activity/readingplan/DailyReadingItemAdapter.kt",
        "src/main/java/net/bible/android/view/activity/readingplan/ReadingPlanItemAdapter.kt",
        "src/main/java/net/bible/android/view/activity/readingplan/actionbar",
        "src/main/res/layout/list.xml",
        "src/main/res/layout/two_line_list_item.xml",
        "src/main/res/layout/reading_plan_one_day.xml",
        "src/main/res/layout/reading_plan_one_reading.xml",
        "src/main/res/layout/reading_plan_title.xml",
        "src/main/res/menu/reading_plan.xml",
        "src/main/res/menu/reading_plan_list_context_menu.xml",
    )

    @Test fun theClassicReadingPlanFilesAndResourcesAreGone() {
        assertTrue(
            "cwd is not the :app module dir — this guard would pass vacuously",
            File("src/main").isDirectory,
        )
        val survivors = doomedPaths.filter { File(it).exists() }
        assertEquals(
            "these classic reading-plan files, resources or directories should have been deleted " +
                "in S2. The whole actionbar/ directory goes, not just ReadingPlanTitle: the " +
                "manager and all five buttons have no consumer except each other and classic " +
                "DailyReading. list.xml is S2's to delete — S1 left it because these two list " +
                "screens still inflated it (spec 8.1).",
            emptyList<String>(),
            survivors,
        )
    }

    /**
     * `ReadingPlanKeys.kt` is this slice's trap: it sits in the doomed directory, it is named
     * after the feature being deleted, and it holds `ReadingPlanKeys` (the PLAN/DAY intent-extra
     * keys) and `ReadingPlanCatalog` — both lifted out of `DailyReading`'s companion by the
     * prologue's P2 precisely so they could outlive it. `DailyReadingComposeActivity` and
     * `service/readingplan/ReadingPlanTextFileDao` read them. Asserting these files exist turns
     * "deleted too much" into a failure instead of a silence.
     */
    @Test fun theSurvivingReadingPlanCollaboratorsStillExist() {
        val expected = listOf(
            "src/main/java/net/bible/android/view/activity/readingplan/ReadingPlanKeys.kt",
            "src/main/java/net/bible/android/view/activity/readingplan/DailyReadingComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/readingplan/DailyReadingListComposeActivity.kt",
            "src/main/java/net/bible/android/view/activity/readingplan/ReadingPlanSelectorComposeActivity.kt",
            "src/main/res/layout/list_content_simple.xml",
        )
        val missing = expected.filterNot { File(it).isFile }
        assertEquals("S2 deleted a file it was supposed to keep", emptyList<String>(), missing)
    }

    /**
     * The reference proof of spec §3.3, expressed as a test so it survives this session. Walks
     * every shipping source set (`src/main`, `src/debug`, `src/standard`, `src/discrete`, …) rather
     * than a path list, so a new file naming a deleted class by its FULLY-QUALIFIED name from
     * OUTSIDE the deleted package cannot escape. That scoping matters: an unqualified same-package
     * reference (a bare `DailyReading` written inside `readingplan/`) is invisible to this sweep —
     * but that case does not need this test, because the compiler already catches it: the class it
     * would resolve to no longer exists.
     *
     * Matching on the FULLY-QUALIFIED name with a trailing (not leading — see [ClassicRemovalScan.refsFor])
     * non-identifier boundary is what makes this possible at all: every surviving Compose twin is
     * named after its classic original
     * (`DailyReading` / `DailyReadingComposeActivity`), and imports are KEPT because an import is
     * the reference being hunted — see [ClassicRemovalScan] for why both halves are load-bearing.
     */
    @Test fun noSourceFileNamesAClassicReadingPlanScreen() {
        val sources = ClassicRemovalScan.appSources()
        val offenders = sources
            .filter { file ->
                val code = ClassicRemovalScan.codeLinesOf(file.path, keepImports = true)
                doomedClassRefs.any { it.containsMatchIn(code) }
            }
            .map { it.path.replace('\\', '/') }
            .sorted()
        assertEquals(
            "these files still name a classic reading-plan class deleted in S2",
            emptyList<String>(),
            offenders,
        )
        assertTrue("the source-set walk found no Kotlin source at all", sources.size > 100)
    }

    /** No manifest may declare, or point at, a class this slice deletes. */
    @Test fun noManifestEntryNamesAClassicReadingPlanScreen() {
        assertTrue(
            "src/main/AndroidManifest.xml is missing — this guard would pass vacuously",
            File("src/main/AndroidManifest.xml").isFile,
        )
        val offenders = ClassicRemovalScan.manifestPaths
            .filter { File(it).isFile }
            .flatMap { path ->
                File(path).readLines()
                    .filter { line -> doomedClassRefs.any { it.containsMatchIn(line) } }
                    .map { "$path: ${it.trim()}" }
            }
            .sorted()
        assertEquals(
            "a manifest still names a class S2 deletes — either a leftover <activity> block or " +
                "a parentActivityName. Neither is a compile error and neither breaks another " +
                "test; the app would simply reference a class that is gone.",
            emptyList<String>(),
            offenders,
        )
    }

    /** The routing arms must be unconditional now: no `useComposeFor` branch may mention reading plan. */
    @Test fun screenLauncherDoesNotBranchForReadingPlan() {
        val path = "src/main/java/net/bible/android/view/ScreenLauncher.kt"
        assertTrue("$path is missing — this guard would pass vacuously", File(path).isFile)
        val code = ClassicRemovalScan.codeLinesOf(path)
        assertTrue(
            "$path no longer reads use_compose_ui at all — the flag must survive S2 for the " +
                "remaining slices (spec §3.4)",
            code.contains("useComposeFor"),
        )
        val readingPlanScreens = listOf(
            "Screen.ReadingPlanSelector", "Screen.DailyReadingList", "Screen.ReadingPlan",
        )
        // Each arm is `Screen.X -> XComposeActivity::class.java`. Take the text from the arm's
        // `Screen.X ->` up to the next `Screen.` and assert no branch survives inside it. The
        // literal " ->" in the search string is what keeps `Screen.ReadingPlan ->` from matching
        // the `Screen.ReadingPlanSelector ->` arm.
        val offenders = readingPlanScreens.filter { screen ->
            val start = code.indexOf("$screen ->")
            if (start < 0) return@filter true
            val next = code.indexOf("Screen.", start + screen.length + 3)
            val arm = if (next < 0) code.substring(start) else code.substring(start, next)
            arm.contains("useComposeFor") || arm.contains("else ")
        }
        assertEquals(
            "these reading-plan arms still branch on the flag (or are missing entirely) — S2 " +
                "collapses them to the Compose class unconditionally",
            emptyList<String>(),
            offenders,
        )
    }
}
