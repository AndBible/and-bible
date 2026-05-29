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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import net.bible.android.control.passagefinder.PassageFinderDataSource
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import kotlin.math.abs

/** Minimum spine width for 1-chapter books (proportional base). */
private val SPINE_PROPORTIONAL_MIN = 7.dp
/** Maximum spine width for the book with the most chapters (proportional base). */
private val SPINE_PROPORTIONAL_MAX = 14.dp
/** Width of a book spine at center of lens. */
private val SPINE_LENS_WIDTH = 36.dp
/** Max height of a book spine. */
private val SPINE_HEIGHT = 123.dp
/** Min height at far edges. */
private val SPINE_MIN_HEIGHT = 86.dp
/** Lens radius in pixels — how far from center the lens effect reaches.
 *  Higher = more books visible at larger sizes. 320 gives ~8 magnified books on a 1080px screen. */
private const val LENS_RADIUS_PX = 320f

/** Static brushes for the 3D-relief shading. Allocated once at file load instead of
 *  per-frame inside drawBehind. The brush color stops are normalized 0..1 over the
 *  rect we draw into, and proximity is applied via the `alpha` parameter on drawRect
 *  — so neither the brush nor a Color.copy() needs to be allocated each frame. */
private val SPINE_HIGHLIGHT_BRUSH = Brush.horizontalGradient(
    0f to Color.White,
    1f to Color.Transparent,
)
private val SPINE_RIGHT_SHADOW_BRUSH = Brush.horizontalGradient(
    0f to Color.Transparent,
    1f to Color.Black,
)
private val SPINE_BOTTOM_SHADOW_BRUSH = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color.Black,
)

/**
 * LazyRow-based horizontal book strip with magnifying lens effect.
 *
 * Uses LazyRow for natural scroll/fling/snap behavior. Each book spine's width, height,
 * and alpha are computed from proximity to the viewport center via graphicsLayer transforms
 * (draw-phase only — no recomposition during scroll).
 *
 * @param openBookIndex The book currently open in the Bible reader (gets a visual marker).
 * @param initialScrollIndex The book to center the scroll on when opening.
 */
@Composable
fun BookStrip(
    modifier: Modifier = Modifier,
    books: List<PassageFinderDataSource.BookInfo>,
    openBookIndex: Int,
    selectedBookIndex: Int,
    initialScrollIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    chapterCounts: (Int) -> Int = { 1 },
    onBookTapped: ((Int) -> Unit)? = null,
    onUserScrollChanged: ((Boolean) -> Unit)? = null,
    syncFromBoundary: Boolean = false,
    isMonochrome: Boolean = CommonUtils.settings.monochromeMode,
    isDarkTheme: Boolean = ScreenSettings.nightMode,
) {
    if (books.isEmpty()) return

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
    )
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center,
    )
    val currentOnSelectedIndexChanged by rememberUpdatedState(onSelectedIndexChanged)
    val currentOnUserScrollChanged by rememberUpdatedState(onUserScrollChanged)

    val coordinator = remember { ScrollCoordinator() }
    val proximityMap = remember { mutableStateOf(emptyMap<Int, Float>()) }

    // Emit user-initiated scroll start/end to the parent so it can drive strip visibility.
    // Programmatic scrolls (initial centering, boundary sync) are filtered out via the
    // ScrollCoordinator flag so they don't trigger spurious hide animations.
    LaunchedEffect(Unit) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!coordinator.programmaticScroll) {
                    currentOnUserScrollChanged?.invoke(scrolling)
                }
            }
    }

    // Center the target book on first show. With proportional widths AND lens magnification,
    // a single scrollToItem can't precisely center: the target item's rendered width grows
    // from baseWidth to SPINE_LENS_WIDTH as proximity increases, and growth happens from the
    // item's left edge — so its center shifts right whenever it grows. The result is an
    // adjacent book ending up closer to the viewport center than the intended one ("two
    // books at the same height"). Iteratively re-center until convergence — updating the
    // proximityMap each iteration so spine widths grow into their final lens-magnified
    // values alongside the position fix. Without this, initial render leaves widths frozen
    // at their pre-centered proximities, producing a flatter bell curve than after a scroll.
    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .filter { it.isNotEmpty() }
            .first()

        coordinator.withProgrammaticScroll {
            repeat(8) {
                val info = listState.layoutInfo
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val targetItem = info.visibleItemsInfo.firstOrNull { it.index == initialScrollIndex }
                val converged = targetItem != null &&
                    abs((targetItem.offset + targetItem.size / 2f) - viewportCenter) < 1f
                proximityMap.value = computeProximityMap(info)
                if (converged) return@withProgrammaticScroll
                if (targetItem != null) {
                    val delta = (targetItem.offset + targetItem.size / 2f) - viewportCenter
                    listState.scrollBy(delta)
                }
                // Wait one frame for layout to settle before re-measuring.
                withFrameNanos { }
            }
            // Final proximity sync after the loop exits at the iteration cap.
            proximityMap.value = computeProximityMap(listState.layoutInfo)
        }
    }

    // Sync BookStrip position when a boundary crossing at chapter/verse level changes the selected book
    LaunchedEffect(selectedBookIndex) {
        if (!coordinator.shouldRecenter()) return@LaunchedEffect
        if (syncFromBoundary && selectedBookIndex in books.indices) {
            coordinator.withProgrammaticScroll {
                listState.animateScrollToItem(selectedBookIndex)
            }
        }
    }

    // Notify parent when centered book changes on scroll settle
    LaunchedEffect(Unit) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling && wasScrolling && !coordinator.programmaticScroll) {
                    val info = listState.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val idx = info.visibleItemsInfo.minByOrNull {
                        abs((it.offset + it.size / 2f) - center)
                    }?.index
                    if (idx != null) {
                        coordinator.markScrollSettled()
                        currentOnSelectedIndexChanged(idx)
                    }
                }
                wasScrolling = scrolling
            }
    }

    // Memoize chapterCounts(index) into a flat List so itemsIndexed doesn't call
    // back through ViewModel → DataSource → JSword on every recomposition. Result is a
    // function of book identity, so `remember(books)` is the correct invalidation key.
    val chapterCountsList = remember(books) {
        List(books.size) { idx -> chapterCounts(idx) }
    }
    val maxChapters = remember(chapterCountsList) {
        chapterCountsList.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val halfViewportPx = constraints.maxWidth / 2f
        val halfViewportDp = with(LocalDensity.current) { halfViewportPx.toDp() }

        // Compute proximity map (0..1) for each visible item based on distance from center.
        // Updated from the effect phase (not composition) so scroll-frame writes don't mutate
        // state during recomposition. Only updates during active scrolling — frozen when idle
        // to prevent a layout feedback loop (width changes → position shifts → proximity
        // recalculates). Initial-layout / re-center updates are driven by the recenter
        // LaunchedEffect above.
        LaunchedEffect(Unit) {
            snapshotFlow { listState.isScrollInProgress to computeProximityMap(listState.layoutInfo) }
                .collect { (scrolling, proximity) ->
                    if (scrolling) proximityMap.value = proximity
                }
        }

        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = halfViewportDp),
        ) {
            itemsIndexed(books) { index, bookItem ->
                val isGroupStart = index > 0 && bookItem.category != books[index - 1].category
                val proximity = proximityMap.value[index] ?: 0f
                val isOpenBook = index == openBookIndex

                BookSpine(
                    bookItem = bookItem,
                    isOpenBook = isOpenBook,
                    proximity = proximity,
                    chapterCount = chapterCountsList[index],
                    maxChapters = maxChapters,
                    isGroupStart = isGroupStart,
                    isMonochrome = isMonochrome,
                    isDarkTheme = isDarkTheme,
                    onClick = {
                        currentOnSelectedIndexChanged(index)
                        onBookTapped?.invoke(index)
                    },
                )
            }
        }
    }
}

/**
 * A single book spine in the strip.
 *
 * @param isOpenBook Whether this is the book currently open in the Bible reader
 *        (shown with a marker, but does NOT affect size — size is purely proximity-based).
 */
@Composable
private fun BookSpine(
    bookItem: PassageFinderDataSource.BookInfo,
    isOpenBook: Boolean,
    proximity: Float,
    chapterCount: Int = 1,
    maxChapters: Int = 1,
    isGroupStart: Boolean,
    isMonochrome: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit,
) {
    // Proportional base width from chapter count
    val fraction = (chapterCount.toFloat() / maxChapters.toFloat()).coerceIn(0f, 1f)
    val baseWidth = SPINE_PROPORTIONAL_MIN + (SPINE_PROPORTIONAL_MAX - SPINE_PROPORTIONAL_MIN) * fraction

    // Quadratic curve for size: center book is clearly larger than its neighbors
    val sizeProximity = proximity * proximity
    val targetWidth = baseWidth + (SPINE_LENS_WIDTH - baseWidth) * sizeProximity
    val targetHeight = SPINE_MIN_HEIGHT + (SPINE_HEIGHT - SPINE_MIN_HEIGHT) * sizeProximity
    // Linear proximity for alpha so approaching items remain visible further out
    val alpha = (0.85f + 0.15f * proximity).coerceIn(0.85f, 1f)

    // Only the exact center item gets bold; all others are normal weight
    val fontWeight = if (proximity > 0.95f) FontWeight.Bold else FontWeight.Normal

    // Per-book color with subtle variation within groups
    val baseColor = if (isMonochrome) {
        val shade = bookItem.category.monochromeShade
        Color(shade, shade, shade)
    } else {
        bookItem.category.color
    }
    val bookHash = bookItem.shortName.hashCode() and 0xFF
    val colorVariation = (bookHash % 30 - 15) / 255f
    val spineAlpha = 0.75f + 0.25f * proximity
    // The per-channel variation factors (0.7/0.5 on green/blue) add a warm tint in color
    // mode. In monochrome the base color is grayscale (r=g=b), so apply the same variation
    // to every channel — otherwise the differing factors would re-introduce a color cast
    // on e-ink devices.
    val variedColor = if (isMonochrome) {
        Color(
            red = (baseColor.red + colorVariation).coerceIn(0f, 1f),
            green = (baseColor.green + colorVariation).coerceIn(0f, 1f),
            blue = (baseColor.blue + colorVariation).coerceIn(0f, 1f),
            alpha = spineAlpha,
        )
    } else {
        Color(
            red = (baseColor.red + colorVariation).coerceIn(0f, 1f),
            green = (baseColor.green + colorVariation * 0.7f).coerceIn(0f, 1f),
            blue = (baseColor.blue + colorVariation * 0.5f).coerceIn(0f, 1f),
            alpha = spineAlpha,
        )
    }

    Box(
        modifier = Modifier
            .width(targetWidth)
            .height(targetHeight)
            .zIndex(proximity)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(2.dp))
            .background(variedColor)
            .drawBehind {
                // 3D relief: left highlight gradient (white→transparent on left 30%).
                // The gradient brush is static (allocated once at file load); proximity
                // is applied via the alpha parameter so no Brush/Color is allocated per frame.
                drawRect(
                    brush = SPINE_HIGHLIGHT_BRUSH,
                    topLeft = Offset.Zero,
                    size = Size(size.width * 0.3f, size.height),
                    alpha = 0.25f * proximity,
                )
                // 3D relief: right shadow gradient (transparent→black on right 30%).
                drawRect(
                    brush = SPINE_RIGHT_SHADOW_BRUSH,
                    topLeft = Offset(size.width * 0.7f, 0f),
                    size = Size(size.width * 0.3f, size.height),
                    alpha = 0.2f * proximity,
                )
                // Bottom shadow for depth (transparent→black on bottom 15%).
                drawRect(
                    brush = SPINE_BOTTOM_SHADOW_BRUSH,
                    topLeft = Offset(0f, size.height * 0.85f),
                    size = Size(size.width, size.height * 0.15f),
                    alpha = 0.15f * proximity,
                )
                // Group divider (thicker line between biblical categories)
                if (isGroupStart) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.5f),
                        start = Offset(0f, 4f),
                        end = Offset(0f, size.height - 4f),
                        strokeWidth = 2f,
                    )
                }
                // Open book marker: small colored bar at the bottom
                if (isOpenBook) {
                    // Monochrome keeps the marker grayscale (black on the light e-ink panel);
                    // colored blue accent only in normal day mode.
                    val markerColor = when {
                        isDarkTheme -> Color.White
                        isMonochrome -> Color.Black
                        else -> Color(0xFF1565C0)
                    }
                    drawRect(
                        color = markerColor,
                        topLeft = Offset(1f, size.height - 4.dp.toPx()),
                        size = Size(size.width - 2f, 4.dp.toPx()),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Text on all books: 8sp minimum, scaling smoothly to 16sp at center
        val fontSize = (8 + proximity * 8).sp
        val textAlpha = (0.45f + 0.55f * proximity).coerceIn(0.45f, 1f)

        BasicText(
            text = bookItem.shortName,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = TextAlign.Center,
                color = Color.Black.copy(alpha = textAlpha),
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .graphicsLayer { rotationZ = -90f }
                .requiredWidth(targetHeight),
        )
    }
}

private fun computeProximityMap(
    info: androidx.compose.foundation.lazy.LazyListLayoutInfo,
): Map<Int, Float> {
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return buildMap {
        for (item in info.visibleItemsInfo) {
            val itemCenter = item.offset + item.size / 2f
            val dist = abs(itemCenter - center)
            val proximity = (1f - (dist / LENS_RADIUS_PX)).coerceIn(0f, 1f)
            put(item.index, proximity)
        }
    }
}
