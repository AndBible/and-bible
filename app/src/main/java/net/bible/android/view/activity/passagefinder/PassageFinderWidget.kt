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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.bible.android.activity.R
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import kotlin.math.abs

/** Full height for the chapter strip. */
private val CHAPTER_STRIP_FULL_HEIGHT = 80.dp
/** Full height for the verse strip. */
private val VERSE_STRIP_FULL_HEIGHT = 60.dp
/** Fixed height for the book strip container — prevents varying spine heights from shifting strips above.
 *  Matches BookStrip.SPINE_HEIGHT (123.dp), the tallest spine size at center of the lens. */
private val BOOK_STRIP_HEIGHT = 123.dp
/** Spacing between strip levels. */
private val STRIP_SPACING = 23.dp

/**
 * Root composable for the PassageFinder overlay widget.
 *
 * Stacks three strip levels (books, chapters, verses) vertically from the bottom.
 * A single vertical gesture detector at the Column level intercepts swipe-up/down
 * to transition between navigation levels, while horizontal scrolling within each
 * strip's LazyRow is unaffected.
 *
 * Gesture detection: vertical motion locks when dy > dx (below diagonal),
 * horizontal locks when dx > dy. This makes upward swipes much easier to trigger.
 *
 * Localization: All user-visible text is data-driven (book names from JSword,
 * chapter/verse numbers are integers, verse text from Bible translation).
 * Settings strings are in strings.xml. No hardcoded UI labels exist.
 */
@Composable
fun PassageFinderWidget(
    viewModel: PassageFinderViewModel,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val verseText by viewModel.previewVerseText.collectAsState()
    val view = LocalView.current
    val hapticController = remember(view) { HapticController(view) }
    val disableAnimations = CommonUtils.settings.disableAnimations
    val isDarkTheme = ScreenSettings.nightMode
    val isMonochrome = CommonUtils.settings.monochromeMode
    val currentLevel = uiState.currentLevel

    val animDuration = if (disableAnimations) 0 else 300

    // Visibility model:
    //   1. While the user is actively scrolling a less-specific strip, hide the more-specific ones:
    //      - chapter strip hides while user is scrolling books
    //      - verse strip hides while user is scrolling books OR chapters
    //   2. Progressive reveal: after the user picks a new book, the verse strip stays
    //      hidden until they pick a chapter — so we don't pre-commit to verse 1 of the
    //      new book. Initially `verseRevealed = true` because the widget opens with a
    //      verse already selected.
    // Programmatic scrolls (initial centering, boundary sync) don't trigger hide — see
    // the user-scroll filter in BookStrip / ChapterStrip.
    var bookScrolling by remember { mutableStateOf(false) }
    var chapterScrolling by remember { mutableStateOf(false) }
    var verseRevealed by remember { mutableStateOf(true) }

    // Reset progressive-reveal state every time the widget opens — a verse is already
    // selected at that point, so all three strips should be visible.
    LaunchedEffect(uiState.visible) {
        if (uiState.visible) {
            verseRevealed = true
            bookScrolling = false
            chapterScrolling = false
        }
    }

    val chapterStripHeight by animateDpAsState(
        targetValue = if (bookScrolling) 0.dp else CHAPTER_STRIP_FULL_HEIGHT,
        animationSpec = if (disableAnimations) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 300f,
        ),
        label = "chapterStripHeight",
    )
    val chapterStripAlpha by animateFloatAsState(
        targetValue = if (bookScrolling) 0f else 1f,
        animationSpec = if (disableAnimations) snap() else spring(stiffness = 300f),
        label = "chapterStripAlpha",
    )

    val hideVerse = bookScrolling || chapterScrolling || !verseRevealed
    val verseStripHeight by animateDpAsState(
        targetValue = if (hideVerse) 0.dp else VERSE_STRIP_FULL_HEIGHT,
        animationSpec = if (disableAnimations) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 300f,
        ),
        label = "verseStripHeight",
    )
    val verseStripAlpha by animateFloatAsState(
        targetValue = if (hideVerse) 0f else 1f,
        animationSpec = if (disableAnimations) snap() else spring(stiffness = 300f),
        label = "verseStripAlpha",
    )

    // Dim overlay — tap-to-dismiss covers full screen, but dark scrim only covers bottom strip area
    val dismissLabel = stringResource(R.string.passage_finder_a11y_dismiss)
    AnimatedVisibility(
        visible = uiState.visible,
        enter = fadeIn(animationSpec = tween(animDuration)),
        exit = fadeOut(animationSpec = tween(animDuration)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = dismissLabel
                    role = Role.Button
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    viewModel.dismiss()
                    onDismiss()
                }
        ) {}
    }

    // Opaque panel color behind strips — themed for dark/light/monochrome
    val panelColor = if (isDarkTheme || isMonochrome) Color(0xF0121212) else Color(0xF0F0F0F0)

    // Strip stack
    AnimatedVisibility(
        visible = uiState.visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(animDuration),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(animDuration),
        ),
    ) {
        val density = LocalDensity.current
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Fixed-height gradient background — independent of Column content height
            // so it stays consistent across BOOK, CHAPTER, and VERSE levels
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .height(420.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.15f to panelColor,
                                1f to panelColor,
                            ),
                        )
                    ),
            )

            // Strip Column (renders on top of gradient background)
            val widgetLabel = stringResource(R.string.passage_finder_a11y_label)
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .padding(bottom = 16.dp)
                    .semantics(mergeDescendants = false) {
                        contentDescription = widgetLabel
                    }
                    .pointerInput(currentLevel) {
                        val drillThresholdPx = with(density) { 50.dp.toPx() }
                        val touchSlopPx = viewConfiguration.touchSlop

                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var locked: Boolean? = null
                            var cumulativeVertical = 0f

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) break

                                val delta = change.positionChange()

                                // Direction locking for drill gesture
                                if (locked == null && (abs(delta.x) > touchSlopPx || abs(delta.y) > touchSlopPx)) {
                                    locked = abs(delta.y) > abs(delta.x)
                                }

                                if (locked == true) {
                                    change.consume()
                                    cumulativeVertical += delta.y

                                    if (cumulativeVertical < -drillThresholdPx) {
                                        viewModel.drillDown()
                                        cumulativeVertical = 0f
                                        locked = null
                                    } else if (cumulativeVertical > drillThresholdPx) {
                                        if (!viewModel.drillUp()) {
                                            viewModel.dismiss()
                                            onDismiss()
                                        }
                                        cumulativeVertical = 0f
                                        locked = null
                                    }
                                }
                            }
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(STRIP_SPACING, Alignment.Bottom),
            ) {
                // Preview bubble — part of the Column so it flows with the strips.
                // Visible when showPreview is set (chapter/verse scroll has occurred) and
                // the user is not currently scrolling books. Renders the full reference
                // plus verse text; book/chapter-only previews aren't useful.
                PreviewBubble(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = STRIP_SPACING)
                        .widthIn(max = 340.dp),
                    books = uiState.books,
                    selectedBookIndex = uiState.selectedBookIndex,
                    selectedChapter = uiState.selectedChapter,
                    selectedVerse = uiState.selectedVerse,
                    verseText = verseText,
                    visible = uiState.showPreview && !bookScrolling,
                    disableAnimations = disableAnimations,
                    onTap = {
                        // Don't call onDismiss() here: the launcher's selectionConfirmed
                        // collector handles navigation and then calls hide() itself.
                        // Cancelling navigationJob early via onDismiss() can race the
                        // emission and silently drop the navigation.
                        viewModel.confirmSelection()
                    },
                )

                // Verse strip — hidden while user is scrolling books or chapters
                VerseStrip(
                    modifier = Modifier
                        .height(verseStripHeight)
                        .graphicsLayer { alpha = verseStripAlpha },
                    books = uiState.books,
                    selectedBookIndex = uiState.selectedBookIndex,
                    selectedChapter = uiState.selectedChapter,
                    selectedVerse = uiState.selectedVerse,
                    verseCounts = { bookIdx, chapter -> viewModel.getVerseCount(bookIdx, chapter) },
                    onVerseSelected = { verse ->
                        if (verse != uiState.selectedVerse) {
                            hapticController.onVerseBoundary()
                        }
                        viewModel.onVerseSelected(verse)
                    },
                    disableAnimations = disableAnimations,
                    onVerseTapped = { verse ->
                        if (verse == uiState.selectedVerse) {
                            // Already centered -- confirm selection. Don't call onDismiss():
                            // the launcher's selectionConfirmed collector navigates and then
                            // hides the view; cancelling navigationJob early would race the
                            // SharedFlow emission and drop the navigation.
                            viewModel.confirmSelection()
                        }
                        // Otherwise: onVerseSelected already called by VerseStrip onClick,
                        // which triggers scroll-to-center via the LaunchedEffect
                    },
                )

                // Chapter strip — hidden while user is scrolling books
                ChapterStrip(
                    modifier = Modifier
                        .height(chapterStripHeight)
                        .graphicsLayer { alpha = chapterStripAlpha },
                    books = uiState.books,
                    selectedBookIndex = uiState.selectedBookIndex,
                    selectedChapter = uiState.selectedChapter,
                    chapterCounts = { bookIdx -> viewModel.getChapterCount(bookIdx) },
                    onChapterSelected = { chapter ->
                        if (chapter != uiState.selectedChapter) {
                            hapticController.onChapterBoundary()
                        }
                        viewModel.onChapterSelected(chapter)
                        // User has now picked a chapter — reveal verse strip
                        verseRevealed = true
                    },
                    onChapterTapped = { chapter ->
                        viewModel.onChapterSelected(chapter)
                        verseRevealed = true
                        viewModel.drillDown()
                    },
                    onUserScrollChanged = { chapterScrolling = it },
                    disableAnimations = disableAnimations,
                )

                // Book strip in fixed-height container so varying spine widths
                // don't shift the chapter/verse strips above. Always fully visible —
                // the user needs the book strip to navigate even while scrolling books.
                Box(
                    modifier = Modifier
                        .height(BOOK_STRIP_HEIGHT)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    BookStrip(
                        books = uiState.books,
                        openBookIndex = uiState.openBookIndex,
                        selectedBookIndex = uiState.selectedBookIndex,
                        initialScrollIndex = uiState.selectedBookIndex,
                        chapterCounts = { bookIdx -> viewModel.getChapterCount(bookIdx) },
                        onSelectedIndexChanged = { index ->
                            if (index != uiState.selectedBookIndex) {
                                hapticController.onBookBoundary()
                                // Book actually changed — hide verse strip until user
                                // picks a chapter for the new book.
                                verseRevealed = false
                            }
                            viewModel.onBookSelected(index)
                        },
                        onBookTapped = { index ->
                            if (index != uiState.selectedBookIndex) {
                                verseRevealed = false
                            }
                            viewModel.onBookSelected(index)
                            viewModel.drillDown()
                        },
                        onUserScrollChanged = { bookScrolling = it },
                        syncFromBoundary = currentLevel != NavigationLevel.BOOK,
                        isMonochrome = isMonochrome,
                        isDarkTheme = isDarkTheme,
                    )
                }
            }
        }
    }
}
