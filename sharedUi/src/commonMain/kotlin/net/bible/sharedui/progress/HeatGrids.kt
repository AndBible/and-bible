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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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

/** Cells per row for the book/chapter heat grids — an approximation of the classic weighted GridLayout. */
private const val GRID_COLUMNS = 7

private val CellCorner = 4.dp
private val CellSpacing = 4.dp
private val TargetDotSize = 6.dp

/**
 * OT/NT book heat grid. Mirrors classic `ReadingProgressActivity.createBookButton` +
 * `refreshBibleHeatmap`: one cell per book, coloured by [color], labelled with [BookHeat.shortName]
 * (a small superscript "✓" appended when [BookHeat.isComplete]), an optional small target dot
 * when [BookHeat.hasTarget]. Layout is a [FlowRow] of roughly-equal-width cells (~[GRID_COLUMNS]
 * per row) approximating the classic weighted `GridLayout`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookHeatGrid(
    books: List<BookHeat>,
    color: @Composable (BookHeat) -> Color,
    onClick: (bookId: String) -> Unit,
    onLongClick: ((bookId: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        maxItemsInEachRow = GRID_COLUMNS,
        horizontalArrangement = Arrangement.spacedBy(CellSpacing),
        verticalArrangement = Arrangement.spacedBy(CellSpacing),
    ) {
        for (book in books) {
            val bgColor = color(book)
            val textColor = textColorForBackground(bgColor)
            HeatCell(
                modifier = Modifier.weight(1f, fill = true),
                bgColor = bgColor,
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
                    color = textColor,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Chapter heat grid for a single book's chapter detail. Mirrors classic
 * `ReadingProgressActivity.createChapterButton` + `renderChapterDetail`: one cell per chapter,
 * coloured by [color], labelled with the chapter number, an optional small target dot when
 * [ChapterHeat.hasTarget].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChapterHeatGrid(
    chapters: List<ChapterHeat>,
    color: @Composable (ChapterHeat) -> Color,
    onClick: (chapter: Int) -> Unit,
    onLongClick: ((chapter: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        maxItemsInEachRow = GRID_COLUMNS,
        horizontalArrangement = Arrangement.spacedBy(CellSpacing),
        verticalArrangement = Arrangement.spacedBy(CellSpacing),
    ) {
        for (chapter in chapters) {
            val bgColor = color(chapter)
            val textColor = textColorForBackground(bgColor)
            HeatCell(
                modifier = Modifier.weight(1f, fill = true),
                bgColor = bgColor,
                hasTarget = chapter.hasTarget,
                onClick = { onClick(chapter.chapter) },
                onLongClick = onLongClick?.let { cb -> { cb(chapter.chapter) } },
            ) {
                Text(
                    text = "${chapter.chapter}",
                    color = textColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
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
