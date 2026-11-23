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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import net.bible.sharedui.theme.LocalDisableAnimations
import kotlin.math.roundToInt

/** A book tap aligns the detail heading with the viewport, not just its nearest visible edge. */
internal class ProgressDetailScroll(val scrollState: ScrollState) {
    var request by mutableIntStateOf(0)
        private set
    var requestedBook by mutableStateOf<String?>(null)
        private set
    var headingLocation by mutableStateOf<Pair<String, Int>?>(null)
        private set

    fun request(bookId: String) {
        requestedBook = bookId
        request++ // Re-selecting the same book must scroll even if the model does not change.
    }

    fun complete() {
        requestedBook = null
    }

    fun headingModifier(bookId: String): Modifier = Modifier.onGloballyPositioned {
        headingLocation = bookId to it.positionInParent().y.roundToInt()
    }
}

@Composable
internal fun rememberProgressDetailScroll(detailBookId: String?): ProgressDetailScroll {
    val scrollState = rememberScrollState()
    val target = remember(scrollState) { ProgressDetailScroll(scrollState) }
    val disableAnimations = LocalDisableAnimations.current
    LaunchedEffect(target.request, detailBookId) {
        val book = target.requestedBook ?: return@LaunchedEffect
        if (book != detailBookId) return@LaunchedEffect
        // The controller loads asynchronously. Wait for the selected heading's actual layout,
        // and measure in content coordinates so a previous scroll cannot change the destination.
        val offset = snapshotFlow { target.headingLocation?.takeIf { it.first == book }?.second }
            .filterNotNull().first()
        // Complete in finally: a drag cancels the animated scroll, and a stale request must not scroll later.
        try {
            if (disableAnimations) scrollState.scrollTo(offset)
            else scrollState.animateScrollTo(offset)
        } finally {
            target.complete()
        }
    }
    return target
}
