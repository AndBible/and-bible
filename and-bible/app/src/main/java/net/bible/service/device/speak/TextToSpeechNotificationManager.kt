package net.bible.service.device.speak

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.BitmapFactory
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

    private object Holder { val INSTANCE = TextToSpeechNotificationManager() }

    companion object {
        private const val ACTION_UPDATE_NOTIFICATION = "update_notification"
        private const val ACTION_PLAY="action_play"
        private const val ACTION_PAUSE="action_pause"
        private const val ACTION_REWIND="action_rewind"
        private const val ACTION_PREVIOUS="action_previous"
        private const val ACTION_NEXT="action_next"
        private const val ACTION_FAST_FORWARD="action_fast_forward"
        private const val ACTION_STOP="action_stop"

        private const val SPEAK_NOTIFICATIONS_CHANNEL="speak-notifications"

        private const val NOTIFICATION_ID=1

        private const val WAKELOCK_TAG = "speak-wakelock"
        private const val TAG = "Speak/TTSService"
        private lateinit var wakeLock: PowerManager.WakeLock

        private var foregroundNotification: Notification? = null

        val instance: TextToSpeechNotificationManager by lazy { Holder.INSTANCE }
    }

    class ForegroundService: Service() {
        companion object {
            const val START_SERVICE="action_start_service"
            const val STOP_FOREGROUND="action_stop_foreground"
        }

        private var foreground = false

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            when(intent?.action) {
                START_SERVICE -> start()
                STOP_FOREGROUND -> stop()
                else -> {
                    Log.e(TAG, "Unknown action ${intent?.action} in intent $intent")
                }
            }

            return super.onStartCommand(intent, flags, startId)
        }

        @SuppressLint("WakelockTimeout")
        private fun start() {
            if(foreground) {
                return
            }

            Log.d(TAG, "START_SERVICE")
            startForeground(NOTIFICATION_ID, foregroundNotification!!)
            foreground = true
            wakeLock.acquire()
        }

        override fun onDestroy() {
            // If application is in background (no activity is active) and this service is not foreground either,
            // this service will be stopped by android some time (1 minute when I tested). App itself remains
            // running until OOM killer stops it. Because notification is bound to this service, it will be
            // removed. Thus we need to build notification again. This will be done
            // TextToSpeechNotificationManager via this intent.

            Log.d(TAG, "onDestroy")
            val intent = Intent(application, NotificationReceiver::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
            }
            application.sendBroadcast(intent)
            stop()
        }

        private fun stop() {
            if(!foreground) {
                return
            }

            Log.d(TAG, "STOP_SERVICE")
            wakeLock.release()
            stopForeground(false)
            foregroundNotification = null;
            foreground = false
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null;
        }
    }

    class NotificationReceiver: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val speakControl = instance.speakControl
                val action = intent?.action
                Log.d(TAG, "onReceive $intent $action")
                when (action) {
                    ACTION_PLAY -> speakControl.toggleSpeak()
                    ACTION_PAUSE -> speakControl.pause()
                    ACTION_FAST_FORWARD -> speakControl.forward()
                    ACTION_REWIND -> speakControl.rewind()
                    ACTION_PREVIOUS -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                    ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                    ACTION_STOP -> speakControl.stop()
                    ACTION_UPDATE_NOTIFICATION -> {
                        if(speakControl.isPaused) {
                            instance.buildNotification(false)
                        }
                    }
                }
            }
        }


    private var app: BibleApplication
    private var currentTitle: String
    private var notificationManager: NotificationManager
    private var headsetReceiver: BroadcastReceiver
    @Inject lateinit var speakControl: SpeakControl

    private var currentText = ""

    private fun getString(id: Int): String {
        return BibleApplication.getApplication().getString(id)
    }

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

        ABEventBus.getDefault().register(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(SPEAK_NOTIFICATIONS_CHANNEL,
                    getString(R.string.tts_status), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun destroy() {
        app.unregisterReceiver(headsetReceiver)
        shutdown()
    }

    private fun shutdown() {
        Log.d(TAG, "Shutdown")
        currentTitle = getString(R.string.app_name)
        currentText = ""
        stopForeground()
    }

    fun onEvent(ev: SpeakEvent) {
        Log.d(TAG, "SpeakEvent $ev")
        if(!ev.isSpeaking && ev.isPaused) {
            Log.d(TAG, "Stop foreground (pause)")
            buildNotification(false)
            stopForeground()
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

    private fun generateAction(icon: Int, title: String, command: String): NotificationCompat.Action {
        val intent = Intent(app, NotificationReceiver::class.java).apply {
            action = command
         }
        val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, 0)
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }

    private val rewindAction = generateAction(android.R.drawable.ic_media_rew, getString(R.string.rewind), ACTION_REWIND)
    private val prevAction = generateAction(android.R.drawable.ic_media_previous, getString(R.string.previous), ACTION_PREVIOUS)
    private val pauseAction = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_PAUSE)
    private val playAction = generateAction(android.R.drawable.ic_media_play, getString(R.string.speak), ACTION_PLAY)
    private val nextAction = generateAction(android.R.drawable.ic_media_next, getString(R.string.next), ACTION_NEXT)
    private val forwardAction = generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD)
    private val bibleBitmap = BitmapFactory.decodeResource(app.resources, R.drawable.bible)

    private fun buildNotification(isSpeaking: Boolean) {
        val deletePendingIntent = PendingIntent.getBroadcast(app, 0,
                Intent(app, NotificationReceiver::class.java).apply {
                    action = ACTION_STOP
                }, 0)

        val contentIntent = Intent(app, MainBibleActivity::class.java)
        contentIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentPendingIntent = PendingIntent.getActivity(app, 0, contentIntent, 0)

        val style = MediaStyle().setShowActionsInCompactView(2)

        val builder = NotificationCompat.Builder(app, SPEAK_NOTIFICATIONS_CHANNEL)

        builder.setSmallIcon(R.drawable.ichthys_alpha)
                .setLargeIcon(bibleBitmap)
                .setContentTitle(currentTitle)
                .setSubText(speakControl.statusText)
                .setShowWhen(false)
                .setContentText(currentText)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setStyle(style)
                .addAction(rewindAction)
                .addAction(prevAction)
                .addAction(if(isSpeaking) pauseAction else playAction)
                .addAction(nextAction)
                .addAction(forwardAction)
                .setOnlyAlertOnce(true)

        val notification = builder.build()
        Log.d(TAG, "Updating notification, isSpeaking: $isSpeaking")
        if(isSpeaking) {
            foregroundNotification = notification
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startForeground()
    {
        val intent = Intent(app, ForegroundService::class.java)
        intent.action = ForegroundService.START_SERVICE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        }
        else {
            app.startService(intent)
        }
    }

    private fun stopForeground()
    {
       val intent = Intent(app, ForegroundService::class.java)
       intent.action = ForegroundService.STOP_FOREGROUND
       app.startService(intent)
    }
}