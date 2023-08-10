/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.activity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkEvent
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.control.speak.load
import net.bible.android.control.speak.save
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.database.migrations.genericBookmark
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.application
import net.bible.service.common.CommonUtils
import net.bible.service.common.AdvancedSpeakSettings
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_PERCENT
import net.bible.service.device.speak.TextCommand
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import java.lang.Exception
import javax.inject.Inject


/**
 * This is singleton manager class which
 * - takes care of updating widgets when they need to change (via EventBus)
 * - receives events from widgets and acts accordingly
 */
class SpeakWidgetManager {
    companion object {
        var instance: SpeakWidgetManager? = null
        const val TAG = "SpeakWidget"
    }

    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl

    private val app = BibleApplication.application
    private val resetTitle = app.getString(R.string.app_name_medium)
    private var currentTitle = resetTitle
    private var currentText = ""

    init {
        if(instance != null) {
            throw IllegalStateException("This is singleton!")
        }
        instance = this
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.application.applicationComponent)
                .build().inject(this)
        ABEventBus.register(this)
    }

    fun destroy() {
        ABEventBus.unregister(this)
        instance = null
    }

    fun onEvent(ev: SpeakProgressEvent) {
        if (ev.speakCommand is TextCommand) {
            if (ev.speakCommand.type == TextCommand.TextType.TITLE) {
                currentTitle = ev.speakCommand.text
                if (currentTitle.isEmpty()) {
                    currentTitle = resetTitle
                }
            } else {
                currentText = ev.speakCommand.text
            }

            updateWidgetTexts()
        }
    }

    fun onEvent(ev: SpeakEvent) {
        if (ev.isSpeaking) {
            currentTitle = resetTitle
        } else if (!ev.isSpeaking && !ev.isPaused) {
            currentTitle = resetTitle
            currentText = ""
        }
        updateWidgetTexts()
        updateWidgetSpeakButton(ev.isSpeaking)
    }

    fun onEvent(ev: SpeakSettingsChangedEvent) {
        updateSleepTimerButtonIcon(ev.speakSettings)
    }

    private fun updateWidgetSpeakButton(speaking: Boolean) {
        Log.i(TAG, "updateWidgetSpeakButton")
        val views = RemoteViews(app.packageName, R.layout.speak_widget)
        val resource = if (speaking) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        views.setImageViewResource(R.id.speakButton, resource)
        partialUpdateWidgets(views)
    }

    private fun updateWidgetTexts() {
        Log.i(TAG, "updateWidgetTexts")
        val views = RemoteViews(app.packageName, R.layout.speak_widget)
        Log.i(TAG, "updating status")
        views.setTextViewText(R.id.titleText, currentTitle)

        val manager = AppWidgetManager.getInstance(app.applicationContext)
        for((cls, wOptions) in widgetOptions) {
            var statusText = speakControl.getStatusText(wOptions.statusFlags)
            if(wOptions.showText) {
                statusText += ": $currentText"
            }
            views.setTextViewText(R.id.statusText, statusText)
            for (id in manager.getAppWidgetIds(ComponentName(app, cls.java))) {
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
    }

    private fun updateSleepTimerButtonIcon(settings: SpeakSettings) {
        val enabled = settings.sleepTimer > 0
        val views = RemoteViews(app.applicationContext.packageName, R.layout.speak_widget)
        val resource = if (enabled) R.drawable.alarm_enabled else R.drawable.alarm_disabled
        views.setImageViewResource(R.id.sleepButton, resource)
        partialUpdateWidgets(views)
    }

    private fun partialUpdateWidgets(views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(app.applicationContext)
        for(cls in widgetOptions.keys) {
            for (id in manager.getAppWidgetIds(ComponentName(app, cls.java))) {
                manager.partiallyUpdateAppWidget(id, views)
            }
        }
    }

    fun onEvent(ev: BookmarkEvent) {
        val manager = AppWidgetManager.getInstance(app)
        for (widgetId in manager.getAppWidgetIds(ComponentName(app, SpeakBookmarkWidget::class.java))) {
            updateBookmarkWidget(app, manager, widgetId)
        }
    }

    private var bookmarksAdded = false

    fun updateBookmarkWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.i(TAG, "updateBookmarkWidget")
        val views = RemoteViews(context.packageName, R.layout.speak_bookmarks_widget)

        views.removeAllViews(R.id.layout)
        fun addButton(name: String, b: BookmarkEntities.BaseBookmarkWithNotes?) {
            val button = RemoteViews(context.packageName, R.layout.speak_bookmarks_widget_button)
            button.setTextViewText(R.id.button, name)
            if(b != null) {
                val type = when(b) {
                    is BookmarkEntities.BibleBookmarkWithNotes -> "bible"
                    is BookmarkEntities.GenericBookmarkWithNotes -> "generic"
                    else -> throw RuntimeException("Illegal type")
                }
                val intent = Intent(context, SpeakBookmarkWidget::class.java).apply {
                    action = SpeakBookmarkWidget.ACTION_BOOKMARK
                    data = Uri.parse("bookmarksById://${type}/${b.id}")
                }
                val bc = PendingIntent.getBroadcast(context, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                button.setOnClickPendingIntent(R.id.button, bc)
            }
            views.addView(R.id.layout, button)
            bookmarksAdded = true
        }

        val label = bookmarkControl.speakLabel
        if(!AdvancedSpeakSettings.autoBookmark) {
            addButton(app.getString(R.string.speak_autobookmarking_disabled), null)
        }
        val bibleBookmarks = bookmarkControl.getBibleBookmarksWithLabel(label).sortedWith { o1, o2 -> o1.verseRange.start.compareTo(o2.verseRange.start) }
        val genBookmarks = bookmarkControl.getGenericBookmarksWithLabel(label)
        for (b in bibleBookmarks + genBookmarks){
            val repeatSymbol = if(b.playbackSettings?.verseRange != null) "\uD83D\uDD01" else ""
            addButton(when(b) {
                is BookmarkEntities.BibleBookmarkWithNotes -> "${b.verseRange.start.name} (${b.playbackSettings?.bookId ?: "?"}) $repeatSymbol"
                is BookmarkEntities.GenericBookmarkWithNotes -> "${b.book?.abbreviation} ${b.bookKey?.name} $repeatSymbol"
                else -> throw RuntimeException("Illegal type")
            }, b)
            Log.i(TAG, "Added button for $b")
        }
        views.setViewVisibility(R.id.helptext, if (bookmarksAdded) View.GONE else View.VISIBLE)

        val contentIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
        val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        views.setOnClickPendingIntent(R.id.root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    abstract class AbstractSpeakWidget : AppWidgetProvider() {
        // Lazy evaluation to these shortcut variables is necessary here as Application (and SpeakWidgetManager)
        // is not instantiated before registering broadcastreceivers (here: widgets) in robolectric tests.
        val instance: SpeakWidgetManager by lazy { SpeakWidgetManager.instance!! }
        val speakControl by lazy { instance.speakControl }
        val bookmarkControl by lazy { instance.bookmarkControl }
        val app by lazy {instance.app }

        override fun onReceive(context: Context?, intent: Intent?) {
            CommonUtils.initializeApp()
            super.onReceive(context, intent)
        }

        override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            CommonUtils.initializeApp()
            Log.i(TAG, "onUpdate")
            for (appWidgetId in appWidgetIds) {
                try {
                    setupWidget(context, appWidgetManager, appWidgetId)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget updating failed!")
                }
            }
        }

        protected abstract fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int)
    }

    abstract class AbstractButtonSpeakWidget : AbstractSpeakWidget() {
        companion object {
            const val ACTION_SPEAK = "action_speak"
            const val ACTION_REWIND = "action_rewind"
            const val ACTION_FAST_FORWARD = "action_fast_forward"
            const val ACTION_STOP = "action_stop"
            const val ACTION_NEXT = "action_next"
            const val ACTION_PREV = "action_prev"
            const val ACTION_SLEEP_TIMER = "action_sleep_timer"
        }

        protected abstract val buttons: List<String>
        private val allButtons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_NEXT, ACTION_PREV, ACTION_REWIND,
                ACTION_SPEAK, ACTION_STOP, ACTION_SLEEP_TIMER)


        override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            Log.i(TAG, "onUpdate")
            for (appWidgetId in appWidgetIds) {
                setupWidget(context, appWidgetManager, appWidgetId)
            }
        }

        override fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            Log.i(TAG, "setupWidget (speakWidget)")

            val views = RemoteViews(context.packageName, R.layout.speak_widget)
            views.setTextViewText(R.id.statusText, "- ${app.getString(R.string.speak_status_stopped)} -")

            fun setupButton(action: String, button: Int, visible: Int) {
                val intent = Intent(context, javaClass)
                intent.action = action
                val bc = PendingIntent.getBroadcast(context, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
                views.setOnClickPendingIntent(button, bc)
                views.setViewVisibility(button, visible)
            }

            val contentIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
            val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            views.setOnClickPendingIntent(R.id.layout, pendingIntent)

            for (b in allButtons) {
                val buttonId = buttonId(b)
                if (buttonId != null) {
                    setupButton(b, buttonId, if (buttons.contains(b)) View.VISIBLE else View.GONE)
                }
            }
            val wOptions = instance.widgetOptions[this::class] as WidgetOptions
            if(!wOptions.showTitle) {
                views.setViewVisibility(R.id.titleText, View.GONE)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
            instance.updateSleepTimerButtonIcon(SpeakSettings.load())
            instance.updateWidgetSpeakButton(speakControl.isSpeaking)
        }

        private fun buttonId(b: String): Int? {
            return when (b) {
                ACTION_SPEAK -> R.id.speakButton
                ACTION_FAST_FORWARD -> R.id.forwardButton
                ACTION_NEXT -> R.id.nextButton
                ACTION_PREV -> R.id.prevButton
                ACTION_REWIND -> R.id.rewindButton
                ACTION_STOP -> R.id.stopButton
                ACTION_SLEEP_TIMER -> R.id.sleepButton
                else -> null
            }
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            super.onReceive(context, intent)
            Log.i(TAG, "onReceive $context ${intent?.action}")
            when (intent?.action) {
                ACTION_SPEAK -> speakControl.toggleSpeak(preferLast = true)
                ACTION_REWIND -> speakControl.rewind()
                ACTION_FAST_FORWARD -> speakControl.forward()
                ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                ACTION_PREV -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                ACTION_SLEEP_TIMER -> toggleSleepTimer()
                ACTION_STOP -> speakControl.stop()
            }
        }

        private fun toggleSleepTimer() {
            val settings = SpeakSettings.load()
            if (settings.sleepTimer > 0) {
                settings.sleepTimer = 0
            } else {
                settings.sleepTimer = settings.lastSleepTimer
            }
            settings.save()
        }
    }

    class SpeakBookmarkWidget : AbstractSpeakWidget() {
        companion object {
            const val ACTION_BOOKMARK = "action_bookmark"
        }

        override fun onReceive(context: Context?, intent: Intent?) {
            super.onReceive(context, intent)
            Log.i(TAG, "onReceive $context ${intent?.action}")
            if (intent?.action == ACTION_BOOKMARK) {
                val bookmarkType = intent.data?.host ?: return
                val path = intent.data?.path ?: return
                val bookmarkId = path.slice(1 until path.length)
                Log.i(TAG, "onReceive osisRef $bookmarkId $bookmarkType")
                val dto = when(bookmarkType) {
                    "bible" -> bookmarkControl.bibleBookmarksByIds(listOf(IdType(bookmarkId))).first()
                    "generic" -> bookmarkControl.genericBookmarkById(IdType(bookmarkId))!!
                    else -> throw RuntimeException("Illegal type")
                }
                speakControl.speakFromBookmark(dto)
            }
        }

        override fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            instance.updateBookmarkWidget(context, appWidgetManager, appWidgetId)
        }
    }

    class WidgetOptions(val statusFlags: Int = FLAG_SHOW_ALL, val showTitle: Boolean = true, val showText: Boolean = false)

    val widgetOptions = hashMapOf(
            SmallSpeakControlWidget::class to WidgetOptions(0, false),
            MiddleSpeakControlWidget::class to WidgetOptions(FLAG_SHOW_PERCENT, true),
            LargeSpeakControlWidget::class to WidgetOptions(FLAG_SHOW_ALL, true),
            SpeakTextControlWidget::class to WidgetOptions(0, true, true)
    )

    class SmallSpeakControlWidget : AbstractButtonSpeakWidget() {
        override val buttons: List<String> = listOf(ACTION_REWIND, ACTION_SPEAK)
    }

    class MiddleSpeakControlWidget : AbstractButtonSpeakWidget() {
        override val buttons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP, ACTION_SLEEP_TIMER)
    }

    class LargeSpeakControlWidget : AbstractButtonSpeakWidget() {
        override val buttons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_NEXT, ACTION_PREV, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP, ACTION_SLEEP_TIMER)
    }

    class SpeakTextControlWidget : AbstractButtonSpeakWidget() {
        override val buttons: List<String> = listOf(ACTION_PREV, ACTION_NEXT, ACTION_SPEAK, ACTION_STOP)
    }
}
