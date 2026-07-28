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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A reusable colour-scale legend: a leading [label] followed by a row of equal-width coloured
 * bands with a row of step labels beneath. Mirrors the classic `ReadingProgressActivity`
 * `showCountScale`/`showBookPercentScale` legend layout (`app/src/main/java/net/bible/android/
 * view/activity/progress/ReadingProgressActivity.kt`), used for both the chapter read-count scale
 * and the book read-percent scale.
 *
 * Colours are supplied by the caller via [stepColor] (typically backed by the `ReadingProgressPalette.kt`
 * helpers, e.g. [countHeatColor]/[bookProgressColor]), so this component itself knows nothing about the
 * palette or e-ink degradation — that flows through automatically because the caller's colour lambda
 * is `@Composable` and reads the ambient display-colour-mode.
 */
@Composable
fun AbColorScaleLegend(
    label: String,
    steps: List<Int>,
    stepColor: @Composable (step: Int) -> Color,
    stepLabel: (step: Int) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            // Classic renders this label as a bare 10sp TextView (ReadingProgressActivity.kt:876-882),
            // i.e. ~1.2x natural leading. Overriding fontSize alone inherited bodyLarge's 24sp
            // lineHeight, which made the two-line "Percent\nRead" label read as two tall rows
            // (A/B batch 2 F3).
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                steps.forEach { step ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(9.dp)
                            .background(stepColor(step)),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                steps.forEach { step ->
                    Text(
                        text = stepLabel(step),
                        // Classic: bare 9sp TextView (ReadingProgressActivity.kt:855-861).
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
