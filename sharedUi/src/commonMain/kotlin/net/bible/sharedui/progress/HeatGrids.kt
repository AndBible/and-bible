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

package net.bible.sharedui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.sharedcore.progress.BookHeat
import net.bible.sharedcore.progress.ChapterHeat

/**
 * Cells per row for the book heat grids — classic's `GridLayout android:columnCount="6"`
 * (`res/layout/reading_progress.xml:126`/`:141` reading tab, `:428`/`:443` memorize tab).
 */
const val BOOK_GRID_COLUMNS = 6

/**
 * Cells per row for the chapter-detail heat grids — classic's `columnCount="10"`
 * (`res/layout/reading_progress.xml:173` reading tab, `:467` memorize tab).
 */
const val CHAPTER_GRID_COLUMNS = 10

private val CellCorner = 4.dp
private val CellSpacing = 4.dp
private val TargetDotSize = 6.dp

/**
 * A grid of equal-width cells, [columns] per row, with the final short row's missing slots kept
 * empty instead of letting its cells grow.
 *
 * This is what classic's `GridLayout` + `columnSpec(UNDEFINED, 1, 1f)` does: excess width is shared
 * per COLUMN across the whole grid, so cell width is identical in every row. A `FlowRow` with
 * weighted children (the port's previous approach) resolves weights per ROW, which widened a short
 * last row -- reported in the maintainer's A/B batch 2. `LazyVerticalGrid` is not an option here:
 * both callers render inside the screen's `verticalScroll` Column.
 */
@Composable
private fun <T> UniformCellGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    cell: @Composable (item: T, cellModifier: Modifier) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CellSpacing)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CellSpacing),
            ) {
                rowItems.forEach { item -> cell(item, Modifier.weight(1f)) }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * OT/NT book heat grid. Mirrors classic `ReadingProgressActivity.createBookButton` +
 * `refreshBibleHeatmap`: one cell per book, coloured by [colors] (background + matching text
 * colour), labelled with [BookHeat.shortName] (a small superscript "✓" appended when
 * [BookHeat.isComplete]), an optional small target dot when [BookHeat.hasTarget]. Layout is a
 * [UniformCellGrid] of [BOOK_GRID_COLUMNS] equal-width cells per row, mirroring the classic
 * weighted `GridLayout`.
 */
@Composable
fun BookHeatGrid(
    books: List<BookHeat>,
    colors: @Composable (BookHeat) -> HeatColors,
    onClick: (bookId: String) -> Unit,
    onLongClick: ((bookId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    UniformCellGrid(items = books, columns = BOOK_GRID_COLUMNS, modifier = modifier) { book, cellModifier ->
        val cellColors = colors(book)
        HeatCell(
            modifier = cellModifier,
            bgColor = cellColors.background,
            hasTarget = book.hasTarget,
            onClick = { onClick(book.bookId) },
            onLongClick = onLongClick?.let { cb -> { cb(book.bookId) } },
        ) {
            Text(
                text = if (book.isComplete) {
                    buildAnnotatedString {
                        append(book.shortName)
                        append(" ")
                        withStyle(
                            SpanStyle(
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                baselineShift = BaselineShift.Superscript,
                            ),
                        ) {
                            append("✓")
                        }
                    }
                } else {
                    buildAnnotatedString { append(book.shortName) }
                },
                color = cellColors.content,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Chapter heat grid for a single book's chapter detail. Mirrors classic
 * `ReadingProgressActivity.createChapterButton` + `renderChapterDetail`: one cell per chapter,
 * coloured by [colors] (background + matching text colour), labelled with the chapter number, an
 * optional small target dot when [ChapterHeat.hasTarget]. Layout is a [UniformCellGrid] of
 * [CHAPTER_GRID_COLUMNS] equal-width cells per row, mirroring the classic weighted `GridLayout`.
 */
@Composable
fun ChapterHeatGrid(
    chapters: List<ChapterHeat>,
    colors: @Composable (ChapterHeat) -> HeatColors,
    onClick: (chapter: Int) -> Unit,
    onLongClick: ((chapter: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    UniformCellGrid(items = chapters, columns = CHAPTER_GRID_COLUMNS, modifier = modifier) { chapter, cellModifier ->
        val cellColors = colors(chapter)
        HeatCell(
            modifier = cellModifier,
            bgColor = cellColors.background,
            hasTarget = chapter.hasTarget,
            onClick = { onClick(chapter.chapter) },
            onLongClick = onLongClick?.let { cb -> { cb(chapter.chapter) } },
        ) {
            Text(
                text = "${chapter.chapter}",
                color = cellColors.content,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A single heat-map cell: rounded [bgColor] background, centred [label], an optional small
 * target-indicator dot at the top end (mirrors the classic `FrameLayout` + `GradientDrawable`
 * dot overlay). Click/long-click drive [onClick]/[onLongClick] via [Modifier.combinedClickable].
 */
@Composable
private fun HeatCell(
    modifier: Modifier = Modifier,
    bgColor: Color,
    hasTarget: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(bgColor, RoundedCornerShape(CellCorner))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        label()
        if (hasTarget) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(TargetDotSize)
                    .background(targetDot(), CircleShape),
            )
        }
    }
}
