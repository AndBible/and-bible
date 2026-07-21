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
import net.bible.sharedcore.progress.MemorizeModel
import net.bible.sharedcore.progress.MonthLabel
import net.bible.sharedcore.progress.PassageRow
import net.bible.sharedcore.progress.ReadingProgressModel
import net.bible.sharedcore.progress.ReadingProgressScale
import net.bible.sharedcore.progress.ReadingSummary
import net.bible.sharedcore.progress.ReadingTab
import net.bible.sharedcore.progress.TargetRow
import net.bible.sharedui.progress.MemorizeTabBody
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

    // --- Memorize tab ---
    // MemorizeTabBody is a self-contained composable (no ReadingProgressScreen host needed), so
    // these fixtures render it directly, mirroring the Reading-tab fixtures above but bypassing
    // the screen scaffold entirely.

    private fun memOtBooks(hasTargets: Boolean): List<BookHeat> = listOf(
        BookHeat(bookId = "Gen", shortName = "Gen", isNT = false, readPercent = 1.0f, isComplete = false, hasTarget = hasTargets),
        BookHeat(bookId = "Exod", shortName = "Exod", isNT = false, readPercent = 0.6f, isComplete = false),
        BookHeat(bookId = "Lev", shortName = "Lev", isNT = false, readPercent = 0.1f, isComplete = false),
        BookHeat(bookId = "Num", shortName = "Num", isNT = false, readPercent = 0f, isComplete = false),
        BookHeat(bookId = "Deut", shortName = "Deut", isNT = false, readPercent = 0.3f, isComplete = false, hasTarget = hasTargets),
        BookHeat(bookId = "Josh", shortName = "Josh", isNT = false, readPercent = 0f, isComplete = false),
    )

    private fun memNtBooks(): List<BookHeat> = listOf(
        BookHeat(bookId = "Matt", shortName = "Matt", isNT = true, readPercent = 0.4f, isComplete = false),
        BookHeat(bookId = "Mark", shortName = "Mark", isNT = true, readPercent = 0.8f, isComplete = false),
        BookHeat(bookId = "Luke", shortName = "Luke", isNT = true, readPercent = 0f, isComplete = false),
        BookHeat(bookId = "John", shortName = "John", isNT = true, readPercent = 0.15f, isComplete = false),
    )

    private fun memChapterDetail(): ChapterDetail {
        val chapters = (1..12).map { chapter ->
            val progress = when {
                chapter <= 2 -> 1.0f
                chapter <= 5 -> 0.6f
                chapter <= 8 -> 0.2f
                else -> 0f
            }
            ChapterHeat(
                chapter = chapter,
                count = 0,
                level = ReadingProgressScale.memorizationLevel(progress),
                hasTarget = chapter == 3 || chapter == 9,
            )
        }
        return ChapterDetail(bookId = "Gen", title = "Genesis", chapters = chapters, maxCount = 0, countScaleSteps = emptyList())
    }

    private fun memPassages(): List<PassageRow> = listOf(
        PassageRow(rangeName = "John 3:16", startOrdinal = 1001, endOrdinal = 1001, relativeTime = "2 days ago"),
        PassageRow(rangeName = "Psalm 23:1-6", startOrdinal = 2001, endOrdinal = 2006, relativeTime = "1 week ago"),
        PassageRow(rangeName = "Romans 8:28", startOrdinal = 3001, endOrdinal = 3001, relativeTime = "3 weeks ago"),
    )

    private fun memTargets(): List<TargetRow> = listOf(
        TargetRow(id = "t1", rangeName = "Matthew 5:1-12", memorized = 6, total = 12, startOrdinal = 4001, endOrdinal = 4012, relativeTime = "started 5 days ago"),
        TargetRow(id = "t2", rangeName = "1 Corinthians 13:1-13", memorized = 0, total = 13, startOrdinal = 5001, endOrdinal = 5013, relativeTime = "started today"),
    )

    private fun memModel(overviewActive: Boolean, empty: Boolean = false): MemorizeModel {
        val passages = if (empty) emptyList() else memPassages()
        val targets = if (empty) emptyList() else memTargets()
        return MemorizeModel(
            overviewActive = overviewActive,
            memorizedCount = if (empty) 0 else 47,
            targetTotal = if (empty) 0 else 25,
            targetMemorized = if (empty) 0 else 6,
            targetPermille = if (empty) 0 else 240,
            targetPercent = if (empty) 0f else 24f,
            otBooks = memOtBooks(hasTargets = !empty),
            ntBooks = memNtBooks(),
            memChapterDetail = if (empty) null else memChapterDetail(),
            calendar = calendar(empty = empty),
            passages = passages,
            passagesShown = passages.size,
            passagesTotal = passages.size,
            targets = targets,
            targetsShown = targets.size,
            targetsTotal = targets.size,
        )
    }

    private fun memorizeContent(m: MemorizeModel): @Composable () -> Unit = {
        MemorizeTabBody(
            memorize = m,
            onSetOverview = {},
            onBookClick = {},
            onChapterClick = {},
            onCalendarDayClick = {},
            onPassageTap = { _, _ -> },
            onPassageUnmark = {},
            onTargetTap = { _, _ -> },
            onTargetRemove = {},
            onShowMorePassages = {},
            onShowMoreTargets = {},
        )
    }

    @Test fun memorizeOverview() =
        captureMatrix("ReadingProgress", "memorizeOverview", heightDp = 1200, content = memorizeContent(memModel(overviewActive = true)))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun memorizeOverview_rtl() =
        captureRtl("ReadingProgress", "memorizeOverview", heightDp = 1200, content = memorizeContent(memModel(overviewActive = true)))

    @Test fun memorizeList() =
        captureGolden("ReadingProgress", "memorizeList", EDGE_MODE, heightDp = 1200, content = memorizeContent(memModel(overviewActive = false)))

    @Test fun memorizeEmpty() =
        captureGolden("ReadingProgress", "memorizeEmpty", EDGE_MODE, heightDp = 800, content = memorizeContent(memModel(overviewActive = false, empty = true)))
}
