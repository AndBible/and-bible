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
}
