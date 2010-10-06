package net.bible.android.device;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.SharedConstants;
import net.bible.android.activity.ProgressStatus;
import net.bible.android.activity.R;
import net.bible.android.application.BibleApplication;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

/**Show all Progress status
 * see BibleDesktop JobsProgressBar for example use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ProgressNotificationManager {
	private static final String TAG = "ProgressNotificationManager";
	
	Map<Progress, Notification> progressMap = new HashMap<Progress, Notification>();
	
	private WorkListener workListener;

	private static final ProgressNotificationManager singleton = new ProgressNotificationManager();
	
	// only one instance initialised at startup to monitor for JSword Progress events and map them to Android Notifications
	private ProgressNotificationManager() {
	}
	
	public static ProgressNotificationManager getInstance() {
		return singleton;
	}
	
    public void initialise() {
        Log.i(TAG, "Initializing");

		workListener = new WorkListener() {

			@Override
			public void workProgressed(WorkEvent ev) {
				final Progress prog = ev.getJob();
				final int done = prog.getWork();
				
				// compose a descriptive string showing job name and current section if relevant
				String status = StringUtils.left(prog.getJobName(),50)+SharedConstants.LINE_SEPARATOR;
				if (!StringUtils.isEmpty(prog.getSectionName()) && !prog.getSectionName().equalsIgnoreCase(prog.getJobName())) {
					status += prog.getSectionName();
				}

				// update notification view
				final Notification notification = findOrCreateNotification(prog);
				notification.contentView.setProgressBar(R.id.status_progress, 100, done, false);
				notification.contentView.setTextViewText(R.id.status_text, status);

				// inform the progress bar of updates in progress
                getNotificationManager().notify(prog.hashCode(), notification);
                
				if (prog.isFinished()) {
					finished(prog);
				}
			}

			@Override
			public void workStateChanged(WorkEvent ev) {
				Log.d(TAG, "WorkState changed");
				// we don't care about these events
			}
		};
		JobManager.addWorkListener(workListener);

        Log.d(TAG, "Finished Initializing");
    }
    
    private void finished(Progress prog) {
		Log.d(TAG, "Finished");
		getNotificationManager().cancel(prog.hashCode());
		progressMap.remove(prog);
    }
    
	public void close() {
		Log.i(TAG,"Clearing Notifications");
		try {
			// clear map and all Notification objects
		    for (Progress prog : progressMap.keySet()) {
		    	if (prog.isCancelable()) {
		    		Log.i(TAG,"Cancelling job");
		    		prog.cancel();
		    	}
		    	finished(prog);
		    }
	
		    // de-register from notifications
		    JobManager.removeWorkListener(workListener);
		} catch (Exception e) {
			Log.e(TAG, "Error tidying up", e);
		}
	}

	/** find the Progress object in our map to the associated Notifications
	 * 
	 * @param prog
	 * @return
	 */
    private Notification findOrCreateNotification(Progress prog) {
    	Notification notification = progressMap.get(prog);
    	if (notification == null) {
			Log.d(TAG, "Creating Notification for progress Hash:"+prog.hashCode());
    		// configure the intent
            Intent intent = new Intent(BibleApplication.getApplication(), ProgressStatus.class);
            final PendingIntent pendingIntent = PendingIntent.getActivity(BibleApplication.getApplication(), 0, intent, 0);

        	notification = new Notification(R.drawable.bible, prog.getJobName(), System.currentTimeMillis());
            notification.flags = notification.flags | Notification.FLAG_ONGOING_EVENT | Notification.FLAG_AUTO_CANCEL;
            notification.contentView = new RemoteViews(SharedConstants.PACKAGE_NAME, R.layout.progress_notification);
            notification.contentIntent = pendingIntent;
            notification.contentView.setImageViewResource(R.id.status_icon, R.drawable.bible);
            notification.contentView.setTextViewText(R.id.status_text, "");
            notification.contentView.setProgressBar(R.id.status_progress, 100, prog.getWork(), false);

    		progressMap.put(prog, notification);
    		
            getNotificationManager().notify(prog.hashCode(), notification);    	
    	}
    	
    	return notification;
    }

    private NotificationManager getNotificationManager() {
		// add it to the NotificationManager
		return  (NotificationManager) BibleApplication.getApplication().getSystemService(BibleApplication.getApplication().NOTIFICATION_SERVICE);

    }
}
