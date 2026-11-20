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
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import net.bible.android.control.widget.SpeakPhase
import net.bible.android.control.widget.SpeakWidgetState
import net.bible.android.control.widget.WidgetStrings
import net.bible.android.activity.R

/**
 * The Speak widget: one strip, 2x1 to 5x1 cells. The legacy Small, Middle and Large providers render it too.
 * Content by width: [stripColumns].
 */
class SpeakGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(SPEAK_BREAKPOINT_DP.map { DpSize(it.dp, 40.dp) }.toSet())

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideWidgetContent(context) { state, strings ->
        SpeakStripContent(state, strings, LocalSize.current.width)
    }
}

/** What a Speak strip of [width] shows. */
@Composable
fun SpeakStripContent(state: SpeakWidgetState, strings: WidgetStrings, width: Dp) {
    val columns = stripColumns(width)
    val speaking = state.phase == SpeakPhase.SPEAKING
    val percentFraction = (state.percent ?: 0) / 100f
    WidgetRoot(openAppAction(), padding = 10.dp) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SpeakPlayButton(state, if (columns >= 4) 52.dp else 44.dp)
            Gap(8.dp)
            Column(modifier = GlanceModifier.defaultWeight()) {
                when (columns) {
                    2 -> {
                        WidgetText(state.reference ?: strings.continueListening, size = 14, bold = true)
                        WidgetText(compactSubline(state, strings, speaking), dim = true)
                    }
                    3 -> {
                        WidgetText(state.reference ?: strings.continueListening, size = 14, bold = true)
                        Spacer(GlanceModifier.height(4.dp))
                        if (state.percent != null) WidgetProgressBar(percentFraction)
                        else WidgetText(compactSubline(state, strings, speaking), dim = true)
                    }
                    else -> {
                        if (columns == 5) {
                            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    WidgetText(state.statusLine, size = 13, bold = true)
                                }
                                state.sleepMinutesLeft?.let { SleepChip(it) }
                            }
                        } else {
                            WidgetText(state.title ?: state.reference ?: strings.continueListening, size = 13, bold = true)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RewindButton(30.dp); PrevButton(30.dp); StopButton(30.dp); NextButton(30.dp); ForwardButton(30.dp)
                            SleepToggleButton(state, 30.dp)
                        }
                        if (state.percent != null) WidgetProgressBar(percentFraction)
                    }
                }
            }
            if (columns == 3) {
                Gap(4.dp)
                PrevButton(30.dp); NextButton(30.dp); SleepToggleButton(state, 30.dp)
            }
        }
    }
}

/** "34 % · WEB" while speaking, "Paused" when paused, otherwise the translation of the position it would resume from. */
private fun compactSubline(state: SpeakWidgetState, strings: WidgetStrings, speaking: Boolean): String = when {
    speaking -> listOfNotNull(state.percent?.let { "$it %" }, state.translation).joinToString(" · ")
    state.phase == SpeakPhase.PAUSED -> strings.paused
    else -> state.translation ?: ""
}
