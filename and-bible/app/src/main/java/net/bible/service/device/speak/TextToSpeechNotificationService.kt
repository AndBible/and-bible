package net.bible.service.device.speak

import android.annotation.SuppressLint
import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.annotation.RequiresApi
import android.util.Log
import android.widget.RemoteViews
import de.greenrobot.event.EventBus
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.activity.SpeakWidget
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.device.speak.event.SpeakEventManager
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

@ActivityScope
class TextToSpeechNotificationService: Service() {
    companion object {
        const val ACTION_START="action_start"
        const val ACTION_REMOVE="action_remove"

        const val ACTION_PLAY="action_play"
        const val ACTION_PAUSE="action_pause"
        const val ACTION_REWIND="action_rewind"
        const val ACTION_FAST_FORWARD="action_fast_forward"
        const val ACTION_STOP="action_stop"

        const val CHANNEL_ID="speak-notifications"
        const val NOTIFICATION_ID=1
        const val WAKELOCK_TAG = "speak-wakelock"
        const val TAG = "Speak/TTSService"
    }

    @Inject lateinit var speakControl: SpeakControl

    private lateinit var currentTitle: String
    private var currentText = ""
    lateinit var notificationManager: NotificationManager
    private lateinit var headsetReceiver: BroadcastReceiver
    private lateinit var wakeLock: PowerManager.WakeLock

    private val pauseAction: Notification.Action
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_PAUSE)

    private val playAction: Notification.Action
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() = generateAction(android.R.drawable.ic_media_play, getString(R.string.speak), ACTION_PLAY)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(! ::speakControl.isInitialized) {
            initialize()
        }

        handleIntent(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initialize() {
        Log.d(TAG, "Initialize")
        currentTitle = getString(R.string.app_name)
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.getApplication().getApplicationComponent())
                .build().inject(this)

        val powerManager = BibleApplication.getApplication().getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        headsetReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getIntExtra("state", 0) == 0 && speakControl.isSpeaking) {
                    speakControl.pause()
                } else if (intent?.getIntExtra("state", 0) == 1 && speakControl.isPaused) {
                    speakControl.continueAfterPause()
                }
            }
        }
        registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))

        EventBus.getDefault().register(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        SpeakEventManager.getInstance().addSpeakEventListener {
            if(!it.isSpeaking) {
                Log.d(TAG, "Stop foreground (pause)")
                stopForeground(false)
                if(wakeLock.isHeld) {
                    wakeLock.release()
                }
                buildNotification(playAction)
            }
            else {
                buildNotification(pauseAction, true)
            }
            updateWidgetSpeakButton(it.isSpeaking)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.tts_status), NotificationManager.IMPORTANCE_LOW)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(headsetReceiver)
        super.onDestroy()
    }

    private fun shutdown() {
        currentTitle = getString(R.string.app_name)
        currentText = ""
        updateWidgetTexts()
        Log.d(TAG, "Shutdown")
        stopForeground(true)
        if(wakeLock.isHeld) {
            wakeLock.release()
        }
        stopService(Intent(applicationContext, this.javaClass))
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onEventMainThread(ev: SpeakProggressEvent) {
        if(ev.speakCommand is TextCommand) {
            if(ev.speakCommand.type == TextCommand.TextType.TITLE) {
                currentTitle = ev.speakCommand.text
            }
            else {
                currentText = ev.speakCommand.text;
            }
        }
        buildStartNotification()
        updateWidgetTexts()
    }

    private fun updateWidgetSpeakButton(speaking: Boolean) {
        val app = BibleApplication.getApplication()
        val views = RemoteViews(app.applicationContext.packageName, R.layout.speak_widget)
        val resource = if(speaking) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        views.setImageViewResource(R.id.speakButton, resource)
        partialUpdateWidgets(views)
    }


    private fun updateWidgetTexts() {
        val views = RemoteViews(applicationContext.packageName, R.layout.speak_widget)
        views.setTextViewText(R.id.statusText, speakControl.statusText)
        views.setTextViewText(R.id.titleText, currentTitle)
        partialUpdateWidgets(views)
    }

    private fun partialUpdateWidgets(views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(applicationContext)
        for(id in manager.getAppWidgetIds(ComponentName(application, SpeakWidget::class.java))) {
            manager.partiallyUpdateAppWidget(id, views)
        }
    }


    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun buildStartNotification() {
        buildNotification(if (speakControl.isSpeaking) pauseAction else playAction)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handleIntent(intent: Intent?) {
        if(intent?.action == null) {
            return
        }
        when(intent.action) {
            ACTION_START -> buildStartNotification()
            ACTION_REMOVE -> shutdown()
            ACTION_PLAY -> {
                speakControl.continueAfterPause()
            }
            ACTION_PAUSE -> {
                speakControl.pause()
            }
            ACTION_FAST_FORWARD -> speakControl.forward()
            ACTION_REWIND -> speakControl.rewind()
            ACTION_STOP -> {
                speakControl.stop()
                shutdown()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun generateAction(icon: Int, title: String, intentAction: String): Notification.Action {
        val intent = Intent(applicationContext, this.javaClass)
        intent.setAction(intentAction)
        val pendingIntent = PendingIntent.getService(applicationContext, 1, intent, 0)
        return Notification.Action.Builder(icon, title, pendingIntent).build()
    }

    @SuppressLint("WakelockTimeout")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun buildNotification(action: Notification.Action, foreground: Boolean = false) {
        val style = Notification.MediaStyle()

        val deleteIntent = Intent(applicationContext, this.javaClass)
        deleteIntent.setAction(ACTION_STOP)
        val deletePendingIntent = PendingIntent.getService(applicationContext, 1, deleteIntent, 0)

        val contentIntent = Intent(applicationContext, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentPendingIntent = PendingIntent.getActivity(applicationContext, 1, contentIntent, 0)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder.setSmallIcon(R.drawable.ichthys_alpha)
                .setContentTitle(currentTitle)
                .setSubText(speakControl.statusText)
                .setShowWhen(false)
                .setContentText(currentText)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setStyle(style)
                .addAction(generateAction(android.R.drawable.ic_media_rew, getString(R.string.rewind), ACTION_REWIND))
                .addAction(action)
                .addAction(generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD))
                .setOnlyAlertOnce(true)

        style.setShowActionsInCompactView(1)

        val notification = builder.build()
        if(foreground) {
            Log.d(TAG, "Starting foreground")
            startForeground(NOTIFICATION_ID, notification)
            if(!wakeLock.isHeld) {
                wakeLock.acquire()
            }
        }
        else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }
}