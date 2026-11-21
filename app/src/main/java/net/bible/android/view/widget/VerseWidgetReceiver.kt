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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Provider of the Verse widget. Besides Glance's own updates it redraws every Verse widget at a period boundary
 * ([VerseWidgetScheduler]) and when the time or time zone changes (a day then starts at another instant).
 */
class VerseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VerseGlanceWidget()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        // #4176: the crash safe mode switched off androidx.startup (no WorkManager): Glance cannot draw.
        if (net.bible.android.control.report.StartupCrashGuard.safeModeWithoutProviders(context)) return
        when (intent.action) {
            // No DATE_CHANGED: it is not delivered to manifest receivers on API 26+; the period alarm covers midnight.
            VerseWidgetScheduler.ACTION_PERIOD, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                // goAsync() is null when the receiver is not run by a broadcast (a direct call, a test).
                val pending = goAsync()
                scope.launch {
                    try { glanceAppWidget.updateAll(context) } finally { pending?.finish() }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    /** The launcher restored widgets from a backup under new ids: bring their settings along. */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        VerseWidgetConfigStore.moveIds(context, oldWidgetIds, newWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach {
            VerseWidgetConfigStore.delete(context, it)
            VerseWidgetScheduler.cancel(context, it)
        }
    }
}
