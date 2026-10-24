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

package net.bible.service.sword.index

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.BuildVariant
import net.bible.service.common.CALC_NOTIFICATION_CHANNEL
import net.bible.service.common.CommonUtils
import net.bible.service.device.ProgressNotificationManager
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.NavRoutes
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus

private const val TAG = "SearchIndexingService"
private const val NOTIFICATION_ID = 8

/**
 * Foreground service that builds the FTS5 search indexes of books (K6-6: Android indexing runs in a foreground
 * service). Books are built one at a time, first in first out; a request for a book that is already queued or being
 * built is ignored. Status changes (`SCHEDULED`, `CREATING`, `DONE`, `UNDONE`) are made by the index manager itself, so
 * the UI that observes [IndexStatus] is unaffected.
 *
 * The queue is process-wide and independent of the service instance: the service is only the foreground host (the
 * progress notification, and staying alive while there is work). If Android refuses to start it (for instance a
 * background-start restriction), the queue still drains on its own worker thread, just without the notification.
 * A failed build leaves the book `UNDONE` and the queue continues; cancelling (the user deleting the index or the
 * book) is done by the index manager and shows up as a build that ends `UNDONE`.
 */
class SearchIndexingService : Service(), IndexingObserver, WorkListener {

    companion object {
        /** Builds the index of one book, synchronously (the manager completes it before returning). Test seam. */
        @VisibleForTesting var builder: (Book) -> Unit = { book ->
            IndexManagerFactory.getIndexManager().apply {
                indexPolicy = AndroidIndexPolicy()
                scheduleIndexCreation(book)
            }
        }

        private val queue = IndexingQueue(
            resolve = { Books.installed().getBook(it) },
            build = { builder(it) },
            startWorker = { n, b -> Thread(b, n).apply { isDaemon = true }.start() },
        )

        /**
         * Queues the index build of [book] and starts the foreground service (once; later requests while it runs just
         * add to the queue). Does nothing for a book already queued or being built. The book instance is carried so
         * its status is the one reset to `UNDONE` if the build cannot run, and it is built even when it is no longer in
         * the installed registry, so a caller that deletes the document must [dequeue] it.
         */
        fun enqueue(context: Context, book: Book) = enqueue(context, book.initials, book)

        /**
         * Drops a queued, not yet started build of [initials] (status `UNDONE`). A running build is cancelled by the
         * index manager's `deleteIndex` instead.
         */
        fun dequeue(initials: String) = queue.remove(initials)

        private fun enqueue(context: Context, initials: String, instance: Book?) {
            if (!queue.offer(initials, instance)) return
            try {
                val intent = Intent(context, SearchIndexingService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException: the queue keeps draining without a service
                Log.w(TAG, "Could not start the indexing service; indexing $initials in-process", e)
            }
        }

        internal val isBusy: Boolean get() = queue.isBusy
        @VisibleForTesting fun awaitIdleForTest(timeoutMs: Long): Boolean = queue.awaitIdle(timeoutMs)
        @VisibleForTesting fun resetForTest() = queue.detach()
    }

    private val notificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    @Volatile private var currentBook: Book? = null
    private var lastPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        JobManager.addWorkListener(this)
    }

    override fun onDestroy() {
        JobManager.removeWorkListener(this)
        queue.detach(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService requires startForeground within seconds, so first thing.
        try {
            startForeground(NOTIFICATION_ID, notification(queue.currentBook(), null))
        } catch (e: Exception) {
            Log.w(TAG, "Could not enter the foreground; indexing continues without the service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!queue.attach(this)) onIdle()
        return START_NOT_STICKY
    }

    /**
     * Android 15 (API 35) limits `dataSync` foreground services to about 6 hours per day and calls this when the
     * budget is used up; the service must then stop at once or the system raises an exception. The indexing queue is
     * process-wide and independent of the service, so the running build simply continues in-process (without the
     * notification) and the queued books follow; the next [enqueue] starts the service again. The method does not
     * exist before API 35, so on older devices it is never called.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service time limit reached (type $fgsType); indexing continues without the service")
        currentBook = null
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    override fun onBookStarted(book: Book) {
        currentBook = book
        lastPercent = -1
        notificationManager.notify(NOTIFICATION_ID, notification(book, null))
    }

    /** Stops only if still idle, atomically with the queue: a request that landed since keeps the service up. */
    override fun onIdle() {
        queue.runIfIdle {
            currentBook = null
            stopForegroundCompat()
            stopSelf()
        }
    }

    override fun workProgressed(ev: WorkEvent) {
        val book = currentBook ?: return
        val job = ev.job
        if (job.jobID != Progress.CREATE_INDEX.replace("%s", book.initials)) return
        val percent = job.work
        if (percent == lastPercent) return
        lastPercent = percent
        notificationManager.notify(NOTIFICATION_ID, notification(book, percent))
    }

    override fun workStateChanged(ev: WorkEvent) {}

    private fun notification(book: Book?, percent: Int?): Notification {
        val discrete = CommonUtils.isDiscrete
        val app = BibleApplication.application // Resources follow `locale_pref` live (F114); the Service's own do not
        val intent = NavHostComposeActivity.intentFor(this, NavRoutes.READING).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return NotificationCompat.Builder(
            this,
            if (BuildVariant.Appearance.isDiscrete) CALC_NOTIFICATION_CHANNEL
            else ProgressNotificationManager.PROGRESS_NOTIFICATION_CHANNEL
        )
            .setSmallIcon(if (discrete) R.drawable.ic_calc_24 else R.drawable.ic_ichtys)
            .setContentTitle(app.getString(if (discrete) R.string.install_zip_module_discrete else R.string.search_indexing_notification_title))
            .apply {
                if (book != null) setContentText(app.getString(R.string.creating_index_for, book.name))
                if (percent != null) setProgress(100, percent, false) else if (book != null) setProgress(100, 0, true)
            }
            .setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
