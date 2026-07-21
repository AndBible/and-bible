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

package net.bible.android.view.compose.golden

import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.progress.BookHeat
import net.bible.sharedcore.progress.CalendarHeatmapLayout
import net.bible.sharedcore.progress.CalendarSkeleton
import net.bible.sharedcore.progress.ChapterDetail
import net.bible.sharedcore.progress.ChapterHeat
import net.bible.sharedcore.progress.DaySlot
import net.bible.sharedcore.progress.MonthLabel
import net.bible.sharedcore.progress.ReadingProgressModel
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedcore.progress.ReadingSummary
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedui.progress.ReadingProgressScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingProgressGoldenTest {

    private fun otBooks(empty: Boolean): List<BookHeat> = if (empty) {
        listOf(
            BookHeat(bookId = "Gen", shortName = "Gen", isNT = false, readPercent = 0f, isComplete = false),
            BookHeat(bookId = "Exod", shortName = "Exod", isNT = false, readPercent = 0f, isComplete = false),
        )
    } else {
        listOf(
            BookHeat(bookId = "Gen", shortName = "Gen", isNT = false, readPercent = 1.0f, isComplete = true),
            BookHeat(bookId = "Exod", shortName = "Exod", isNT = false, readPercent = 0.6f, isComplete = false),
            BookHeat(bookId = "Lev", shortName = "Lev", isNT = false, readPercent = 0.1f, isComplete = false),
            BookHeat(bookId = "Num", shortName = "Num", isNT = false, readPercent = 0f, isComplete = false),
            BookHeat(bookId = "Deut", shortName = "Deut", isNT = false, readPercent = 0.25f, isComplete = false),
            BookHeat(bookId = "Josh", shortName = "Josh", isNT = false, readPercent = 0f, isComplete = false),
        )
    }

    private fun ntBooks(empty: Boolean): List<BookHeat> = if (empty) {
        listOf(BookHeat(bookId = "Matt", shortName = "Matt", isNT = true, readPercent = 0f, isComplete = false))
    } else {
        listOf(
            BookHeat(bookId = "Matt", shortName = "Matt", isNT = true, readPercent = 0.4f, isComplete = false),
            BookHeat(bookId = "Mark", shortName = "Mark", isNT = true, readPercent = 0.8f, isComplete = false),
            BookHeat(bookId = "Luke", shortName = "Luke", isNT = true, readPercent = 0f, isComplete = false),
            BookHeat(bookId = "John", shortName = "John", isNT = true, readPercent = 0.15f, isComplete = false),
        )
    }

    private fun chapterDetail(): ChapterDetail {
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

    private fun calendar(empty: Boolean) = CalendarHeatmapLayout.assemble(
        skeleton = CalendarSkeleton(
            slots = (0 until 8).flatMap { w -> (0 until 7).map { d -> DaySlot(w, d, (w * 7L + d) * 86_400_000L) } },
            monthLabels = listOf(MonthLabel(0, "Jan"), MonthLabel(4, "Feb")),
            weeks = 8,
            dayOfWeekLabels = listOf("", "M", "", "W", "", "F", ""),
        ),
        dailyCounts = if (empty) {
            emptyMap()
        } else {
            (0 until 56).associate { i -> i * 86_400_000L to (i % 5) }
        },
    )

    private fun model(
        empty: Boolean = false,
        cycle: Int = 1,
        latest: Int = 1,
        withChapterDetail: Boolean = !empty,
    ): ReadingProgressModel {
        val bookPercentScaleMax = ReadingProgressScale.resolveBookPercentScaleMax(
            (otBooks(empty) + ntBooks(empty)).maxOfOrNull { it.readPercent },
        )
        return ReadingProgressModel(
            tab = ReadingTab.READING,
            summary = if (empty) {
                ReadingSummary(chaptersRead = 0, activeDays = 0, overallPermille = 0, overallPercent = 0f)
            } else {
                ReadingSummary(chaptersRead = 42, activeDays = 17, overallPermille = 320, overallPercent = 32f)
            },
            otBooks = otBooks(empty),
            ntBooks = ntBooks(empty),
            bookPercentScaleMax = bookPercentScaleMax,
            bookPercentScaleSteps = ReadingProgressScale.buildBookPercentScaleSteps(bookPercentScaleMax),
            chapterDetail = if (withChapterDetail) chapterDetail() else null,
            calendar = calendar(empty),
            cycle = cycle,
            latestCycle = latest,
        )
    }

    private fun screen(m: ReadingProgressModel): @Composable () -> Unit = {
        ReadingProgressScreen(
            model = m,
            loading = false,
            onUp = {},
            onSelectTab = {},
            onPrevCycle = {},
            onNextCycle = {},
            onNewCycle = {},
            onBookClick = {},
            onBookLongClick = {},
            onChapterClick = {},
            onChapterLongClick = {},
            onCalendarDayClick = {},
            onOpenSettings = {},
            onShowHelp = {},
        )
    }

    @Test fun reading_primary() =
        captureMatrix("ReadingProgress", "reading", heightDp = 1400, content = screen(model()))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun reading_primary_rtl() =
        captureRtl("ReadingProgress", "reading", heightDp = 1400, content = screen(model()))

    @Test fun reading_empty() =
        captureGolden("ReadingProgress", "empty", EDGE_MODE, heightDp = 1000, content = screen(model(empty = true)))

    @Test fun reading_multiCycle() =
        captureGolden("ReadingProgress", "multiCycle", EDGE_MODE, heightDp = 1400, content = screen(model(cycle = 2, latest = 3)))
}
