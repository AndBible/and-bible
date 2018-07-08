package net.bible.service.device

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews

import net.bible.android.BibleApplication
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.view.activity.download.ProgressStatus

import org.apache.commons.lang3.StringUtils
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener

import java.util.HashMap

/**Show all Progress status
 * see BibleDesktop JobsProgressBar for example use
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class ProgressNotificationManager// only one instance initialised at startup to monitor for JSword Progress events and map them to Android Notifications
private constructor() {

    internal var progressMap: MutableMap<Progress, Notification> = HashMap()

    private var workListener: WorkListener? = null

    private val androidNotificationManager = notificationManager

    private// add it to the NotificationManager
    val notificationManager: NotificationManager
        get() = BibleApplication.getApplication().getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager

    fun initialise() {
        Log.i(TAG, "Initializing")

        workListener = object : WorkListener {

            override fun workProgressed(ev: WorkEvent) {
                val prog = ev.job
                val done = prog.work

                // updating notifications is really slow so we only update the notification manager every 5%
                if (prog.isFinished || done % 5 == 0) {
                    // compose a descriptive string showing job name and current section if relevant
                    var status = StringUtils.left(prog.jobName, 50) + SharedConstants.LINE_SEPARATOR
                    if (!StringUtils.isEmpty(prog.sectionName) && !prog.sectionName.equals(prog.jobName, ignoreCase = true)) {
                        status += prog.sectionName
                    }

                    // update notification view
                    val notification = findOrCreateNotification(prog)
                    notification.contentView.setProgressBar(R.id.status_progress, 100, done, false)
                    notification.contentView.setTextViewText(R.id.status_text, status)

                    // this next line is REALLY slow and is the reason we only update the notification manager every 5%
                    // inform the progress bar of updates in progress
                    androidNotificationManager.notify(prog.hashCode(), notification)

                    if (prog.isFinished) {
                        finished(prog)
                    }
                }
            }

            override fun workStateChanged(ev: WorkEvent) {
                Log.d(TAG, "WorkState changed")
                // we don't care about these events
            }
        }
        JobManager.addWorkListener(workListener)

        Log.d(TAG, "Finished Initializing")
    }

    private fun finished(prog: Progress) {
        Log.d(TAG, "Finished")
        androidNotificationManager.cancel(prog.hashCode())
        progressMap.remove(prog)
    }

    fun close() {
        Log.i(TAG, "Clearing Notifications")
        try {
            // clear map and all Notification objects
            for (prog in progressMap.keys) {
                if (prog.isCancelable) {
                    Log.i(TAG, "Cancelling job")
                    prog.cancel()
                }
                finished(prog)
            }

            // de-register from notifications
            JobManager.removeWorkListener(workListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error tidying up", e)
        }

    }

    /** find the Progress object in our map to the associated Notifications
     *
     * @param prog
     * @return
     */
    private fun findOrCreateNotification(prog: Progress): Notification {
        var notification: Notification? = progressMap[prog]
        if (notification == null) {
            Log.d(TAG, "Creating Notification for progress Hash:" + prog.hashCode())
            // configure the intent
            val intent = Intent(BibleApplication.getApplication(), ProgressStatus::class.java)
            val pendingIntent = PendingIntent.getActivity(BibleApplication.getApplication(), 0, intent, 0)


            notification = Notification(R.drawable.ichthys_alpha, prog.jobName, System.currentTimeMillis())
            notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_AUTO_CANCEL
            notification.contentView = RemoteViews(SharedConstants.PACKAGE_NAME, R.layout.progress_notification)
            notification.contentIntent = pendingIntent
            notification.contentView.setImageViewResource(R.id.status_icon, R.drawable.ichthys)
            notification.contentView.setTextViewText(R.id.status_text, "")
            notification.contentView.setProgressBar(R.id.status_progress, 100, prog.work, false)

            progressMap[prog] = notification

            androidNotificationManager.notify(prog.hashCode(), notification)
        }

        return notification
    }

    companion object {
        private val TAG = "ProgressNotificatnMngr"

        val instance = ProgressNotificationManager()
    }
}
