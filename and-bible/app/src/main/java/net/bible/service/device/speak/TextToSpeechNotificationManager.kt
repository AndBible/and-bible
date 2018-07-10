package net.bible.service.device.speak

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.support.v4.media.app.NotificationCompat.MediaStyle
import android.util.Log
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import javax.inject.Inject

@ActivityScope
class TextToSpeechNotificationManager {
    companion object {

        private const val ACTION_PLAY="action_play"
        private const val ACTION_PAUSE="action_pause"
        private const val ACTION_REWIND="action_rewind"
        private const val ACTION_PREVIOUS="action_previous"
        private const val ACTION_NEXT="action_next"
        private const val ACTION_FAST_FORWARD="action_fast_forward"
        private const val ACTION_STOP="action_stop"

        private const val ACTION_START_SERVICE="action_start_service"
        private const val ACTION_PAUSE_SERVICE="action_pause_service"
        private const val ACTION_STOP_SERVICE="action_stop_service"

        private const val SPEAK_NOTIFICATIONS_CHANNEL="speak-notifications"
        private const val NOTIFICATION_ID=1
        private const val WAKELOCK_TAG = "speak-wakelock"
        private const val TAG = "Speak/TTSService"
        private lateinit var wakeLock: PowerManager.WakeLock

        private var currentNotification: Notification? = null
    }

    class ForegroundService: Service() {
        private var foreground = false

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            when(intent?.action) {
                ACTION_START_SERVICE -> {
                    start()
                }
                ACTION_STOP_SERVICE -> {
                    stop(true)
                }
                ACTION_PAUSE_SERVICE -> {
                    stop(false)
                }
            }

            return super.onStartCommand(intent, flags, startId)
        }

        private fun stop(removeNotification: Boolean) {
            if(!foreground) {
                return
            }

            Log.d(TAG, "STOP_SERVICE removeNotification: $removeNotification")

            wakeLock.release()
            stopForeground(removeNotification)
            foreground = false
        }

        @SuppressLint("WakelockTimeout")
        private fun start() {
            if(foreground) {
                return
            }

            Log.d(TAG, "START_SERVICE")
            startForeground(NOTIFICATION_ID, currentNotification)
            foreground = true
            wakeLock.acquire()
        }

        override fun onTaskRemoved(rootIntent: Intent?) {
            if(!foreground) {
                shutDown()
            }
        }

        override fun onDestroy() {
            shutDown()
        }

        private fun shutDown() {
            val notificationManager = BibleApplication.getApplication().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
            stopSelf()
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null;
        }
    }

    @Inject lateinit var speakControl: SpeakControl
    private var app: BibleApplication
    private var currentTitle: String
    private var notificationManager: NotificationManager
    private var headsetReceiver: BroadcastReceiver
    private var notificationReceiver: BroadcastReceiver

    private var currentText = ""

    private val pauseAction: NotificationCompat.Action
        get() = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_PAUSE)

    private fun getString(id: Int): String {
        return BibleApplication.getApplication().getString(id)
    }

    private val playAction: NotificationCompat.Action
        get() = generateAction(android.R.drawable.ic_media_play, getString(R.string.speak), ACTION_PLAY)


    init {
        Log.d(TAG, "Initialize")
        app = BibleApplication.getApplication()
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        currentTitle = getString(R.string.app_name)
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.getApplication().getApplicationComponent())
                .build().inject(this)

        val powerManager = BibleApplication.getApplication().getSystemService(Context.POWER_SERVICE) as PowerManager
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
        app.registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))

        notificationReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "onReceive $context $intent")
                when (intent?.action) {
                    ACTION_PLAY -> speakControl.continueAfterPause()
                    ACTION_PAUSE -> speakControl.pause()
                    ACTION_FAST_FORWARD -> speakControl.forward()
                    ACTION_REWIND -> speakControl.rewind()
                    ACTION_PREVIOUS -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                    ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                    ACTION_STOP -> speakControl.stop()
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(ACTION_PLAY)
        filter.addAction(ACTION_PAUSE)
        filter.addAction(ACTION_REWIND)
        filter.addAction(ACTION_FAST_FORWARD)
        filter.addAction(ACTION_NEXT)
        filter.addAction(ACTION_PREVIOUS)
        filter.addAction(ACTION_STOP)

        app.registerReceiver(notificationReceiver, filter)

        ABEventBus.getDefault().register(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(SPEAK_NOTIFICATIONS_CHANNEL, getString(R.string.tts_status), NotificationManager.IMPORTANCE_DEFAULT)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.cancelAll()
    }

    fun destroy() {
        app.unregisterReceiver(headsetReceiver)
        app.unregisterReceiver(notificationReceiver)
        shutdown()
    }

    private fun shutdown() {
        Log.d(TAG, "Shutdown")
        currentTitle = getString(R.string.app_name)
        currentText = ""
        stopForeground(true)
    }

    fun onEvent(ev: SpeakEvent) {
        Log.d(TAG, "SpeakEvent $ev")
        if(!ev.isSpeaking && ev.isPaused) {
            Log.d(TAG, "Stop foreground (pause)")
            stopForeground()
            buildNotification(false)
        }
        else if (ev.isSpeaking) {
            buildNotification(true)
            startForeground()
        }
        else {
            shutdown()
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    fun onEvent(ev: SpeakProgressEvent) {
        if(ev.speakCommand is TextCommand) {
            if(ev.speakCommand.type == TextCommand.TextType.TITLE) {
                currentTitle = ev.speakCommand.text
                if(currentTitle.isEmpty()) {
                    currentTitle = getString(R.string.app_name)
                }
            }
            else {
                currentText = ev.speakCommand.text;
            }
        }
        buildNotification(speakControl.isSpeaking)
    }

    private fun generateAction(icon: Int, title: String, intentAction: String): NotificationCompat.Action {
        val intent = Intent(intentAction)
        val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, 0)
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }

    private fun buildNotification(isSpeaking: Boolean) {
        val deletePendingIntent = PendingIntent.getBroadcast(app, 0, Intent(ACTION_STOP), 0)

        val contentIntent = Intent(app, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentPendingIntent = PendingIntent.getActivity(app, 0, contentIntent, 0)

        val style = MediaStyle()
        style.setShowActionsInCompactView(2)

        val builder = NotificationCompat.Builder(app, SPEAK_NOTIFICATIONS_CHANNEL)

        builder.setSmallIcon(R.drawable.ichthys_alpha)
                .setContentTitle(currentTitle)
                .setSubText(speakControl.statusText)
                .setShowWhen(false)
                .setContentText(currentText)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setStyle(style)
                .addAction(generateAction(android.R.drawable.ic_media_rew, getString(R.string.rewind), ACTION_REWIND))
                .addAction(generateAction(android.R.drawable.ic_media_previous, getString(R.string.previous), ACTION_PREVIOUS))

        builder.addAction(if(isSpeaking) pauseAction else playAction)

        if(!isSpeaking) {
            builder.addAction(generateAction(R.drawable.ic_media_stop, getString(R.string.stop), ACTION_STOP))
        }
        else {
            builder.addAction(generateAction(android.R.drawable.ic_media_next, getString(R.string.next), ACTION_NEXT))
        }

        builder.addAction(generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD))
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)

        val notification = builder.build()
        Log.d(TAG, "Updating notification")
        notificationManager.notify(NOTIFICATION_ID, notification)
        currentNotification = notification
    }

    private fun startForeground()
    {
        val intent = Intent(app, ForegroundService::class.java)
        intent.action = ACTION_START_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        }
        else {
            app.startService(intent)
        }
    }

    private fun stopForeground(removeNotification: Boolean = false)
    {
        val intent = Intent(app, ForegroundService::class.java)
        intent.action = if(removeNotification) ACTION_STOP_SERVICE else ACTION_PAUSE_SERVICE
        app.startService(intent)
    }
}