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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.widget.SpeakWidgetState
import net.bible.android.control.widget.SpeakWidgetStateProducer
import net.bible.android.control.widget.WidgetStrings
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.AppCoroutineScope
import org.koin.mp.KoinPlatform

/** Whether the app opened for a widget draw (#4112: false means damaged data) and whether the monochrome setting is on. */
internal data class WidgetEntry(val appOpens: Boolean, val mono: Boolean)

/** Opens the app headlessly (on IO) and reads the colour setting; never throws. */
internal suspend fun widgetEntry(): WidgetEntry = withContext(Dispatchers.IO) {
    val ok = CommonUtils.initializeAppForHeadlessEntry()
    WidgetEntry(ok, ok && runCatching { CommonUtils.settings.monochromeMode }.getOrDefault(false))
}

/**
 * Draws a speak-state widget: the attention card when the app's data is damaged, otherwise [content] from the live
 * [SpeakWidgetStateProducer.state].
 */
internal suspend fun GlanceAppWidget.provideWidgetContent(
    context: Context,
    content: @Composable (SpeakWidgetState, WidgetStrings) -> Unit,
) {
    val entry = widgetEntry()
    if (entry.appOpens) WidgetUpdater.start(context)
    provideContent {
        AbWidgetTheme(entry.mono) {
            if (!entry.appOpens) AttentionGlanceContent() else {
                val koin = KoinPlatform.getKoin()
                val state by koin.get<SpeakWidgetStateProducer>().state.collectAsState()
                content(state, koin.get())
            }
        }
    }
}

/**
 * Keeps the speak-state widgets (Speak, Speak with text, Listening list) in step with the speak services: starts the
 * shared [SpeakWidgetStateProducer] and redraws them whenever its state changes. The producer already coalesces bursts
 * (500 ms) and never emits per word.
 */
object WidgetUpdater {
    private var job: Job? = null

    /** Starts the producer and the redraw loop; idempotent. */
    @Synchronized
    fun start(context: Context) {
        val koin = KoinPlatform.getKoin()
        val producer = koin.get<SpeakWidgetStateProducer>()
        producer.start()
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = koin.get<AppCoroutineScope>().launch {
            producer.state.collect { runCatching { refreshSpeakWidgets(app) } }
        }
    }

    /** Stops following the speak services; the widgets keep their last drawing. */
    @Synchronized
    fun stop() {
        job?.cancel(); job = null
        runCatching { KoinPlatform.getKoin().get<SpeakWidgetStateProducer>().stop() }
    }

    /** Redraws every widget with the current resources, e.g. after an in-app language change. */
    fun refreshForLocale(context: Context) {
        val app = context.applicationContext
        val koin = KoinPlatform.getKoin()
        koin.get<SpeakWidgetStateProducer>().refreshForLocale()
        koin.get<AppCoroutineScope>().launch { runCatching { refreshAll(app) } }
    }

    private suspend fun refreshSpeakWidgets(context: Context) {
        SpeakGlanceWidget().updateAll(context)
        SpeakTextGlanceWidget().updateAll(context)
        ListeningListGlanceWidget().updateAll(context)
    }

    /** Redraws all four widgets. */
    suspend fun refreshAll(context: Context) {
        refreshSpeakWidgets(context)
        VerseGlanceWidget().updateAll(context)
    }
}
