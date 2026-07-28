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

package net.bible.android.view.compose.mockup

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.inspectionMode
import com.github.takahirom.roborazzi.size
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.progress.BookHeat
import net.bible.sharedcore.progress.CalendarHeatmap
import net.bible.sharedcore.progress.CalendarHeatmapLayout
import net.bible.sharedcore.progress.CalendarSkeleton
import net.bible.sharedcore.progress.ChapterDetail
import net.bible.sharedcore.progress.ChapterHeat
import net.bible.sharedcore.progress.DaySlot
import net.bible.sharedcore.progress.MonthLabel
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.progress.AbColorScaleLegend
import net.bible.sharedui.progress.BookHeatGrid
import net.bible.sharedui.progress.ChapterHeatGrid
import net.bible.sharedui.progress.bookProgressColor
import net.bible.sharedui.progress.calendarLevelColor
import net.bible.sharedui.progress.colorEmpty
import net.bible.sharedui.progress.countHeatColor
import net.bible.sharedui.theme.AbTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * NOT a golden test. Renders the reading-progress heat surfaces under three candidate palettes so
 * the maintainer can choose one on pixels (A/B batch 2 F6, spec §7). Output goes to
 * `app/build/mockups/` — deliberately outside `src/test/roborazzi/`, so these images are never
 * verified by `verifyRoborazziStandardGoogleplayDebug` and never gate a build. **Delete this class
 * once the palette decision is made** (it is throwaway evidence, not a permanent test).
 *
 * - A: today's colours (= classic's exactly, `ReadingProgressPalette.kt`).
 * - B: theme-aware neutrals — empty cells `surfaceVariant`, contrast from the theme; heat ramps as A.
 * - C: M3-derived ramps — reading from `primary`, memorization/calendar from `tertiary`, empties
 *   `surfaceVariant`.
 *
 * Fixture builders are copied from (not shared with) [net.bible.android.view.compose.golden.ReadingProgressGoldenTest] —
 * that class's visibility is not widened for this throwaway.
 *
 * Deviation from the mechanism sketch in the task brief: [net.bible.sharedui.progress.AbCalendarHeatmap]
 * has no injectable per-cell colour lambda — it calls
 * [net.bible.sharedui.progress.calendarLevelColor] internally, and this class must not edit
 * production code to add one. So the calendar row below is a small local reproduction of its cell
 * grid ([PaletteCalendarMockup], same [CalendarHeatmap] fixture, same level-to-colour mapping used
 * everywhere else in this file) rather than a call to the real component. It is not pixel-identical
 * to the production calendar (no month/day-of-week header labels), but is enough to compare the
 * three candidate ramps against each other and against the surrounding theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ProgressPaletteMockupTest {

    private enum class Palette { A, B, C }

    // --- Fixtures (copied from ReadingProgressGoldenTest, trimmed to what this mockup needs) ---

    private fun otBooks(): List<BookHeat> = listOf(
        BookHeat(bookId = "Gen", shortName = "Gen", isNT = false, readPercent = 1.0f, isComplete = true),
        BookHeat(bookId = "Exod", shortName = "Exod", isNT = false, readPercent = 0.6f, isComplete = false),
        BookHeat(bookId = "Lev", shortName = "Lev", isNT = false, readPercent = 0.1f, isComplete = false),
        BookHeat(bookId = "Num", shortName = "Num", isNT = false, readPercent = 0f, isComplete = false),
        BookHeat(bookId = "Deut", shortName = "Deut", isNT = false, readPercent = 0.25f, isComplete = false),
        BookHeat(bookId = "Josh", shortName = "Josh", isNT = false, readPercent = 0f, isComplete = false),
    )

    private fun ntBooks(): List<BookHeat> = listOf(
        BookHeat(bookId = "Matt", shortName = "Matt", isNT = true, readPercent = 0.4f, isComplete = false),
        BookHeat(bookId = "Mark", shortName = "Mark", isNT = true, readPercent = 0.8f, isComplete = false),
        BookHeat(bookId = "Luke", shortName = "Luke", isNT = true, readPercent = 0f, isComplete = false),
        BookHeat(bookId = "John", shortName = "John", isNT = true, readPercent = 0.15f, isComplete = false),
    )

    private fun chapterFixture(): ChapterDetail {
        val maxCount = 4
        val chapters = (1..12).map { chapter ->
            val count = when {
                chapter <= 2 -> 4
                chapter <= 5 -> 2
                chapter <= 8 -> 1
                else -> 0
            }
            ChapterHeat(chapter = chapter, count = count, level = ReadingProgressScale.heatLevel(count, maxCount))
        }
        return ChapterDetail(
            bookId = "Gen",
            title = "Genesis",
            chapters = chapters,
            maxCount = maxCount,
            countScaleSteps = ReadingProgressScale.countScaleSteps(maxCount),
        )
    }

    private fun calendarFixture(): CalendarHeatmap = CalendarHeatmapLayout.assemble(
        skeleton = CalendarSkeleton(
            slots = (0 until 8).flatMap { w -> (0 until 7).map { d -> DaySlot(w, d, (w * 7L + d) * 86_400_000L) } },
            monthLabels = listOf(MonthLabel(0, "Jan"), MonthLabel(4, "Feb")),
            weeks = 8,
            dayOfWeekLabels = listOf("", "M", "", "W", "", "F", ""),
        ),
        dailyCounts = (0 until 56).associate { i -> i * 86_400_000L to (i % 5) },
    )

    private val bookMax = 1.5f

    // --- Palette colour lambdas (per the task brief) ---

    /** Empty-cell colour per candidate: A = classic's #E8E8E8, B/C = the theme's surfaceVariant. */
    @Composable
    private fun emptyColor(p: Palette): Color = when (p) {
        Palette.A -> colorEmpty()
        Palette.B, Palette.C -> MaterialTheme.colorScheme.surfaceVariant
    }

    /** Book heat: A/B keep classic's blue->red ramp, C interpolates the theme's primary range. */
    @Composable
    private fun bookColor(p: Palette, readPercent: Float, maxPercent: Float): Color = when {
        readPercent <= 0f -> emptyColor(p)
        p == Palette.C -> lerp(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            readPercent.coerceIn(0f, 1f),
        )
        else -> bookProgressColor(readPercent, maxPercent)
    }

    /** Chapter count heat: A/B classic's yellow->orange->red, C the theme's tertiary range. */
    @Composable
    private fun chapterColor(p: Palette, count: Int, maxCount: Int): Color = when {
        count <= 0 -> emptyColor(p)
        p == Palette.C -> lerp(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            (count.toFloat() / maxCount.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
        else -> countHeatColor(count, maxCount)
    }

    /** Calendar/memorization levels 0..4: A/B classic's greens, C the theme's tertiary range. */
    @Composable
    private fun levelColor(p: Palette, level: Int): Color = when {
        level <= 0 -> emptyColor(p)
        p == Palette.C -> lerp(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            level / 4f,
        )
        else -> calendarLevelColor(level)
    }

    /**
     * Local stand-in for [net.bible.sharedui.progress.AbCalendarHeatmap]'s cell grid (see class
     * kdoc for why a call to the real component isn't used here): one column per week, one row per
     * day-of-week, cells coloured by [levelColor].
     */
    @Composable
    private fun PaletteCalendarMockup(heatmap: CalendarHeatmap, p: Palette) {
        val byWeek = heatmap.cells.groupBy { it.weekIndex }.toSortedMap()
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            byWeek.forEach { (_, weekCells) ->
                val byDay = weekCells.associateBy { it.dayIndex }
                Column {
                    for (day in 0 until 7) {
                        val level = byDay[day]?.level ?: 0
                        Box(
                            modifier = Modifier
                                .padding(1.dp)
                                .size(12.dp)
                                .background(levelColor(p, level), RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PaletteSample(p: Palette) {
        val chapters = chapterFixture()
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text("Palette ${p.name}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text("OT books", style = MaterialTheme.typography.labelSmall)
            BookHeatGrid(books = otBooks(), color = { bookColor(p, it.readPercent, bookMax) }, onClick = {})
            Spacer(Modifier.height(4.dp))
            Text("NT books", style = MaterialTheme.typography.labelSmall)
            BookHeatGrid(books = ntBooks(), color = { bookColor(p, it.readPercent, bookMax) }, onClick = {})
            Spacer(Modifier.height(4.dp))
            AbColorScaleLegend(
                label = "% Read",
                steps = ReadingProgressScale.buildBookPercentScaleSteps(bookMax),
                stepColor = { step -> bookColor(p, step / 100f, bookMax) },
                stepLabel = { step -> "$step%" },
            )
            Spacer(Modifier.height(8.dp))

            Text("Chapters (Genesis)", style = MaterialTheme.typography.labelSmall)
            ChapterHeatGrid(
                chapters = chapters.chapters,
                color = { chapterColor(p, it.count, chapters.maxCount) },
                onClick = {},
            )
            Spacer(Modifier.height(4.dp))
            AbColorScaleLegend(
                label = "Reads",
                steps = chapters.countScaleSteps,
                stepColor = { step -> chapterColor(p, step, chapters.maxCount) },
                stepLabel = { step -> "$step" },
            )
            Spacer(Modifier.height(8.dp))

            Text("Activity calendar", style = MaterialTheme.typography.labelSmall)
            PaletteCalendarMockup(calendarFixture(), p)
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(p: Palette, dark: Boolean) {
        captureRoboImage(
            "build/mockups/palette${p.name}_${if (dark) "dark" else "light"}.png",
            roborazziComposeOptions = RoborazziComposeOptions { inspectionMode(true); size(0, 900) },
        ) {
            ProvideAppLocals {
                AbTheme(darkTheme = dark, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    // Same background/content-colour wrapper as GoldenHarness.capture (Task 1) —
                    // duplicated on purpose, so this throwaway class can be deleted without
                    // touching the golden harness.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        CompositionLocalProvider(
                            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background),
                        ) {
                            PaletteSample(p)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun renderAllPalettes() {
        Palette.entries.forEach { p -> render(p, dark = false); render(p, dark = true) }
    }
}
