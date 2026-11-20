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
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import net.bible.android.activity.R
import net.bible.android.control.widget.ListeningRow
import net.bible.android.control.widget.SpeakWidgetState
import net.bible.android.control.widget.WidgetStrings

/** Width below which a listening row shows only its reference and bar (2x2 cells). */
internal const val LISTENING_WIDE_DP = 180

/** The meta-line columns of a wide row; the same on every row so the bars line up whatever the names are. */
internal val TRANSLATION_COLUMN = 58.dp
internal val REPEAT_COLUMN = 16.dp

private val HEADER_HEIGHT = 28.dp
private val WIDE_ROW_HEIGHT = 44.dp
private val COMPACT_ROW_HEIGHT = 34.dp

/** The speak bookmarks as a list: tap a row to speak from it. */
class ListeningListGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(LISTENING_WIDE_DP.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideWidgetContent(context) { state, strings ->
        ListeningListContent(state, strings, LocalSize.current.width, LocalSize.current.height)
    }
}

/**
 * Header (speaker, name, count when wide) and as many rows as fit [height]. A plain column rather than a lazy list: the
 * list is short, a scrolling `RemoteViews` list cannot be rendered without its adapter service, and rows that do not
 * fit are simply left out (the app has the full list).
 */
@Composable
fun ListeningListContent(state: SpeakWidgetState, strings: WidgetStrings, width: Dp, height: Dp) {
    val compact = width < LISTENING_WIDE_DP.dp
    val rowHeight = if (compact) COMPACT_ROW_HEIGHT else WIDE_ROW_HEIGHT
    val fit = ((height - HEADER_HEIGHT - 24.dp).value / rowHeight.value).toInt().coerceAtLeast(1)
    WidgetRoot(openAppAction(), padding = 12.dp) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_speaker),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier.size(18.dp),
                )
                Gap(6.dp)
                Column(modifier = GlanceModifier.defaultWeight()) { WidgetText(strings.listeningListName, size = 13, bold = true) }
                if (!compact && state.listening.isNotEmpty()) WidgetText(state.listening.size.toString(), dim = true)
            }
            if (state.listening.isEmpty()) {
                Spacer(GlanceModifier.height(4.dp))
                WidgetText(strings.listeningListEmptyTitle, size = 13, bold = true)
                WidgetText(strings.listeningListEmptyText, dim = true, maxLines = if (compact) 4 else 5)
            } else {
                state.listening.take(fit).forEach { ListeningRowContent(it, compact) }
            }
        }
    }
}

/** One row: reference line, then (wide) translation | repeat marker | progress bar in fixed columns. */
@Composable
fun ListeningRowContent(row: ListeningRow, compact: Boolean) {
    val action = if (row.current) actionRunCallback<PlayPauseAction>()
    else actionRunCallback<SpeakFromBookmarkAction>(actionParametersOf(SpeakFromBookmarkAction.BOOKMARK_ID to row.bookmarkId))
    val base = GlanceModifier.fillMaxWidth().height(if (compact) COMPACT_ROW_HEIGHT else WIDE_ROW_HEIGHT)
        .cornerRadius(12.dp).padding(horizontal = 6.dp, vertical = 2.dp)
    Row(
        modifier = (if (row.current) base.background(GlanceTheme.colors.primaryContainer) else base).clickable(action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            // The pause marker sits on the reference line, so the bar below keeps the same start and length on every row.
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.defaultWeight()) { WidgetText(row.reference, size = 13, bold = true) }
                if (row.current) Image(
                    provider = ImageProvider(R.drawable.ic_widget_pause),
                    contentDescription = str(R.string.pause),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
            if (compact) {
                Spacer(GlanceModifier.height(3.dp))
                row.percent?.let { WidgetProgressBar(it / 100f) }
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = GlanceModifier.width(TRANSLATION_COLUMN)) { WidgetText(row.translation, dim = true) }
                    Box(modifier = GlanceModifier.width(REPEAT_COLUMN), contentAlignment = Alignment.Center) {
                        if (row.repeats) Image(
                            provider = ImageProvider(R.drawable.ic_widget_repeat),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
                            modifier = GlanceModifier.size(12.dp),
                        )
                    }
                    // The bar slot stays even without a bar (generic bookmarks), so every row has the same geometry.
                    val percent = row.percent
                    if (percent != null) WidgetProgressBar(percent / 100f, GlanceModifier.defaultWeight(), fillWidth = false)
                    else Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}
