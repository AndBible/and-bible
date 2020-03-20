/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.service.device.speak

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakEvent
import net.bible.service.device.speak.event.SpeakProgressEvent
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.passage.Verse
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
            val intent = Intent(this, NotificationReceiver::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
            }
            sendBroadcast(intent)
            stop()
        }

        override fun onTaskRemoved(rootIntent: Intent?) {
            Log.d(TAG, "Task removed")
            if(!foreground) {
                stopSelf()
            }
            super.onTaskRemoved(rootIntent)
        }

        private fun stop(removeNotification: Boolean = false) {
            if(!foreground) {
                return
            }

            Log.d(TAG, "STOP_SERVICE")
            wakeLock.release()
            stopForeground(removeNotification)
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
            val action = intent?.action
            Log.d(TAG, "NotificationReceiver onnReceive $intent $action")
            val bookRef = intent?.data?.host
            val osisRef = intent?.data?.path?.removePrefix("/")
            when (action) {
                ACTION_SPEAK_OR_PAUSE -> {
                    if(!speakControl.isPaused && bookRef != null && osisRef!=null) {
                        // if application has been stopped and intent has bible reference,
                        // start playback from the correct position
                        speakControl.speakBible(bookRef, osisRef)
                    }
                    else {
                        speakControl.toggleSpeak()
                    }
                }
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


    private var app = BibleApplication.application
    private var currentTitle = getString(R.string.app_name)
    private var notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var headsetReceiver  = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.getIntExtra("state", 0) == 0 && speakControl.isSpeaking) {
                    speakControl.pause()
                } else if (intent?.getIntExtra("state", 0) == 1 && speakControl.isPaused) {
                    speakControl.continueAfterPause()
                }
            }
        }

    private var currentText = ""

    private fun getString(id: Int): String {
        return BibleApplication.application.getString(id)
    }

    init {
        Log.d(TAG, "Initialize")

        if(instance != null) {
            throw RuntimeException("This class is singleton!")
        }

        instance = this
        DaggerActivityComponent.builder()
                .applicationComponent(BibleApplication.application.applicationComponent)
                .build().inject(this)

        val powerManager = BibleApplication.application.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)

        app.registerReceiver(headsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))

        ABEventBus.getDefault().register(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        Log.d(TAG, "Shutdown")
        currentTitle = getString(R.string.app_name)
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
        }
    }

    fun onEventMainThread(ev: SpeakProgressEvent) {
        if(ev.speakCommand is TextCommand) {
            if(ev.speakCommand.type == TextCommand.TextType.TITLE) {
                currentTitle = ev.speakCommand.text
                if(currentTitle.isEmpty()) {
                    currentTitle = getString(R.string.app_name)
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
        val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, 0)
        return NotificationCompat.Action.Builder(icon, title, pendingIntent).build()
    }

    private val rewindAction = generateAction(android.R.drawable.ic_media_rew, getString(R.string.rewind), ACTION_REWIND)
    private val prevAction = generateAction(android.R.drawable.ic_media_previous, getString(R.string.previous), ACTION_PREVIOUS)
    private val pauseAction = generateAction(android.R.drawable.ic_media_pause, getString(R.string.pause), ACTION_SPEAK_OR_PAUSE)
    private fun getPlayAction(book: Book?, verse: Verse?): NotificationCompat.Action {
        val intent = Intent(app, NotificationReceiver::class.java).apply {
            action = ACTION_SPEAK_OR_PAUSE
         }
        if(book != null && verse != null) {
            intent.data = Uri.parse("bible://${book.initials}/${verse.osisRef}")
        }
        val pendingIntent = PendingIntent.getBroadcast(app, 0, intent, 0)
        return NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, getString(R.string.speak), pendingIntent).build()
    }
    private val nextAction = generateAction(android.R.drawable.ic_media_next, getString(R.string.next), ACTION_NEXT)
    private val forwardAction = generateAction(android.R.drawable.ic_media_ff, getString(R.string.forward), ACTION_FAST_FORWARD)
    private val bibleBitmap = BitmapFactory.decodeResource(app.resources, R.drawable.bible)

    private fun buildNotification(isSpeaking: Boolean) {
        val deletePendingIntent = PendingIntent.getBroadcast(app, 0,
                Intent(app, NotificationReceiver::class.java).apply {
                    action = ACTION_STOP
                }, 0)

        val contentIntent = Intent(app, MainBibleActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(app, 0, contentIntent, 0)
        val style = MediaStyle()
            .setShowActionsInCompactView(2)

        val builder = NotificationCompat.Builder(app, SPEAK_NOTIFICATIONS_CHANNEL)

        builder.setSmallIcon(R.drawable.ichthys_alpha)
                .setLargeIcon(bibleBitmap)
                .setContentTitle(currentTitle)
                .setSubText(speakControl.getStatusText(FLAG_SHOW_ALL))
                .setShowWhen(false)
                .setDeleteIntent(deletePendingIntent)
                .setContentIntent(contentPendingIntent)
                .setStyle(style)
                .addAction(rewindAction)
                .addAction(prevAction)
                .addAction(if(isSpeaking) pauseAction else getPlayAction(
                        speakControl.currentlyPlayingBook, speakControl.currentlyPlayingVerse))
                .addAction(nextAction)
                .addAction(forwardAction)
                .setOnlyAlertOnce(true)

        val sleepTime = speakControl.sleepTimerActivationTime
        if(sleepTime!=null) {
            val minutes = (sleepTime.time - Calendar.getInstance().timeInMillis) / 60000
            builder.setContentText(app.getString(R.string.sleep_timer_active_at, minutes.toString()))
        }
        else {
            builder.setContentText(currentText)
        }


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

    private fun stopForeground(removeNotification: Boolean = false)
    {
       val intent = Intent(app, ForegroundService::class.java)
       intent.action = if(removeNotification) ForegroundService.STOP_FOREGROUND_REMOVE_NOTIFICATION
                       else ForegroundService.STOP_FOREGROUND
       app.startService(intent)
    }
}
