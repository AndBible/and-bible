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

package net.bible.sharedui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import net.bible.sharedui.theme.LocalDisplayColorMode
import net.bible.service.common.DisplayColorMode

/**
 * A full-size modal blocking spinner — the Compose, KMP replacement for the Android `Hourglass`
 * (`net.bible.android.view.util.Hourglass`). Draws a dimming scrim that swallows all touches
 * (`clickable {}` with no indication) so the underlying screen is inert while a blocking async flow
 * runs, plus a centered spinner and an optional [message].
 *
 * Under inspection (Compose previews / Roborazzi goldens) it renders a deterministic determinate
 * frame instead of the animated indeterminate spinner — same reason as [AbLoadingIndicator]:
 * Roborazzi's `inspectionMode(true)` does NOT freeze Compose `InfiniteTransition`, so a real
 * indeterminate `CircularProgressIndicator` would capture a bistable frame → flaky golden.
 * `LocalInspectionMode` is FALSE in production, so this branch is inert at runtime.
 */
@Composable
fun AbLoadingOverlay(message: String? = null, modifier: Modifier = Modifier) {
    val mono = LocalDisplayColorMode.current == DisplayColorMode.MONOCHROME
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (mono) Color.Transparent else Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* swallow touches while loading */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (LocalInspectionMode.current) {
                // The pure paper track is invisible at zero; show a stable ink arc in previews.
                CircularProgressIndicator(progress = { if (mono) 0.5f else 0f })
            } else {
                CircularProgressIndicator()
            }
            if (message != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
