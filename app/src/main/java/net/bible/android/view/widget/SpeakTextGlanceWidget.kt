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
package net.bible.android.view.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import net.bible.android.control.widget.SpeakWidgetState
import net.bible.android.control.widget.WidgetStrings

/** Width from which the text widget shows its status line (4 cells), and height from which it is the 3-row layout. */
internal const val TEXT_WIDE_DP = 290
internal const val TEXT_TALL_DP = 240

/**
 * Speak with text: the verse being read with the transport controls. 3x2 (title, verse, prev / play / next),
 * 4x2 (+ status line), 4x3 and larger (+ next verse dimmed, progress bar and the full control row).
 */
class SpeakTextGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 110.dp), DpSize(TEXT_WIDE_DP.dp, 110.dp), DpSize(TEXT_WIDE_DP.dp, TEXT_TALL_DP.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideWidgetContent(context) { state, strings ->
        SpeakTextContent(state, strings, LocalSize.current.width, LocalSize.current.height)
    }
}

@Composable
fun SpeakTextContent(state: SpeakWidgetState, strings: WidgetStrings, width: Dp, height: Dp) {
    val wide = width >= TEXT_WIDE_DP.dp
    val tall = height >= TEXT_TALL_DP.dp
    WidgetRoot(openAppAction()) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WidgetText(state.title ?: strings.appName, size = 13, bold = true)
                }
                state.sleepMinutesLeft?.let { SleepChip(it) }
            }
            Spacer(GlanceModifier.height(4.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                // Nothing spoken yet (stopped): say where Speak would continue, instead of an empty box.
                WidgetText(state.verseText.ifEmpty { state.statusLine }, size = 15, maxLines = if (tall) 8 else 4)
                if (tall) state.nextVerseText?.let {
                    Spacer(GlanceModifier.height(4.dp))
                    WidgetText(it, size = 13, dim = true, maxLines = 3)
                }
            }
            if (wide && state.verseText.isNotEmpty()) WidgetText(state.statusLine, dim = true)
            if (tall && state.percent != null) {
                Spacer(GlanceModifier.height(4.dp))
                WidgetProgressBar((state.percent ?: 0) / 100f)
            }
            Spacer(GlanceModifier.height(4.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (tall) { SleepToggleButton(state, 32.dp); RewindButton(32.dp) }
                PrevButton(32.dp); Gap(4.dp); SpeakPlayButton(state, 44.dp); Gap(4.dp); NextButton(32.dp)
                if (tall) { ForwardButton(32.dp); StopButton(32.dp) }
            }
        }
    }
}
