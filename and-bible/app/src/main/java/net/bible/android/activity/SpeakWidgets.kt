package net.bible.android.activity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import de.greenrobot.event.EventBus
import de.greenrobot.event.EventBusException
import net.bible.android.BibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.event.passage.SynchronizeWindowsEvent
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.control.speak.SpeakSettingsChangedEvent
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.db.bookmark.BookmarkDto
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.device.speak.TextCommand
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import javax.inject.Inject


abstract class AbstractSpeakWidget: AppWidgetProvider() {
    @Inject
    lateinit var speakControl: SpeakControl
    protected lateinit var currentTitle: String
    @Inject
    lateinit var bookmarkControl: BookmarkControl
    companion object {
        const val TAG = "SpeakWidget"
        val app = BibleApplication.getApplication()
    }

    protected fun initialize() {
        if (::speakControl.isInitialized) {
            return
        }
        currentTitle = app.getString(R.string.app_name)
        Log.d(TAG, "Initialize")
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.getApplication().applicationComponent)
                .build().inject(this)
        try {
            EventBus.getDefault().register(this)
        } catch (e: EventBusException) {}
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate")
        initialize()
        for (appWidgetId in appWidgetIds) {
            setupWidget(context, appWidgetManager, appWidgetId)
        }
    }
    protected abstract fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int)
}


abstract class AbstractButtonSpeakWidget: AbstractSpeakWidget() {
    protected abstract val buttons: List<String>
    private val allButtons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_NEXT, ACTION_PREV, ACTION_REWIND,
            ACTION_SPEAK, ACTION_STOP, ACTION_SLEEP_TIMER)


    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate")
        initialize()
        for (appWidgetId in appWidgetIds) {
            setupWidget(context, appWidgetManager, appWidgetId)
        }
    }

    fun onEvent(ev: SpeakProgressEvent) {
        if(ev.speakCommand is TextCommand) {
            if(ev.speakCommand.type == TextCommand.TextType.TITLE) {
                currentTitle = ev.speakCommand.text
                if(currentTitle.isEmpty()) {
                    currentTitle = app.getString(R.string.app_name)
                }
            }
            updateWidgetTexts()
        }
    }

    fun onEvent(ev: SpeakEvent) {
        if(ev.isSpeaking) {
            currentTitle = app.getString(R.string.app_name)
        }
        updateWidgetSpeakButton(ev.isSpeaking)
    }

    fun onEvent(ev: SpeakSettingsChangedEvent) {
        updateSleepTimerButtonIcon(ev.speakSettings)
    }

    private fun updateWidgetSpeakButton(speaking: Boolean) {
        val views = RemoteViews(app.applicationContext.packageName, R.layout.speak_widget)
        val resource = if(speaking) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        views.setImageViewResource(R.id.speakButton, resource)
        partialUpdateWidgets(views)
    }

    private fun updateWidgetTexts() {
        val views = RemoteViews(app.packageName, R.layout.speak_widget)
        views.setTextViewText(R.id.statusText, speakControl.statusText)
        views.setTextViewText(R.id.titleText, currentTitle)
        partialUpdateWidgets(views)
    }

    private fun partialUpdateWidgets(views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(app.applicationContext)
        for(id in manager.getAppWidgetIds(ComponentName(app, this::class.java))) {
            manager.partiallyUpdateAppWidget(id, views)
        }
    }

    override fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "setupWidget")

        val views = RemoteViews(context.packageName, R.layout.speak_widget)
        views.setTextViewText(R.id.titleText, context.getString(R.string.app_name))
        views.setTextViewText(R.id.statusText, speakControl.statusText)

        fun setupButton(action: String, button: Int, visible: Int) {
            val intent = Intent(context, javaClass)
            intent.action = action
            val bc = PendingIntent.getBroadcast(context, 0, intent, 0)
            views.setOnClickPendingIntent(button, bc)
            views.setViewVisibility(button, visible)
        }

        val contentIntent = Intent(context, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0)
        views.setOnClickPendingIntent(R.id.layout, pendingIntent)

        for(b in allButtons) {
            val buttonId = buttonId(b)
            if(buttonId != null) {
                setupButton(b, buttonId, if(buttons.contains(b)) View.VISIBLE else View.GONE)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
        updateSleepTimerButtonIcon(SpeakSettings.load())
    }

    private fun buttonId(b: String): Int? {
        return when(b) {
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
        initialize()
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive" + context + intent?.action)
        when(intent?.action) {
            ACTION_SPEAK -> {
                if(speakControl.isPaused) {
                    speakControl.continueAfterPause()
                }
                else if (!speakControl.isSpeaking) {
                    speakControl.speakBible() // TODO gen books
                }
                else {
                    speakControl.pause()
                }
            }
            ACTION_REWIND -> speakControl.rewind()
            ACTION_FAST_FORWARD -> speakControl.forward()
            ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
            ACTION_PREV -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
            ACTION_SLEEP_TIMER -> speakControl.toggleSleepTimer()
            ACTION_STOP -> speakControl.stop()
        }
    }

    private fun updateSleepTimerButtonIcon(settings: SpeakSettings) {
        val enabled = settings.sleepTimer > 0
        val views = RemoteViews(app.applicationContext.packageName, R.layout.speak_widget)
        val resource = if(enabled) R.drawable.alarm_enabled else R.drawable.alarm_disabled
        views.setImageViewResource(R.id.sleepButton, resource)
        partialUpdateWidgets(views)
    }

    companion object {
        const val ACTION_SPEAK="action_speak"
        const val ACTION_REWIND="action_rewind"
        const val ACTION_FAST_FORWARD="action_fast_forward"
        const val ACTION_STOP="action_stop"
        const val ACTION_NEXT="action_next"
        const val ACTION_PREV="action_prev"
        const val ACTION_SLEEP_TIMER="action_sleep_timer"
    }
}

class SpeakBookmarkWidget: AbstractSpeakWidget() {
    override fun onReceive(context: Context?, intent: Intent?) {
        initialize()
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive" + context + intent?.action)
        if(intent?.action?.startsWith("ACTION_BOOKMARK_") == true) {
            val osisRef = intent.action.substring(16..intent.action.length-1)
            val dto = bookmarkControl.getBookmarkByOsisRef(osisRef)
            if(speakControl.isSpeaking || speakControl.isPaused) {
                speakControl.stop()
            }
            speakControl.speakBible(dto.verseRange.start)
        }
    }

    fun onEvent(ev: SynchronizeWindowsEvent) {
        val manager = AppWidgetManager.getInstance(app)
        for(i in manager.getAppWidgetIds(ComponentName(app, this::class.java))) {
            setupWidget(app, manager, i)
        }
    }

    override fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "setupWidget")

        val views = RemoteViews(context.packageName, R.layout.speak_bookmarks_widget)

        views.removeAllViews(R.id.layout)

        fun addButton(name: String, osisRef: String) {
            val button = RemoteViews(context.packageName, R.layout.speak_bookmarks_widget_button)
            button.setTextViewText(R.id.button, name)

            val intent = Intent(context, javaClass)
            intent.action = "ACTION_BOOKMARK_$osisRef"
            val bc = PendingIntent.getBroadcast(context, 0, intent, 0)
            button.setOnClickPendingIntent(R.id.button, bc)
            views.addView(R.id.layout, button)
        }

        val settings = SpeakSettings.load()
        val labelDto = LabelDto()
        labelDto.id = settings.autoBookmarkLabelId

        for(b in bookmarkControl.getBookmarksWithLabel(labelDto).sortedWith(
                Comparator<BookmarkDto> { o1, o2 -> o1.verseRange.start.compareTo(o2.verseRange.start) })) {
            addButton(b.verseRange.start.name, b.verseRange.start.osisRef)
            Log.d(TAG, "Added button for $b")
        }

        val contentIntent = Intent(context, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0)
        views.setOnClickPendingIntent(R.id.layout, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

class SpeakWidget1 : AbstractButtonSpeakWidget() {
    override val buttons: List<String> = listOf(ACTION_REWIND, ACTION_SPEAK)
}

class SpeakWidget2 : AbstractButtonSpeakWidget() {
    override val buttons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP, ACTION_SLEEP_TIMER)
}

class SpeakWidget3 : AbstractButtonSpeakWidget() {
    override val buttons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_NEXT, ACTION_PREV, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP, ACTION_SLEEP_TIMER)
}

