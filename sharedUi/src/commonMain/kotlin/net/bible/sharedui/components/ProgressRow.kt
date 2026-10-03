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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp

/**
 * An indeterminate linear progress bar for loading states.
 *
 * Under inspection (Compose previews / Roborazzi golden tests) it renders a deterministic,
 * frozen determinate frame instead of the animated indeterminate bar. This is required because
 * Roborazzi's `inspectionMode(true)` sets `LocalInspectionMode` but does NOT freeze Compose
 * `InfiniteTransition`, so a real indeterminate `LinearProgressIndicator` captures a bistable
 * animation frame → non-deterministic golden verify. `LocalInspectionMode` is FALSE in
 * production, so this branch is inert at runtime (zero behavior change).
 */
@Composable
fun AbLoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        LinearProgressIndicator(progress = { 0f }, modifier = modifier)
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

/** A label + linear progress bar; indeterminate when [indeterminate], else determinate at [percent]%. */
@Composable
fun ProgressRow(label: String, percent: Int, indeterminate: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        if (indeterminate) {
            AbLoadingIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
}
