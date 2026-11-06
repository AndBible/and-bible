package net.bible.sharedui.reading.activity

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.bible.android.control.activity.ActivityState
import net.bible.sharedui.reading.ToolbarButtonWidth
import net.bible.sharedui.reading.ToolbarIconButton
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.LocalDisableAnimations
import net.bible.sharedui.theme.isPureMonochrome

private val RingSize = 38.dp
private val RingStroke = 2.dp
private const val IndeterminateSweep = 90f
private const val SpinMillis = 1100

/**
 * The toolbar's overflow ("three dots") button, with the activity indicators of #4007: a thin progress ring
 * around the icon while background work runs (determinate when exactly one running item reports a percent,
 * otherwise indeterminate) and a count badge for unread failures.
 *
 * With [activity] `null` this is exactly the plain toolbar icon button, so the preference-off rendering is
 * unchanged. The ring and badge are non-interactive overlays with their own semantics nodes, so the button's
 * own label and click action are untouched.
 */
@Composable
fun ActivityOverflowButton(
    icon: Painter,
    contentDescription: String,
    activity: ActivityMenuState?,
    onClick: () -> Unit,
) {
    if (activity == null) {
        ToolbarIconButton(icon = icon, contentDescription = contentDescription, onClick = onClick)
        return
    }
    val strings = LocalStrings.current
    val running = activity.items.filter { it.state == ActivityState.RUNNING }
    val single = running.singleOrNull()?.progress
    Box(Modifier.fillMaxHeight().width(ToolbarButtonWidth), contentAlignment = Alignment.Center) {
        ToolbarIconButton(icon = icon, contentDescription = contentDescription, onClick = onClick)
        if (running.isNotEmpty()) {
            ActivityRing(percent = single, description = strings.activityRunningDescription)
        }
        if (activity.unreadFailures > 0) {
            val mono = isPureMonochrome()
            Badge(
                containerColor = if (mono) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                contentColor = if (mono) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 4.dp)
                    .testTag(ACTIVITY_BADGE_TAG)
                    .semantics(mergeDescendants = true) {
                        this.contentDescription = strings.activityFailedBadge(activity.unreadFailures)
                    },
            ) {
                Text(activity.unreadFailures.toString(), fontSize = 10.sp)
            }
        }
    }
}

/**
 * Thin ring drawn around the icon. [percent] null = indeterminate (a rotating arc, or a static arc when
 * animations are off or in inspection mode so captures are deterministic).
 */
@Composable
private fun ActivityRing(percent: Int?, description: String) {
    val color = if (isPureMonochrome()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    val animate = !LocalDisableAnimations.current && !LocalInspectionMode.current
    val rotation = if (percent == null && animate) {
        rememberInfiniteTransition(label = "activityRing").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(SpinMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "activityRingRotation",
        ).value
    } else 0f
    Canvas(
        Modifier
            .size(RingSize)
            .testTag(ACTIVITY_RING_TAG)
            .semantics {
                this.contentDescription = description
                progressBarRangeInfo = if (percent == null) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(percent.coerceIn(0, 100) / 100f, 0f..1f)
                }
            },
    ) {
        val stroke = RingStroke.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        if (percent == null) {
            drawArc(color, startAngle = -90f + rotation, sweepAngle = IndeterminateSweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = style)
        } else {
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, Offset(inset, inset), arcSize, style = style)
            drawArc(color, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize, style = style)
        }
    }
}
