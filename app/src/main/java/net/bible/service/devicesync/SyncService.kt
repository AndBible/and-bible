/*
 * Copyright (c) 2023 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.devicesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils

private const val TAG = "SyncService"
private const val NOTIFICATION_ID=2
private const val SYNC_NOTIFICATION_CHANNEL="sync-notifications"
private const val WAKELOCK_TAG = "andbible:sync-wakelock"

class SyncService: Service() {
    companion object {
        const val START_SERVICE="action_start_service"
    }
    private val app get() = application
    private val powerManager get() = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val notificationManager get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            START_SERVICE -> synchronize()
            else -> {
                Log.w(TAG, "Unknown command $intent ${intent?.action}")
                stop()
            }
        }
        return START_STICKY
    }

    val scope = CoroutineScope(Dispatchers.IO)

    private fun synchronize() {
        Log.i(TAG, "Synchronize started")
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(BuildVariant.Appearance.isDiscrete) {
                CommonUtils.createDiscreteNotificationChannel()
            } else {
                val channel = NotificationChannel(
                    SYNC_NOTIFICATION_CHANNEL,
                    getString(R.string.device_sync), NotificationManager.IMPORTANCE_LOW
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val builder = NotificationCompat.Builder(
            this,
            if(BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else SYNC_NOTIFICATION_CHANNEL)


        val contentIntent = application.packageManager.getLaunchIntentForPackage(application.packageName)
        val contentPendingIntent = PendingIntent.getActivity(app, 0, contentIntent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        builder
            .setShowWhen(false)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)

        if(CommonUtils.isDiscrete) {
            builder
                .setSmallIcon(R.drawable.ic_baseline_headphones_24)
                .setContentTitle(getString(R.string.speak))
        } else {
            builder
                .setSmallIcon(R.drawable.ic_sync_white_24dp)
                .setContentTitle(getString(R.string.synchronizing))
        }
        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)

        startForeground(NOTIFICATION_ID, notification)
        wakeLock.acquire(100)

        scope.launch {
            DeviceSynchronize.synchronize()
            DeviceSynchronize.waitUntilFinished()
            Log.i(TAG, "Synchronize finished")
            wakeLock.release()
            stop()
        }
    }
    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }
}
