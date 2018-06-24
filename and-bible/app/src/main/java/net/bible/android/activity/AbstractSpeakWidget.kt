package net.bible.android.activity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View.*
import android.widget.RemoteViews
import net.bible.android.BibleApplication
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import javax.inject.Inject

abstract class AbstractSpeakWidget: AppWidgetProvider() {
    @Inject lateinit var speakControl: SpeakControl
    protected abstract val buttons: List<String>
    private val allButtons: List<String> = listOf(ACTION_FAST_FORWARD, ACTION_NEXT, ACTION_PREV, ACTION_REWIND, ACTION_SPEAK, ACTION_STOP)

    protected fun initialize() {
        if(::speakControl.isInitialized) {
            return
        }
        Log.d(TAG, "Initialize")
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.getApplication().getApplicationComponent())
                .build().inject(this)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate")
        initialize()
        for (appWidgetId in appWidgetIds) {
            setupWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun setupWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
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
                setupButton(b, buttonId, if(buttons.contains(b)) VISIBLE else GONE)
            }
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun buttonId(b: String): Int? {
        return when(b) {
                ACTION_SPEAK -> R.id.speakButton
                ACTION_FAST_FORWARD -> R.id.forwardButton
                ACTION_NEXT -> R.id.nextButton
                ACTION_PREV -> R.id.prevButton
                ACTION_REWIND -> R.id.rewindButton
                ACTION_STOP -> R.id.stopButton
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
                    speakControl.speakBible()
                }
                else {
                    speakControl.pause()
                }
            }
            ACTION_REWIND -> speakControl.rewind()
            ACTION_FAST_FORWARD -> speakControl.forward()
            ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
            ACTION_PREV -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
            ACTION_STOP -> {
                speakControl.stop()
            }
        }
    }


    companion object {
        const val TAG = "SpeakWidget"
        const val ACTION_SPEAK="action_speak"
        const val ACTION_REWIND="action_rewind"
        const val ACTION_FAST_FORWARD="action_fast_forward"
        const val ACTION_STOP="action_stop"
        const val ACTION_NEXT="action_next"
        const val ACTION_PREV="action_prev"
    }
}