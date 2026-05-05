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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.android.activity.R
import net.bible.android.control.passagefinder.PassageFinderDataSource

/**
 * Semi-transparent floating bubble that shows the current selection reference.
 *
 * Content is progressive and crossfades between levels:
 * - BOOK level: full book name (e.g. "Genesis")
 * - CHAPTER level: abbreviated reference (e.g. "Gen 1")
 * - VERSE level: abbreviated reference + verse (e.g. "Gen 1:1") with verse text below
 *
 * The bubble is hidden until the user first interacts with the widget (controlled by [visible]).
 * All animations respect the [disableAnimations] setting for accessibility.
 */
@Composable
fun PreviewBubble(
    modifier: Modifier = Modifier,
    books: List<PassageFinderDataSource.BookInfo>,
    selectedBookIndex: Int,
    selectedChapter: Int,
    selectedVerse: Int,
    currentLevel: NavigationLevel,
    verseText: String?,
    visible: Boolean,
    disableAnimations: Boolean,
    onTap: () -> Unit,
) {
    val bubbleShape = RoundedCornerShape(24.dp)

    val book = books.getOrNull(selectedBookIndex)
    val shortName = book?.shortName ?: ""
    val longName = book?.longName ?: ""
    val currentReference = when (currentLevel) {
        NavigationLevel.BOOK -> longName
        NavigationLevel.CHAPTER -> "$shortName $selectedChapter"
        NavigationLevel.VERSE -> "$shortName $selectedChapter:$selectedVerse"
    }
    val confirmDescription = stringResource(R.string.passage_finder_a11y_go_to, currentReference)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(if (disableAnimations) 0 else 200)),
        exit = fadeOut(tween(if (disableAnimations) 0 else 150)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    shadowElevation = 8f
                    shape = bubbleShape
                    clip = false
                }
                .semantics {
                    contentDescription = confirmDescription
                    role = Role.Button
                }
                .clickable(onClick = onTap)
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = bubbleShape,
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {

            AnimatedContent(
                targetState = currentLevel,
                transitionSpec = {
                    fadeIn(tween(if (disableAnimations) 0 else 150)) togetherWith
                        fadeOut(tween(if (disableAnimations) 0 else 150))
                },
                contentAlignment = Alignment.Center,
                label = "bubbleLevelTransition",
            ) { targetLevel ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val title = when (targetLevel) {
                        NavigationLevel.BOOK -> longName
                        NavigationLevel.CHAPTER -> "$shortName $selectedChapter"
                        NavigationLevel.VERSE -> "$shortName $selectedChapter:$selectedVerse"
                    }

                    BasicText(
                        text = title,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )

                    if (targetLevel == NavigationLevel.VERSE) {
                        AnimatedVisibility(
                            visible = !verseText.isNullOrBlank(),
                            enter = fadeIn(tween(if (disableAnimations) 0 else 200)),
                        ) {
                            BasicText(
                                text = verseText ?: "",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Justify,
                                ),
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
