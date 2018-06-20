package net.bible.android.activity

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import android.app.PendingIntent
import android.content.Intent
import net.bible.service.device.speak.TextToSpeechNotificationService
import net.bible.android.BibleApplication
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import javax.inject.Inject

class SpeakWidget : AppWidgetProvider() {
    companion object {
        const val ACTION_PLAY="action_play"
        const val ACTION_PAUSE="action_pause"
        const val ACTION_REWIND="action_rewind"
        const val ACTION_FAST_FORWARD="action_fast_forward"
        const val ACTION_STOP="action_stop"
        const val TAG = "SpeakWidget"
    }

    @Inject lateinit var speakControl: SpeakControl

    private fun initialize() {
        if(::speakControl.isInitialized) {
            return
        }
        Log.d(TextToSpeechNotificationService.TAG, "Initialize")
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.getApplication().getApplicationComponent())
                .build().inject(this)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate")
        initialize()
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        initialize()
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive" + context + intent?.action)
        when(intent?.action) {
            ACTION_PLAY -> {
                if(speakControl.isPaused) {
                    speakControl.continueAfterPause()
                }
                else {
                    speakControl.speakBible()
                }
            }
            ACTION_PAUSE -> {
                speakControl.pause()
            }
            ACTION_FAST_FORWARD -> speakControl.forward()
            ACTION_REWIND -> speakControl.rewind()
            ACTION_STOP -> speakControl.stop()
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        Log.d(TAG, "updateAppWidget")

        val views = RemoteViews(context.packageName, R.layout.speak_widget)
        views.setTextViewText(R.id.titleText, context.getString(R.string.app_name))
        views.setTextViewText(R.id.statusText, speakControl.statusText)

        fun setupButton(action: String, button: Int) {
            val intent = Intent(context, javaClass)
            intent.action = action
            val bc = PendingIntent.getBroadcast(context, 0, intent, 0)
            views.setOnClickPendingIntent(button, bc);
        }

        val contentIntent = Intent(context, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, 0)
        views.setOnClickPendingIntent(R.id.layout, pendingIntent)

        setupButton(ACTION_PLAY, R.id.speakButton)
        setupButton(ACTION_STOP, R.id.stopButton)
        setupButton(ACTION_PAUSE, R.id.pauseButton)
        setupButton(ACTION_REWIND, R.id.rewindButton)
        setupButton(ACTION_FAST_FORWARD, R.id.forwardButton)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

