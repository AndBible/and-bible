/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.cloudsync.documents

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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.service.cloudsync.SYNC_NOTIFICATION_CHANNEL
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils
import org.crosswire.jsword.book.Books
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val DOC_SYNC_NOTIFICATION_ID = 6
private const val WAKELOCK_TAG = "andbible:docsync-wakelock"
private const val WAKELOCK_TIMEOUT_MS = 30L * 60 * 1000 // 30 minutes
private const val TAG = "DocumentSyncService"

/**
 * Posted on [ABEventBus] as document transfers progress. [running] is false once the
 * queue is drained. [current]/[total] count completed-or-in-progress vs total ops in
 * the active batch; [currentName] is the document being transferred (null at start/end).
 */
class DocumentSyncProgressEvent(
    val running: Boolean,
    val current: Int,
    val total: Int,
    val currentName: String?,
)

/**
 * Foreground service that runs all document-sync transfers (push/download) off the UI
 * thread, with a single per-document progress notification. Callers enqueue ops via
 * [start]; a single consumer coroutine drains the queue and posts [DocumentSyncProgressEvent].
 */
class DocumentSyncService : Service() {
    companion object {
        private const val START = "action_document_sync"
        private const val EXTRA_PUSH = "pushInitials"
        private const val EXTRA_DOWNLOAD = "downloadInitials"
        private const val EXTRA_REMOVE = "removeInitials"
        private const val EXTRA_PURGE = "purgeInitials"
        private const val EXTRA_UNINSTALL = "uninstallInitials"

        /**
         * Enqueues a batch (pushes/downloads/removals/purges/uninstalls) and starts the foreground
         * service. Manual callers pass explicit ops (they bypass the wifi-only guard by design); auto
         * callers must apply their guards first. No-op if all lists are empty.
         */
        fun start(
            context: Context,
            pushInitials: List<String>,
            downloadInitials: List<String>,
            removeInitials: List<String> = emptyList(),
            purgeInitials: List<String> = emptyList(),
            uninstallInitials: List<String> = emptyList(),
        ) {
            if (pushInitials.isEmpty() && downloadInitials.isEmpty() && removeInitials.isEmpty() &&
                purgeInitials.isEmpty() && uninstallInitials.isEmpty()) return
            val intent = Intent(context, DocumentSyncService::class.java).apply {
                action = START
                putStringArrayListExtra(EXTRA_PUSH, ArrayList(pushInitials))
                putStringArrayListExtra(EXTRA_DOWNLOAD, ArrayList(downloadInitials))
                putStringArrayListExtra(EXTRA_REMOVE, ArrayList(removeInitials))
                putStringArrayListExtra(EXTRA_PURGE, ArrayList(purgeInitials))
                putStringArrayListExtra(EXTRA_UNINSTALL, ArrayList(uninstallInitials))
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                // e.g. Android 12+ background-start restriction or 14+ dataSync quota.
                Log.e(TAG, "Could not start document sync service", e)
            }
        }

        /**
         * Stops the service, cancelling any in-flight drain (onDestroy → scope.cancel()). Used on
         * sign-out so transfers tied to the disconnected cloud account don't keep running against it.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, DocumentSyncService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ConcurrentLinkedQueue<DocumentSyncOp>()
    private val active = AtomicBoolean(false)
    private val total = AtomicInteger(0)
    private val done = AtomicInteger(0)

    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val powerManager get() = getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    /** True only once [startForeground] has actually succeeded. Read on IO, written on main. */
    @Volatile private var isForeground = false
    /** Most recent start id, so teardown via [stopSelfResult] can't kill a just-started batch. */
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action != START) { if (!active.get()) stopSelfSafe(startId); return START_NOT_STICKY }
        // We were started via startForegroundService(): Android requires a matching startForeground()
        // within ~5s for EVERY such start — including a batch that arrives while an earlier drain is
        // still running (the non-fresh path below) — or it kills the process with
        // ForegroundServiceDidNotStartInTimeException. startForeground is idempotent on the same
        // notification id, so calling it again for a non-fresh batch (or an empty one) is safe.
        startForegroundSafe()
        val push = intent.getStringArrayListExtra(EXTRA_PUSH) ?: arrayListOf()
        val download = intent.getStringArrayListExtra(EXTRA_DOWNLOAD) ?: arrayListOf()
        val remove = intent.getStringArrayListExtra(EXTRA_REMOVE) ?: arrayListOf()
        val purge = intent.getStringArrayListExtra(EXTRA_PURGE) ?: arrayListOf()
        val uninstall = intent.getStringArrayListExtra(EXTRA_UNINSTALL) ?: arrayListOf()
        val ops = buildDocumentSyncOps(push, download, remove, purge, uninstall)
        if (ops.isEmpty()) { if (!active.get()) stopSelfSafe(startId); return START_NOT_STICKY }

        // fresh == this batch starts a new drain session (no drain currently running).
        val fresh = active.compareAndSet(false, true)
        if (fresh) { done.set(0); total.set(0) }
        queue.addAll(ops)
        total.addAndGet(ops.size)
        if (fresh) {
            drain()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafe() {
        try {
            startForeground(DOC_SYNC_NOTIFICATION_ID, buildNotification(null))
            isForeground = true
        } catch (e: Exception) {
            // Android 14+ may refuse a dataSync FGS once the daily quota is exhausted. We never
            // went foreground, so don't pretend to be one on teardown (isForeground stays false).
            // Processing continues on the IO scope as a best effort; the OS may still reclaim the
            // process while backgrounded (device-dependent), which the wakelock partially mitigates.
            Log.e(TAG, "Could not start foreground; processing in background", e)
        }
        acquireWakeLock()
    }

    private fun drain() = scope.launch {
        ABEventBus.post(DocumentSyncProgressEvent(true, 0, total.get(), null))
        // The start id this drain session is responsible for. Captured while we still own the
        // queue (active == true): a fresh start() arriving after we relinquish advances
        // lastStartId beyond this value, so stopSelfResult(stopId) then returns false and won't
        // tear down that newer batch. Using the live lastStartId here instead would let a stale
        // teardown stop a just-started drain (it gets overwritten by onStartCommand on its first line).
        var stopId = lastStartId
        while (true) {
            val op = queue.poll()
            if (op == null) {
                // No more work: relinquish ownership, then re-check for a late enqueue.
                stopId = lastStartId
                active.set(false)
                if (queue.peek() == null) break
                if (!active.compareAndSet(false, true)) break // another start() took over
                continue
            }
            val current = done.get() + 1
            val totalNow = total.get()
            updateNotification(op, current, totalNow)
            ABEventBus.post(DocumentSyncProgressEvent(true, current, totalNow, op.initials))
            lastDownloadPct = -1
            try {
                when (op) {
                    is DocumentSyncOp.Push -> Books.installed().getBook(op.initials)?.let { DocumentSync.pushDocument(it) }
                    is DocumentSyncOp.Download -> DocumentSync.downloadAndInstall(op.initials) { downloaded, totalBytes ->
                        updateDownloadProgress(op, current, totalNow, downloaded, totalBytes)
                    }
                    is DocumentSyncOp.Remove -> DocumentSync.removeFromCloud(op.initials)
                    is DocumentSyncOp.Purge -> DocumentSync.purgeTombstone(op.initials)
                    is DocumentSyncOp.Uninstall -> DocumentSync.uninstallLocal(op.initials)
                }
            } catch (e: Exception) {
                if (isTransientNetworkError(e)) {
                    // Transient connectivity failure (timeout, dropped connection). Not an app
                    // error: the op is retried next sync (its timestamp only advances on success),
                    // so don't alarm the user with the "error/Report" notification for a network
                    // blip. Mirrors CloudSync's IOException handling for the database sync.
                    Log.w(TAG, "Document sync op failed (network); will retry next sync: ${op.initials}", e)
                } else {
                    Log.e(TAG, "Document sync op failed: ${op.initials}", e)
                    ABEventBus.post(BibleApplication.ErrorNotificationEvent(R.string.sync_error))
                }
            }
            done.incrementAndGet()
        }
        // Refresh the cloud-listing cache so the management view shows the new state even if it
        // isn't open to run its own scan (e.g. after auto-upload on install).
        try { DocumentSync.refreshCache() } catch (e: Exception) { Log.e(TAG, "Cache refresh failed", e) }
        ABEventBus.post(DocumentSyncProgressEvent(false, done.get(), total.get(), null))
        stopSelfSafe(stopId)
    }

    private fun notificationChannel(): String =
        if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL else SYNC_NOTIFICATION_CHANNEL

    /**
     * Builds the sync notification. [progressPct] (0-100) drives a determinate progress bar when the
     * download size is known; otherwise an ongoing op with text shows an indeterminate bar, and the
     * initial no-text notification shows no bar at all.
     */
    private fun buildNotification(contentText: String?, progressPct: Int? = null) =
        NotificationCompat.Builder(this, notificationChannel())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSilent(true)
            .setOngoing(true)
            .setSmallIcon(if (CommonUtils.isDiscrete) R.drawable.ic_calc_24 else R.drawable.ic_syncdb_24dp)
            .setContentTitle(getString(R.string.document_sync_notification_title))
            .apply {
                if (contentText != null) setContentText(contentText)
                when {
                    // Determinate bar once we know the download size and a byte percentage.
                    progressPct != null -> setProgress(100, progressPct, false)
                    // Indeterminate: per-document byte progress isn't yet available (op start, push,
                    // remove), and an op count would sit at 100% for a single document.
                    contentText != null -> setProgress(0, 0, true)
                }
            }
            .build()

    /** Direction-specific progress line, e.g. "Downloading KJV (2/5)". */
    private fun opNotificationText(op: DocumentSyncOp, current: Int, total: Int): String {
        val resId = when (op) {
            is DocumentSyncOp.Download -> R.string.document_sync_downloading
            is DocumentSyncOp.Push -> R.string.document_sync_uploading
            is DocumentSyncOp.Remove -> R.string.document_sync_removing
            // Purging deletes the removed-document marker — also a "removing from cloud" action.
            is DocumentSyncOp.Purge -> R.string.document_sync_removing
            // Uninstall removes the local copy after a remote removal — also "removing".
            is DocumentSyncOp.Uninstall -> R.string.document_sync_removing
        }
        return getString(resId, op.initials, current, total)
    }

    private fun updateNotification(op: DocumentSyncOp, current: Int, total: Int) {
        notificationManager.notify(DOC_SYNC_NOTIFICATION_ID, buildNotification(opNotificationText(op, current, total)))
    }

    /** Last whole-percent shown for the in-progress download, to skip redundant notification posts. */
    private var lastDownloadPct = -1

    /**
     * Updates the notification with download progress. Drives a determinate progress bar with the
     * download percentage when the total size is known; otherwise (size unknown) leaves the prior
     * indeterminate notification untouched. Posts only when the whole percent changes, so frequent
     * byte callbacks don't spam the notifier.
     */
    private fun updateDownloadProgress(op: DocumentSyncOp, current: Int, total: Int, downloaded: Long, totalBytes: Long) {
        if (totalBytes <= 0) return
        val pct = (downloaded.coerceAtMost(totalBytes) * 100 / totalBytes).toInt()
        if (pct == lastDownloadPct) return
        lastDownloadPct = pct
        notificationManager.notify(DOC_SYNC_NOTIFICATION_ID, buildNotification(opNotificationText(op, current, total), pct))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).also {
            it.acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopSelfSafe(stopId: Int) {
        if (active.get()) return
        releaseWakeLock()
        if (isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
            isForeground = false
        }
        // stopSelfResult (not bare stopSelf) so a START intent delivered after this drain finished
        // but before AMS destroys us cancels the teardown — otherwise onDestroy()'s scope.cancel()
        // would kill the freshly-started batch's in-flight transfer. stopId is the id of the drain
        // session being torn down, not the live lastStartId, so a newer start is never matched.
        stopSelfResult(stopId)
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
