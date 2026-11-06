package net.bible.sharedui.reading.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityState
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.theme.isPureMonochrome

/** Most rows the overflow menu shows; the rest are behind "Show all". */
internal const val MENU_ACTIVITY_ROWS = 3

/**
 * The activity section at the top of the overflow menu (#4007): up to [MENU_ACTIVITY_ROWS] rows (running first,
 * then failures, then recent successes; newest first within each), each a title with a slim progress bar or the
 * failure message, then "Show all (N)" when more exist or any failure exists, then a divider.
 * Renders nothing when there is nothing to show, so the menu is then exactly the normal one.
 */
@Composable
fun ActivityMenuSection(activity: ActivityMenuState) {
    // Stable sort: the incoming order (newest first) is kept within each state.
    val shown = activity.items
        .filter { it.state != ActivityState.CANCELLED }
        .sortedBy { it.state.menuRank() }
    if (shown.isEmpty()) return
    val strings = LocalStrings.current
    Column(Modifier.testTag(ACTIVITY_SECTION_TAG)) {
        shown.take(MENU_ACTIVITY_ROWS).forEach { ActivityRow(it) }
        if (shown.size > MENU_ACTIVITY_ROWS || shown.any { it.state == ActivityState.FAILED }) {
            AbMenuItem(text = strings.activityShowAll(shown.size), onClick = activity.onShowAll)
        }
        HorizontalDivider()
    }
}

private fun ActivityState.menuRank(): Int = when (this) {
    ActivityState.RUNNING -> 0
    ActivityState.FAILED -> 1
    else -> 2
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    val mono = isPureMonochrome()
    val progress = item.progress
    val message = item.message
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(ACTIVITY_ROW_TAG),
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            item.state == ActivityState.RUNNING -> {
                val barColor = if (mono) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                val trackColor = if (mono) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.surfaceVariant
                val barModifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = barModifier, color = barColor, trackColor = trackColor,
                    )
                } else {
                    LinearProgressIndicator(modifier = barModifier, color = barColor, trackColor = trackColor)
                }
            }
            message != null -> Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.state == ActivityState.FAILED && !mono) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
