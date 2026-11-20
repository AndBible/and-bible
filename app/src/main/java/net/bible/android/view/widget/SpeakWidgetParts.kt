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
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import net.bible.android.activity.R
import net.bible.android.control.widget.SpeakPhase
import net.bible.android.control.widget.SpeakWidgetState

/** Width at which a Speak strip gains its next feature set (2x1, 3x1, 4x1, 5x1), see the spec's catalogue. */
internal val SPEAK_BREAKPOINT_DP = listOf(110, 200, 290, 380)

/** Number of cells-wide a strip of [width] shows: 2..5. */
internal fun stripColumns(width: Dp): Int = SPEAK_BREAKPOINT_DP.indexOfLast { width >= it.dp }.coerceAtLeast(0) + 2

/** An action that opens the app the same way the launcher does (through the startup gate, see #887). */
@Composable
internal fun openAppAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(launchIntent(context))
}

private fun launchIntent(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)

@Composable
internal fun str(id: Int): String = LocalContext.current.getString(id)

/** The rounded widget card: background, system-like corner radius, content padding; a tap anywhere else opens [onClick]. */
@Composable
internal fun WidgetRoot(onClick: Action, padding: Dp = 12.dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp).clickable(onClick),
    ) {
        // Padding on its own box: children that fill the width then stop at the padding instead of the card's edge.
        Box(modifier = GlanceModifier.fillMaxSize().padding(padding), contentAlignment = Alignment.CenterStart) { content() }
    }
}

@Composable
internal fun WidgetText(
    text: String, size: Int = 12, bold: Boolean = false, dim: Boolean = false, maxLines: Int = 1,
    modifier: GlanceModifier = GlanceModifier,
) = Text(
    text = text,
    style = TextStyle(
        color = if (dim) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
    ),
    maxLines = maxLines,
    modifier = modifier,
)

@Composable
internal fun SpeakPlayButton(state: SpeakWidgetState, size: Dp) = PlayButton(
    playing = state.phase == SpeakPhase.SPEAKING,
    size = size,
    action = actionRunCallback<PlayPauseAction>(),
    contentDescription = str(if (state.phase == SpeakPhase.SPEAKING) R.string.pause else R.string.widget_play),
)

@Composable
internal fun SleepToggleButton(state: SpeakWidgetState, size: Dp) = WidgetIconButton(
    icon = R.drawable.ic_widget_moon,
    contentDescription = str(R.string.widget_toggle_sleep_timer),
    size = size,
    action = actionRunCallback<ToggleSleepTimerAction>(),
    active = state.sleepMinutesLeft != null,
)

@Composable internal fun PrevButton(size: Dp) =
    WidgetIconButton(R.drawable.ic_widget_prev, str(R.string.previous), size, actionRunCallback<PrevVerseAction>())
@Composable internal fun NextButton(size: Dp) =
    WidgetIconButton(R.drawable.ic_widget_next, str(R.string.next), size, actionRunCallback<NextVerseAction>())
@Composable internal fun RewindButton(size: Dp) =
    WidgetIconButton(R.drawable.ic_widget_rewind, str(R.string.rewind), size, actionRunCallback<RewindAction>())
@Composable internal fun ForwardButton(size: Dp) =
    WidgetIconButton(R.drawable.ic_widget_forward, str(R.string.forward), size, actionRunCallback<ForwardAction>())
@Composable internal fun StopButton(size: Dp) =
    WidgetIconButton(R.drawable.ic_widget_stop, str(R.string.stop), size, actionRunCallback<StopAction>())

@Composable
internal fun Gap(width: Dp = 6.dp) = Spacer(GlanceModifier.width(width))

/** The "data needs attention" card of #4112: no buttons, no database access; a tap opens the startup recovery question. */
@Composable
fun AttentionGlanceContent() {
    val context = LocalContext.current
    val recovery = Intent(context, net.bible.android.view.activity.StartupActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    WidgetRoot(actionStartActivity(recovery)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(R.drawable.ic_widget_speaker),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(24.dp),
            )
            Gap(10.dp)
            WidgetText(str(R.string.damaged_data_needs_attention), size = 13, maxLines = 3)
        }
    }
}
