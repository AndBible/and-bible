package net.bible.service.device.speak

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.annotation.RequiresApi
import android.util.Log
import de.greenrobot.event.EventBus
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import javax.inject.Inject

@ActivityScope
class TextToSpeechNotificationService: Service() {
    companion object {
        const val ACTION_START_SERVICE="action_start_service"
        const val ACTION_STOP_SERVICE="action_stop_service"

        const val ACTION_PLAY="action_play"
        const val ACTION_PAUSE="action_pause"
        const val ACTION_REWIND="action_rewind"
        const val ACTION_PREVIOUS="action_previous"
        const val ACTION_NEXT="action_next"
        const val ACTION_FAST_FORWARD="action_fast_forward"
        const val ACTION_STOP="action_stop"

        const val CHANNEL_ID="speak-notifications"
        const val NOTIFICATION_ID=1
        const val WAKELOCK_TAG = "speak-wakelock"
        const val TAG = "Speak/TTSService"
    }

    @Inject lateinit var speakControl: SpeakControl
    private lateinit var currentTitle: String
    private lateinit var notificationManager: NotificationManager
    private lateinit var headsetReceiver: BroadcastReceiver
    private lateinit var wakeLock: PowerManager.WakeLock

    private var currentText = ""

    private val pauseAction: Notification.Action
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_PAUSE)

    private val playAction: Notification.Action
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() = generateAction(android.R.drawable.ic_media_play, getString(R.string.speak), ACTION_PLAY)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(! ::speakControl.isInitialized) {
            initialize()
        }

        when(intent?.action) {
            ACTION_START_SERVICE -> {}
            ACTION_STOP_SERVICE -> shutdown()
            ACTION_PLAY -> speakControl.continueAfterPause()
            ACTION_PAUSE -> speakControl.pause()
            ACTION_FAST_FORWARD -> speakControl.forward()
            ACTION_REWIND -> speakControl.rewind()
            ACTION_PREVIOUS -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
            ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
            ACTION_STOP -> speakControl.stop()
        }

        return super.onStartCommand(intent, flags, startId)
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.tts_status), NotificationManager.IMPORTANCE_LOW)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(headsetReceiver)
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        currentTitle = getString(R.string.app_name)
        currentText = ""
        Log.d(TAG, "Shutdown")
        stopForeground(true)
        if(wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    @SuppressLint("WakelockTimeout")
    fun onEvent(ev: SpeakEvent) {
        if(!ev.isSpeaking && ev.isPaused) {
            Log.d(TAG, "Stop foreground (pause)")
            stopForeground(false)
            if(wakeLock.isHeld) {
                wakeLock.release()
            }

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                buildNotification(playAction)
            }
        }
        else if (ev.isSpeaking) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                buildNotification(pauseAction, true)
            }
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
            }
        }
        else {
            shutdown()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            buildNotification(if (speakControl.isSpeaking) pauseAction else playAction)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun generateAction(icon: Int, title: String, intentAction: String): Notification.Action {
        val intent = Intent(applicationContext, this.javaClass)
        intent.setAction(intentAction)
        val pendingIntent = PendingIntent.getService(applicationContext, 1, intent, 0)
        return Notification.Action.Builder(icon, title, pendingIntent).build()
    }

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
                .addAction(generateAction(android.R.drawable.ic_media_previous, getString(R.string.rewind), ACTION_PREVIOUS))
                .addAction(action)
                .addAction(generateAction(android.R.drawable.ic_media_next, getString(R.string.forward), ACTION_NEXT))
                .addAction(generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD))
                .setOnlyAlertOnce(true)

        style.setShowActionsInCompactView(2)

        val notification = builder.build()
        if(foreground) {
            Log.d(TAG, "Starting foreground")
            startForeground(NOTIFICATION_ID, notification)
        }
        else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }
}