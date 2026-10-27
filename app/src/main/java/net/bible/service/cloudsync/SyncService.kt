/*
 * Copyright (c) 2023-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import org.koin.android.ext.android.inject
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import net.bible.sharedcore.log.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils

private const val SYNC_NOTIFICATION_ID=2
const val SYNC_NOTIFICATION_CHANNEL="sync-notifications"
private const val WAKELOCK_TAG = "andbible:sync-wakelock"

class SyncService: Service() {
    private val cloudSync: CloudSync by inject()
    private val scheduler: net.bible.android.platform.AndroidSyncScheduler by inject()
    private val settings: net.bible.sharedcore.platform.AppSettings by inject()

    companion object {
        const val START_SERVICE="action_start_service"
    }
    private val app get() = application
    private val powerManager get() = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val notificationManager get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Guard starts and IO cleanup together so an old run cannot stop a new one.
    private var syncing = false

    @Synchronized
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Acknowledge every foreground-service start before branching or dispatching IO work.
            startForeground(SYNC_NOTIFICATION_ID, buildSyncNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Foreground service entry failed", e)
            if (!syncing) finish(false, false, null)
            return START_NOT_STICKY
        }
        when(intent?.action) {
            START_SERVICE -> if (!syncing) {
                syncing = true
                synchronize()
            }
            else -> {
                Log.w(TAG, "Unknown command $intent ${intent?.action}")
                if (!syncing) stop()
            }
        }
        return START_NOT_STICKY
    }

    val scope = CoroutineScope(Dispatchers.IO)

    /** Builds the foreground sync notification. Text from the Application, whose Resources follow `locale_pref` live (F114); the Service's own context does not. */
    internal fun buildSyncNotification(): Notification {
        val builder = NotificationCompat.Builder(
            this,
            if(BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else SYNC_NOTIFICATION_CHANNEL)


        builder
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setContentTitle(app.getString(R.string.synchronizing))

        if(CommonUtils.isDiscrete) {
            builder.setSmallIcon(R.drawable.ic_calc_24)
        } else {
            builder.setSmallIcon(R.drawable.ic_syncdb_24dp)
        }

        return builder.build()
    }

    private fun synchronize() {
        scope.launch {
            var succeeded = false
            var jobStarted = false
            var wakeLock: PowerManager.WakeLock? = null
            try {
                Log.i(TAG, "Synchronize started")
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
                wakeLock.acquire(5 * 60 * 1000)
                jobStarted = true
                succeeded = runDatabaseSyncJob(cloudSync, settings)
                if (succeeded) Log.i(TAG, "Synchronize finished")
            } catch (e: Exception) {
                Log.e(TAG, "Synchronization or foreground service entry failed", e)
            } finally {
                finish(succeeded, jobStarted, wakeLock)
            }
        }
    }

    @Synchronized
    private fun finish(succeeded: Boolean, jobStarted: Boolean, wakeLock: PowerManager.WakeLock?) {
        // Release every owner even if a listener or cleanup operation throws.
        try {
            // The job clears the running flag itself; only a failure before it started needs it here.
            if (!jobStarted) cloudSync.notifySyncRunning(false)
        } finally {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } finally {
                try {
                    try {
                        notificationManager.cancel(SYNC_NOTIFICATION_ID)
                    } finally {
                        stop()
                    }
                } finally {
                    try {
                        scheduler.complete(succeeded)
                    } finally {
                        syncing = false
                    }
                }
            }
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
