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

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.control.widget.LocalZone
import net.bible.android.control.widget.VerseCandidates
import net.bible.android.control.widget.VerseChangeFrequency
import net.bible.android.control.widget.VerseSelector
import net.bible.android.control.widget.VerseSource
import net.bible.android.control.widget.VerseWidgetConfig
import net.bible.android.control.widget.VerseWidgetContent
import net.bible.android.control.widget.WidgetStrings
import net.bible.android.control.speak.SpeakControl
import net.bible.android.common.toV11n
import net.bible.service.common.CommonUtils
import net.bible.sharedui.reading.bridge.asReadingSpeaker
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.versification.system.Versifications
import org.crosswire.jsword.book.Books
import org.koin.mp.KoinPlatform
import kotlin.random.Random

/**
 * The Verse widget: one verse from a label, a random part of the Bible or today's reading, changing daily, every
 * three hours or only on shuffle. 2x2, 4x2 and 4x3+ layouts. The verse of a period is stable ([VerseSelector]).
 */
class VerseGlanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val entry = widgetEntry()
        val koin = KoinPlatform.getKoin()
        val strings = koin.get<WidgetStrings>()
        var config: VerseWidgetConfig? = null
        var content: VerseWidgetContent? = null
        if (entry.appOpens) {
            // A widget that lost its configuration (restore, cleared data) gets a default instead of staying empty.
            config = VerseWidgetConfigStore.load(context, appWidgetId) ?: defaultVerseWidgetConfig().also {
                VerseWidgetConfigStore.save(context, appWidgetId, it)
            }
            val now = System.currentTimeMillis()
            val zone = koin.get<LocalZone>()
            content = withContext(Dispatchers.IO) { koin.get<VerseCandidates>().content(config, now, zone) }
            VerseWidgetScheduler.schedule(context, appWidgetId, VerseSelector.nextPeriodStart(now, zone, config.frequency))
        }
        provideContent {
            AbWidgetTheme(entry.mono) {
                if (!entry.appOpens || config == null || content == null) AttentionGlanceContent()
                else VerseWidgetBody(content, config, strings, appWidgetId, LocalSize.current.width, LocalSize.current.height)
            }
        }
    }
}

/** A fresh configuration: a random verse from the whole Bible, daily, with its own seed. */
fun defaultVerseWidgetConfig() = VerseWidgetConfig(
    source = VerseSource.Random(VerseSource.RandomPart.WHOLE_BIBLE),
    frequency = VerseChangeFrequency.DAILY,
    seed = Random.nextLong(),
)

/** The intent that opens the configuration of [appWidgetId]; unique per widget so the pending intents do not collide. */
fun verseConfigIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, VerseWidgetConfigActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setData(Uri.parse("andbible-widget://verse-config/$appWidgetId"))

private val VERSE_ID = ActionParameters.Key<Int>("appWidgetId")

/** Draws [content] for the widget [appWidgetId]; a Verse or the reason there is none. */
@Composable
fun VerseWidgetBody(
    content: VerseWidgetContent,
    config: VerseWidgetConfig,
    strings: WidgetStrings,
    appWidgetId: Int,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    when (content) {
        is VerseWidgetContent.Unavailable -> WidgetRoot(actionStartActivity(verseConfigIntent(context, appWidgetId))) {
            Column {
                WidgetText(strings.verseName, size = 13, bold = true)
                Spacer(GlanceModifier.height(4.dp))
                WidgetText(strings.verseUnavailable(content.reason), size = 13, maxLines = 5)
            }
        }
        is VerseWidgetContent.Verse -> {
            val wide = width >= 250.dp
            val tall = height >= 180.dp
            val open: Action = actionStartActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(content.link)).setPackage(context.packageName),
            )
            WidgetRoot(open) {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    if (config.showSourceAndDate) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(verseConfigIntent(context, appWidgetId))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_widget_sun),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                                modifier = GlanceModifier.size(14.dp),
                            )
                            Gap(4.dp)
                            WidgetText("${content.sourceName} · ${content.periodLabel}", dim = true)
                        }
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetText(content.text, size = if (tall) 17 else 15, maxLines = if (tall) 8 else if (wide) 3 else 4)
                    }
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            WidgetText("${content.reference} · ${content.bibleAbbreviation}", size = 12, bold = true)
                        }
                        if (config.showButtons) {
                            WidgetIconButton(
                                R.drawable.ic_widget_speaker, str(R.string.widget_speak_verse), 32.dp,
                                actionRunCallback<VerseSpeakAction>(androidx.glance.action.actionParametersOf(VERSE_ID to appWidgetId)),
                            )
                            WidgetIconButton(
                                R.drawable.ic_widget_shuffle, str(R.string.widget_shuffle), 32.dp,
                                actionRunCallback<VerseShuffleAction>(androidx.glance.action.actionParametersOf(VERSE_ID to appWidgetId)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Advances the shuffle counter of the widget, which picks another verse of the same period. */
class VerseShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = parameters[VERSE_ID] ?: GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        VerseWidgetConfigStore.shuffle(context, appWidgetId) ?: return
        VerseGlanceWidget().update(context, glanceId)
    }
}

/**
 * Speaks the shown verse the way the verse menu's Speak does: a speech in progress is paused for the next one, then
 * `SpeakControl.speakBible`.
 */
class VerseSpeakAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = parameters[VERSE_ID] ?: GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val ok = withContext(Dispatchers.IO) { CommonUtils.initializeAppForHeadlessEntry() }
        if (!ok) return
        val koin = KoinPlatform.getKoin()
        val config = VerseWidgetConfigStore.load(context, appWidgetId) ?: return
        val content = koin.get<VerseCandidates>().content(config, System.currentTimeMillis(), koin.get<LocalZone>())
        val verse = content as? VerseWidgetContent.Verse ?: return
        val initials = verse.ref.bookInitials ?: return
        val book = Books.installed().getBook(initials) as? SwordBook ?: return
        val v11n = Versifications.instance().getVersification(verse.ref.v11nName)
        withContext(Dispatchers.Main) {
            val speak = koin.get<SpeakControl>().asReadingSpeaker()
            if (speak.isSpeaking) speak.pauseForNextSpeech()
            speak.speakBible(book, Verse(v11n, verse.ref.startOrdinal).toV11n(book.versification))
        }
    }
}

/** Schedules the redraw at the next period boundary: an inexact alarm per widget, handled by [VerseWidgetReceiver]. */
object VerseWidgetScheduler {
    const val ACTION_PERIOD = "net.bible.android.view.widget.VERSE_PERIOD"

    private fun pending(context: Context, appWidgetId: Int): PendingIntent = PendingIntent.getBroadcast(
        context, appWidgetId,
        Intent(context, VerseWidgetReceiver::class.java).setAction(ACTION_PERIOD).setData(Uri.parse("andbible-widget://verse-period/$appWidgetId")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets the alarm for [atEpochMs]; a null time (shuffle-only widget) cancels it. */
    fun schedule(context: Context, appWidgetId: Int, atEpochMs: Long?) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (atEpochMs == null) alarms.cancel(pending(context, appWidgetId))
        else alarms.set(AlarmManager.RTC, atEpochMs, pending(context, appWidgetId))
    }

    fun cancel(context: Context, appWidgetId: Int) = schedule(context, appWidgetId, null)
}
