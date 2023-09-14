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

package net.bible.service.cloudsync

import android.app.NotificationManager
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
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils

private const val SYNC_NOTIFICATION_ID=2
const val SYNC_NOTIFICATION_CHANNEL="sync-notifications"
private const val WAKELOCK_TAG = "andbible:sync-wakelock"

class CloudSyncEvent(val running: Boolean = false)

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
            }
        }
        return START_STICKY
    }

    val scope = CoroutineScope(Dispatchers.IO)

    private fun synchronize() {
        Log.i(TAG, "Synchronize started")
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
        if (wakeLock.isHeld) {
            throw RuntimeException("Wakelock already held, double-synchronize")
        }

        val builder = NotificationCompat.Builder(
            this,
            if(BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else SYNC_NOTIFICATION_CHANNEL)


        builder
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setContentTitle(getString(R.string.synchronizing))

        if(CommonUtils.isDiscrete) {
            builder.setSmallIcon(R.drawable.ic_calc_24)
        } else {
            builder.setSmallIcon(R.drawable.ic_backup_restore_24dp)
        }

        val notification = builder.build()
        notificationManager.notify(SYNC_NOTIFICATION_ID, notification)

        startForeground(SYNC_NOTIFICATION_ID, notification)
        wakeLock.acquire(5*60*1000) // 5 minutes

        scope.launch {
            ABEventBus.post(CloudSyncEvent(true))
            CloudSync.synchronize()
            CloudSync.waitUntilFinished(true)
            Log.i(TAG, "Synchronize finished")
            if(wakeLock.isHeld) {
                wakeLock.release()
            }
            ABEventBus.post(CloudSyncEvent(false))
            stop()
        }
    }
    private fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }
}
