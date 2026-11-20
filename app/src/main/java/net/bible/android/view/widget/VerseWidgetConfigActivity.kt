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

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.control.widget.VerseCandidates
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.control.widget.VerseWidgetSources
import net.bible.service.common.CommonUtils
import net.bible.sharedui.AbAppTheme
import org.koin.mp.KoinPlatform
import kotlin.random.Random

/**
 * Configuration of a Verse widget: opened by the launcher when the widget is placed (and, API 31+, when it is
 * reconfigured) and by a tap on a widget that has no verse to show. A translucent host for [VerseWidgetConfigScreen].
 */
class VerseWidgetConfigActivity : ComponentActivity() {
    private var options by mutableStateOf<VerseConfigOptions?>(null)
    private var existing: VerseWidgetConfig? = null
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out must leave the widget unplaced.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        existing = VerseWidgetConfigStore.load(this, appWidgetId)
        setContent {
            AbAppTheme {
                options?.let { o ->
                    VerseWidgetConfigScreen(
                        options = o, existing = existing, newSeed = Random.nextLong(),
                        onSave = ::save, onCancel = { finish() },
                    )
                }
            }
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { CommonUtils.initializeAppForHeadlessEntry() }
            if (!ok) { finish(); return@launch }
            options = withContext(Dispatchers.IO) {
                val koin = KoinPlatform.getKoin()
                val candidates = koin.get<VerseCandidates>()
                val default = candidates.defaultBible()
                VerseConfigOptions(
                    labels = candidates.labels(),
                    bibles = candidates.bibles(),
                    defaultBible = default,
                    hasReadingPlan = default != null && koin.get<VerseWidgetSources>().readingPlanPassage(default) != null,
                )
            }
        }
    }

    private fun save(config: VerseWidgetConfig) {
        VerseWidgetConfigStore.save(this, appWidgetId, config)
        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val app = applicationContext
        val id = appWidgetId
        // Draw it at once; the activity may be gone before the update finishes, so use the app scope.
        KoinPlatform.getKoin().get<net.bible.sharedcore.platform.AppCoroutineScope>().launch {
            runCatching { VerseGlanceWidget().update(app, GlanceAppWidgetManager(app).getGlanceIdBy(id)) }
        }
        finish()
    }
}
