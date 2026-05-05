/*
 * Copyright (c) 2026 Andreas Brauchli and the AndBible contributors.
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

package net.bible.android.view.activity.passagefinder

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.android.control.passagefinder.PassageFinderDataSource
import kotlin.math.abs

/** Minimum width of a verse cell at far edges. */
private val VERSE_MIN_WIDTH = 18.dp
/** Maximum width of a verse cell at center of lens. Matches CHAPTER_MAX_WIDTH so the
 *  centered numeric cell looks the same size on both strips. */
private val VERSE_MAX_WIDTH = 56.dp
/** Minimum height of a verse cell at the far edges. */
private val VERSE_MIN_HEIGHT = 24.dp
/** Maximum height of a verse cell at center of lens. Matches CHAPTER_MAX_HEIGHT. */
private val VERSE_MAX_HEIGHT = 56.dp
/** Lens radius in pixels — how far from center the magnification falls off.
 *  Tighter than the chapter strip (290px) because verse cells are narrower
 *  and a tighter focus reduces visual clutter when scanning long chapters. */
private const val VERSE_LENS_RADIUS_PX = 260f

/**
 * Horizontal strip of verse numbers for the currently selected chapter.
 *
 * Scrolling stops at the first and last verse of the current chapter.
 * The user must manually switch chapters (via the chapter strip) to see other chapters' verses.
 */
@Composable
fun VerseStrip(
    modifier: Modifier = Modifier,
    books: List<PassageFinderDataSource.BookInfo>,
    selectedBookIndex: Int,
    selectedChapter: Int,
    selectedVerse: Int,
    verseCounts: (Int, Int) -> Int,
    onVerseSelected: (Int) -> Unit,
    onVerseTapped: ((Int) -> Unit)? = null,
    disableAnimations: Boolean = false,
) {
    if (books.isEmpty()) return

    val currentOnVerseSelected by rememberUpdatedState(onVerseSelected)
    val currentOnVerseTapped by rememberUpdatedState(onVerseTapped)
    val verseCount = verseCounts(selectedBookIndex, selectedChapter)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedVerse - 1).coerceAtLeast(0),
    )
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )

    val coordinator = remember { ScrollCoordinator() }

    // Re-center only when selection changes externally (not from scroll settle).
    // scrollToItem(idx) under contentPadding(halfViewport) leaves the item's leading
    // edge at the inner content start — the item's center then sits half-a-cell past
    // the viewport center. Apply a delta correction so the auto-selected verse lands
    // in exactly the same position as a user-flung snap-settle.
    LaunchedEffect(selectedBookIndex, selectedChapter, selectedVerse) {
        if (!coordinator.shouldRecenter()) return@LaunchedEffect
        val targetIndex = (selectedVerse - 1).coerceAtLeast(0)
        coordinator.withProgrammaticScroll {
            listState.scrollToItem(targetIndex)
            snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                .filter { items -> items.any { it.index == targetIndex } }
                .first()
            withFrameNanos { }
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            val item = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                ?: return@withProgrammaticScroll
            val delta = (item.offset + item.size / 2f) - viewportCenter
            if (abs(delta) > 0.5f) {
                listState.scrollBy(delta)
            }
        }
    }

    // Detect scroll settle
    LaunchedEffect(Unit) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && wasScrolling && !coordinator.programmaticScroll) {
                    val info = listState.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val centeredItem = info.visibleItemsInfo.minByOrNull {
                        abs((it.offset + it.size / 2f) - center)
                    }
                    if (centeredItem != null) {
                        val verse = centeredItem.index + 1
                        coordinator.markScrollSettled()
                        currentOnVerseSelected(verse)
                    }
                }
                wasScrolling = scrolling
            }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val halfViewportPx = constraints.maxWidth / 2f
        val halfViewportDp = with(LocalDensity.current) { halfViewportPx.toDp() }

        // Single source of truth for proximity. Cells read this State only inside
        // graphicsLayer (draw phase) — no recomposition during scroll. Avoids the
        // per-cell derivedStateOf-over-layoutInfo pattern that caused N×M scans/frame.
        val proximityMap = remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val viewCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                buildMap {
                    for (item in info.visibleItemsInfo) {
                        val dist = abs((item.offset + item.size / 2f) - viewCenter)
                        val p = (1f - dist / VERSE_LENS_RADIUS_PX).coerceIn(0f, 1f)
                        put(item.index, p)
                    }
                }
            }
        }

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(horizontal = halfViewportDp),
        ) {
            items(count = verseCount) { index ->
                val verseNumber = index + 1
                val isSelected = verseNumber == selectedVerse

                VerseCell(
                    verseNumber = verseNumber,
                    index = index,
                    proximityMap = proximityMap,
                    isSelected = isSelected,
                    disableAnimations = disableAnimations,
                    onClick = {
                        currentOnVerseSelected(verseNumber)
                        currentOnVerseTapped?.invoke(verseNumber)
                    },
                )
            }
        }
    }
}

/** Fixed layout size for cells — between min and max so center items overflow slightly. */
private val VERSE_CELL_SIZE = 20.dp
/** Scale ratios for graphicsLayer — min and max relative to fixed cell size. */
private val VERSE_MIN_SCALE = VERSE_MIN_WIDTH.value / VERSE_CELL_SIZE.value
private val VERSE_MAX_SCALE = VERSE_MAX_WIDTH.value / VERSE_CELL_SIZE.value

/**
 * Verse cell with proximity-based scaling and z-ordering.
 *
 * All cells occupy VERSE_CELL_SIZE in layout. Proximity changes are read inside
 * graphicsLayer/drawBehind so they only trigger redraw, never recomposition. The only
 * composition-phase read is [isCenter] (a Boolean), which flips at most twice per scroll
 * pass and drives binary z-ordering: the centered item gets `zIndex = 1f`, every other
 * item gets `0f` — guaranteeing the center always renders on top of its neighbors.
 */
@Composable
private fun VerseCell(
    verseNumber: Int,
    index: Int,
    proximityMap: State<Map<Int, Float>>,
    isSelected: Boolean,
    disableAnimations: Boolean = false,
    onClick: () -> Unit,
) {
    val bgAlpha = if (isSelected) 0.95f else 0.85f

    val isCenter by remember(proximityMap, index) {
        derivedStateOf { (proximityMap.value[index] ?: 0f) > 0.95f }
    }

    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected || isCenter) 1f else 0f,
        animationSpec = if (disableAnimations) snap() else tween(durationMillis = 80),
        label = "verseBorderAlpha",
    )

    // Z-order needs both signals because they diverge mid-scroll:
    //   isCenter   — visual center, updates frame-by-frame as the user scrolls
    //   isSelected — committed selection, only updates on scroll settle
    // At rest they agree. Mid-scroll, isCenter keeps the moving visual center on top.
    // On initial render, proximity hasn't crossed the threshold yet so isCenter is false
    // for everyone and source order would win (4 over 3); isSelected is the fallback.
    Box(
        modifier = Modifier
            .width(VERSE_CELL_SIZE)
            .height(VERSE_CELL_SIZE)
            .zIndex(if (isCenter || isSelected) 1f else 0f)
            .graphicsLayer {
                val proximity = proximityMap.value[index] ?: 0f
                // Quartic falloff steepens the bell curve so the center cell visibly
                // dominates its immediate neighbors instead of blending into them.
                val sizeProximity = proximity * proximity * proximity * proximity
                val scale = VERSE_MIN_SCALE + (VERSE_MAX_SCALE - VERSE_MIN_SCALE) * sizeProximity
                scaleX = scale
                scaleY = scale
                alpha = 0.3f + 0.7f * proximity
            }
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = bgAlpha))
            .drawBehind {
                if (borderAlpha > 0f) {
                    // Border drawn ON TOP of background and clipped to the rounded shape
                    // (clip is outer in the chain), so the strokes can't bleed past the
                    // rounded corners into the rectangular cell area behind.
                    val cornerRadiusPx = 6.dp.toPx()
                    // Outer glow halo
                    drawRoundRect(
                        color = Color.Red.copy(alpha = borderAlpha * 0.3f),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                    // Inner solid core
                    drawRoundRect(
                        color = Color.Red.copy(alpha = borderAlpha * 0.8f),
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        style = Stroke(width = 1.5f.dp.toPx()),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = verseNumber.toString(),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = if (isSelected || isCenter) FontWeight.Bold else FontWeight.Normal,
                color = Color.Black,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
