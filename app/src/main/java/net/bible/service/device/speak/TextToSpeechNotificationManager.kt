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

package net.bible.service.device.speak

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import net.bible.android.BibleApplication
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.bookmarks.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import java.util.*
import javax.inject.Inject

@ActivityScope
class TextToSpeechNotificationManager {
    companion object {
        private const val ACTION_UPDATE_NOTIFICATION = "update_notification"
        private const val ACTION_SPEAK_OR_PAUSE="action_speak_or_pause"
        private const val ACTION_REWIND="action_rewind"
        private const val ACTION_PREVIOUS="action_previous"
        private const val ACTION_NEXT="action_next"
        private const val ACTION_FAST_FORWARD="action_fast_forward"
        private const val ACTION_STOP="action_stop"

        private const val SPEAK_NOTIFICATIONS_CHANNEL="speak-notifications"

        private const val NOTIFICATION_ID=1

        private const val WAKELOCK_TAG = "andbible:speak-wakelock"
        private const val TAG = "Speak/TTSService"
        private lateinit var wakeLock: PowerManager.WakeLock

        private var foregroundNotification: Notification? = null

        private var instance: TextToSpeechNotificationManager? = null
        private var foreground = false
    }

    class ForegroundService: Service() {
        companion object {
            const val START_SERVICE="action_start_service"
            const val STOP_FOREGROUND="action_stop_foreground"
            const val STOP_FOREGROUND_REMOVE_NOTIFICATION="action_stop_foreground_remove_notification"
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            when(intent?.action) {
                START_SERVICE -> start()
                STOP_FOREGROUND -> stop()
                STOP_FOREGROUND_REMOVE_NOTIFICATION -> stop(true)
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
            Log.i(TAG, "START_SERVICE")
            startForeground(NOTIFICATION_ID, foregroundNotification!!)
            foreground = true
            wakeLock.acquire()
        }

        override fun onDestroy() {
            CommonUtils.prepareForDestruction()

            // If application is in background (no activity is active) and this service is not foreground either,
            // this service will be stopped by android some time (1 minute when I tested). App itself remains
            // running until OOM killer stops it. Because notification is bound to this service, it will be
            // removed. Thus we need to build notification again. This will be done
            // TextToSpeechNotificationManager via this intent.

            Log.i(TAG, "onDestroy")
            val intent = Intent(this, NotificationReceiver::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
            }
            sendBroadcast(intent)
            stop()
        }

        override fun onTaskRemoved(rootIntent: Intent?) {
            Log.i(TAG, "Task removed")
            if(!foreground) {
                stopSelf()
            }
            super.onTaskRemoved(rootIntent)
        }

        private fun stop(removeNotification: Boolean = false) {
            if(!foreground) {
                return
            }

            Log.i(TAG, "STOP_SERVICE")
            wakeLock.release()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(if(removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
            } else {
                stopForeground(removeNotification)
            }
            foreground = false
        }

        override fun onBind(intent: Intent?): IBinder? {
            return null
        }
    }

    @Inject lateinit var speakControl: SpeakControl

    class NotificationReceiver: BroadcastReceiver() {
        val speakControl: SpeakControl by lazy { instance!!.speakControl }

        override fun onReceive(context: Context?, intent: Intent?) {
            CommonUtils.initializeApp()
            val action = intent?.action
            Log.i(TAG, "NotificationReceiver onReceive $intent $action")
            when (action) {
                ACTION_SPEAK_OR_PAUSE -> speakControl.toggleSpeak(preferLast = true)
                ACTION_FAST_FORWARD -> speakControl.forward()
                ACTION_REWIND -> speakControl.rewind()
                ACTION_PREVIOUS -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                ACTION_NEXT -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                ACTION_STOP -> speakControl.stop()
                ACTION_UPDATE_NOTIFICATION -> {
                    if(speakControl.isPaused) {
                        instance!!.buildNotification(false)
                    }
                }
            }
        }
    }


    private val app get() = BibleApplication.application
    private var currentTitle = getString(R.string.app_name_medium)
    private var notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var headsetReceiver  = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getIntExtra("state", 0) == 0 && speakControl.isSpeaking) {
                    speakControl.pause()
                } else if (intent?.getIntExtra("state", 0) == 1 && speakControl.isPaused) {
                    speakControl.continueLastPosition()
                }
            }
        }

    private var currentText = ""

    private fun getString(id: Int): String {
        return app.getString(id)
    }

    init {
        Log.i(TAG, "Initialize")

        if(instance != null) {
            throw RuntimeException("This class is singleton!")
        }

        instance = this
        DaggerActivityComponent.builder()
                .applicationComponent(app.applicationComponent)
                .build().inject(this)

        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)

        app.registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))

        ABEventBus.register(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !BuildVariant.Appearance.isDiscrete) {
            val channel = NotificationChannel(SPEAK_NOTIFICATIONS_CHANNEL,
                    getString(R.string.notification_channel_tts_status), NotificationManager.IMPORTANCE_LOW).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

    }

    fun destroy() {
        app.unregisterReceiver(headsetReceiver)
        shutdown()
        instance = null
    }

    private fun shutdown() {
        Log.i(TAG, "Shutdown")
        currentTitle = getString(R.string.app_name_medium)
        currentText = ""

        // In case service was no longer foreground, we need do this here.
        if(foreground) {
            stopForeground(true)
        }
        else {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    fun onEventMainThread(ev: SpeakEvent) {
        Log.i(TAG, "SpeakEvent ${ev.speakState}")
        if(!ev.isSpeaking && ev.isPaused) {
            Log.i(TAG, "Stop foreground (pause)")
            buildNotification(false)
            stopForeground()
        }
        else if (ev.isSpeaking) {
            buildNotification(true)
            startForeground()
        }
        else {
            shutdown()
        }
    }

    fun onEventMainThread(ev: SpeakProgressEvent) {
        if(ev.speakCommand is TextCommand) {
            if(ev.speakCommand.type == TextCommand.TextType.TITLE) {
                currentTitle = ev.speakCommand.text
                if(currentTitle.isEmpty()) {
                    currentTitle = getString(R.string.app_name_medium)
                }
            }
            else {
                currentText = ev.speakCommand.text
            }
        }
        buildNotification(speakControl.isSpeaking)
    }

    private fun generateAction(icon: Int, title: String, command: String): NotificationCompat.Action {
        val intent = Intent(app, NotificationReceiver::class.java).apply {
            action = command
         }
        val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }

    private val rewindAction = generateAction(android.R.drawable.ic_media_rew, getString(R.string.rewind), ACTION_REWIND)
    private val prevAction = generateAction(android.R.drawable.ic_media_previous, getString(R.string.previous), ACTION_PREVIOUS)
    private val pauseAction = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_SPEAK_OR_PAUSE)
    private val playAction = run {
        val intent = Intent(app, NotificationReceiver::class.java).apply {
            action = ACTION_SPEAK_OR_PAUSE
        }
        val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, getString(R.string.speak), pendingIntent).build()
    }
    private val nextAction = generateAction(android.R.drawable.ic_media_next, getString(R.string.next), ACTION_NEXT)
    private val forwardAction = generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD)
    private val bibleBitmap = BitmapFactory.decodeResource(app.resources, R.drawable.bible)

    private fun buildNotification(isSpeaking: Boolean) {
        val deletePendingIntent = PendingIntent.getBroadcast(app, 0,
                Intent(app, NotificationReceiver::class.java).apply {
                    action = ACTION_STOP
                }, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val contentIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
        val contentPendingIntent = PendingIntent.getActivity(app, 0, contentIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val style = MediaStyle()
            .setShowActionsInCompactView(2)

        if(!CommonUtils.isDiscrete) {
            MediaButtonHandler.handler?.ms?.sessionToken?.apply { style.setMediaSession(this) }
        }

        val builder = NotificationCompat.Builder(app, SPEAK_NOTIFICATIONS_CHANNEL)

        builder
                .setShowWhen(false)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setStyle(style)
                .addAction(rewindAction)
                .addAction(prevAction)
                .addAction(if(isSpeaking) pauseAction else playAction)
                .addAction(nextAction)
                .addAction(forwardAction)
                .setOnlyAlertOnce(true)


        if(CommonUtils.isDiscrete) {
            builder
                .setSmallIcon(R.drawable.ic_baseline_headphones_24)
                .setContentTitle(getString(R.string.speak))
        } else {
            builder
                .setSmallIcon(R.drawable.ic_ichtys)
                .setLargeIcon(bibleBitmap)
                .setContentTitle(currentTitle)
                .setSubText(speakControl.getStatusText(FLAG_SHOW_ALL))
        }

        val sleepTime = speakControl.sleepTimerActivationTime
        if(sleepTime!=null) {
            val minutes = (sleepTime.time - Calendar.getInstance().timeInMillis) / 60000
            builder.setContentText(app.getString(R.string.sleep_timer_active_at, minutes.toString()))
        }
        else if(!CommonUtils.isDiscrete) {
            builder.setContentText(currentText)
        }

        val notification = builder.build()
        Log.i(TAG, "Updating notification, isSpeaking: $isSpeaking")
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
            Log.i(TAG, "Foreground service started")
        }
        else {
            app.startService(intent)
        }
    }

    private fun stopForeground(removeNotification: Boolean = false)
    {
       val intent = Intent(app, ForegroundService::class.java)
       intent.action = if(removeNotification) ForegroundService.STOP_FOREGROUND_REMOVE_NOTIFICATION
                       else ForegroundService.STOP_FOREGROUND
       app.startService(intent)
    }
}
