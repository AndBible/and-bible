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

package net.bible.android.view.activity.base;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;

import org.apache.commons.lang3.StringUtils;
import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
/** 
 * Base class for any screen that shows job progress indicators
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class ProgressActivityBase extends CustomTitlebarActivityBase {

	private Map<Progress, ProgressUIControl> progressMap = new HashMap<>();
	private LinearLayout progressControlContainer;

	private WorkListener workListener;
	private Queue<Progress> progressNotificationQueue = new ConcurrentLinkedQueue<Progress>();

	private TextView taskKillWarningView;
	private TextView noTasksMessageView;
	
	private static final String TAG = "ProgressActivityBase";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		applyTheme();
	}

	/** Wait until subclass has setContentView before looking for controls. */
    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "Displaying "+TAG+" view");

        progressControlContainer = (LinearLayout)findViewById(R.id.progressControlContainer);
        initialiseView();
    }

    private void initialiseView() {
    	// prepare to show no tasks msg
    	noTasksMessageView = (TextView)findViewById(R.id.noTasksRunning);
    	taskKillWarningView = (TextView)findViewById(R.id.progressStatusMessage);

    	Iterator<Progress> jobsIterator = JobManager.iterator();
    	while (jobsIterator.hasNext()) {
    		Progress job = jobsIterator.next();
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

		// give new jobs a chance to start then show 'No Job' msg if nothing running
		uiHandler.postDelayed(
			new Runnable() {
				@Override
				public void run() {
					if (!JobManager.iterator().hasNext()) {
						showNoTaskMsg(true);				
					}
				}
			}, 4000);
    }

    /** virtual method called on ui thread to update progress.  Can be overridden for subclass specific ui updates br make sure this method is called to update progres controls
     */
    protected void updateProgress(Progress prog) {
    	// if this is called then ensure the no tasks msg is not also displayed
    	showNoTaskMsg(false);
    	
		int done = prog.getWork();
		String status = getStatusDesc(prog);

		final ProgressUIControl progressUIControl = findOrCreateUIControl(prog);
		progressUIControl.showMsg(status);
		progressUIControl.showPercent(done);
		
		if (prog.isFinished() && !progressUIControl.isFinishNotified) {
			Log.i(TAG, "Job finished:"+prog.getJobName());
			progressUIControl.isFinishNotified = true;
			jobFinished(prog);
		}
    }
    
    protected void jobFinished(Progress job) {
    	// do nothing by default
    }
    
    /** helper method that returns true if alll jobs are finished
     * 
     * @return true if all jobs finished or no jobs
     */
    protected boolean isAllJobsFinished() {
    	Iterator<Progress> jobsIterator = JobManager.iterator();
    	while (jobsIterator.hasNext()) {
    		Progress job = jobsIterator.next();
			if (!job.isFinished()) {
				return false;
			}
		}
		return true;
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

    protected void hideButtons() {
    	View buttonPanel = findViewById(R.id.button_panel);
    	if (buttonPanel!=null) {
    		buttonPanel.setVisibility(View.INVISIBLE);
    	}
    }
    protected void setMainText(String text) {
    	((TextView)findViewById(R.id.progressStatusMessage)).setText(text);
    }
    
    private void showNoTaskMsg(boolean bShow) {
		if (noTasksMessageView!=null && taskKillWarningView!=null) {
	    	if (bShow) {
	   			noTasksMessageView.setVisibility(View.VISIBLE);
	        	// if the no-tasks msg is show then hide the warning relating to running tasks
	    		taskKillWarningView.setVisibility(View.INVISIBLE);
	    	} else {
	    		noTasksMessageView.setVisibility(View.GONE);
	    		taskKillWarningView.setVisibility(View.VISIBLE);
	    	}
		}
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
	protected void onPause() {
		super.onPause();
    	JobManager.removeWorkListener(workListener);
	}


    /** contains a TextView desc and ProgressBar for a single Job
     */
    class ProgressUIControl {
    	LinearLayout parent = new LinearLayout(ProgressActivityBase.this);
    	TextView status = new TextView(ProgressActivityBase.this);
    	ProgressBar progressBar = new ProgressBar(ProgressActivityBase.this, null, android.R.attr.progressBarStyleHorizontal);
    	boolean isFinishNotified;
    	
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
