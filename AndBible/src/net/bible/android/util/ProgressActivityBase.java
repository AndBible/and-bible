package net.bible.android.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;

import org.apache.commons.lang.StringUtils;
import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressActivityBase extends ActivityBase {

	private Map<Progress, ProgressUIControl> progressMap = new HashMap<Progress, ProgressUIControl>();
	private LinearLayout progressControlContainer;

	private WorkListener workListener;
	private Queue<Progress> progressNotificationQueue = new ConcurrentLinkedQueue<Progress>();

	private static final String TAG = "ProgressActivityBase";
	
	/** Called when the activity is first created. */
    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.i(TAG, "Displaying "+TAG+" view");

        progressControlContainer = (LinearLayout)findViewById(R.id.progressControlContainer);
        initialiseView();
    }

    private void initialiseView() {

    	Set<Progress> jobs = JobManager.getJobs();
    	for (Progress job : jobs) {
    		findOrCreateUIControl(job);
    	}

        // allow call back and continuation in the ui thread after JSword has been initialised
    	final Handler uiHandler = new Handler();
    	final Runnable uiUpdaterRunnable = new Runnable() {
			@Override
			public void run() {
    			Progress prog = progressNotificationQueue.poll();
    			if (prog!=null) {
    				updateProgress(prog);
    			}
			}
        };

        // listen for Progress changes and call the above Runnable to update the ui
		workListener = new WorkListener() {
			@Override
			public void workProgressed(WorkEvent ev) {
				callUiThreadUpdateHandler(ev);
			}

			@Override
			public void workStateChanged(WorkEvent ev) {
				callUiThreadUpdateHandler(ev);
			}
			private void callUiThreadUpdateHandler(WorkEvent ev) {
				Progress prog = ev.getJob();
				progressNotificationQueue.offer(prog);
    			// switch back to ui thread to continue
    			uiHandler.post(uiUpdaterRunnable);
			}
		};
		JobManager.addWorkListener(workListener);
    }

    /** virtual method called on ui thread to update progress.  Can be overridden for subclass specific ui updates br make sure this method is called to update progres controls
     */
    protected void updateProgress(Progress prog) {
		int done = prog.getWork();
		String status = getStatusDesc(prog);

		final ProgressUIControl progressUIControl = findOrCreateUIControl(prog);
		progressUIControl.showMsg(status);
		progressUIControl.showPercent(done);
    }

    /** format a descriptive string from a Progress object
     * 
     * @param prog
     * @return
     */
	protected String getStatusDesc(Progress prog) {
		// compose a descriptive string showing job name and current section if relevant
		String status = prog.getJobName()+SharedConstants.LINE_SEPARATOR;
		if (!StringUtils.isEmpty(prog.getSectionName()) && !prog.getSectionName().equalsIgnoreCase(prog.getJobName())) {
			status += prog.getSectionName();
		}
		return status;
	}

	/** get a UI control for the current prog from the previously created controls, or create one
	 * 
	 * @param prog
	 * @return
	 */
	protected ProgressUIControl findOrCreateUIControl(Progress prog) {
		ProgressUIControl uiControl = progressMap.get(prog);
		if (uiControl == null) {
			uiControl = new ProgressUIControl();
			progressMap.put(prog, uiControl);
			progressControlContainer.addView(uiControl.parent);
			uiControl.showMsg(prog.getJobName());
			uiControl.showPercent(prog.getWork());
		}
		
		return uiControl;
	}
	
    @Override
	protected void onStop() {
		super.onStop();
    	JobManager.removeWorkListener(workListener);
	}


    /** contains a TextView desc and ProgressBar for a single Job
     */
    class ProgressUIControl {
    	LinearLayout parent = new LinearLayout(ProgressActivityBase.this);
    	TextView status = new TextView(ProgressActivityBase.this);
    	ProgressBar progressBar = new ProgressBar(ProgressActivityBase.this, null, android.R.attr.progressBarStyleHorizontal);
    	
    	public ProgressUIControl() {
    		parent.setOrientation(LinearLayout.VERTICAL);
    		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); 
    		parent.addView(status, lp);
    		parent.addView(progressBar, lp);
    		progressBar.setMax(100);
    		showMsg("Starting...");
		}

    	void showMsg(String msg) {
        	status.setText(msg);
        }
    	
    	void showPercent(int percent) {
   			progressBar.setIndeterminate(percent==0);
        	progressBar.setProgress(percent);
        }
    }
}