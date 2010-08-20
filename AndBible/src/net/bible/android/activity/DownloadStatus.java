package net.bible.android.activity;

 import java.util.HashMap;
import java.util.Map;

import net.bible.android.util.ActivityBase;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * see BibleDesktop JobsProgressBar for example use
 * 
 * @author denha1m
 *
 */
public class DownloadStatus extends ActivityBase {
	private static final String TAG = "DownloadStatus";
	
	Map<Progress, ProgressUIControl> progressMap = new HashMap<Progress, ProgressUIControl>();
	
	private LinearLayout progressControlContainer;
	
	private WorkListener workListener;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying "+TAG+" view");
        setContentView(R.layout.download_status);

        progressControlContainer = (LinearLayout)findViewById(R.id.progressControlContainer);
        initialiseView();
        Log.d(TAG, "Finished displaying Search Index view");
    }

    private void initialiseView() {

		workListener = new WorkListener() {

			@Override
			public void workProgressed(WorkEvent ev) {
				//int total = ev.getJob().getTotalWork();
				try {
					final Progress prog = ev.getJob();
					final int done = prog.getWork();
					final String status = prog.getJobName()+"\n"+prog.getSectionName();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							try {
								final ProgressUIControl progressUIControl = findOrCreateUIControl(prog);
								progressUIControl.showMsg(status);
								progressUIControl.showPercent(done);
							} catch (Exception e) {
								Log.e(TAG, "error", e);
							}
						}
					});
				} catch (Exception e) {
					Log.e(TAG, "error", e);
				}
			}

			@Override
			public void workStateChanged(WorkEvent ev) {
				final Progress prog = ev.getJob();
				final String status = prog.getJobName()+prog.getSectionName();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						final ProgressUIControl progressUIControl = findOrCreateUIControl(prog);
						progressUIControl.showMsg(status);
					}
				});
			}
		};
		JobManager.addWorkListener(workListener);
    }

    public void onOkay(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, DownloadStatus.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
    
    @Override
	protected void onStop() {
		super.onStop();
    	JobManager.removeWorkListener(workListener);
	}

    private ProgressUIControl findOrCreateUIControl(Progress prog) {
    	ProgressUIControl uiControl = progressMap.get(prog);
    	if (uiControl == null) {
    		uiControl = new ProgressUIControl();
    		progressMap.put(prog, uiControl);
    		progressControlContainer.addView(uiControl.parent);
    	}
    	
    	return uiControl;
    }

//	<TextView android:id="@+id/statusText" 
//		android:layout_height="wrap_content" 
//		android:layout_width="fill_parent"
//		android:layout_alignParentTop="true" />
//	<ProgressBar android:id="@+id/progressBar" 
//        style="?android:attr/progressBarStyleHorizontal"
//        android:layout_width="300dip"
//        android:layout_height="wrap_content"
//        android:max="100"
//        android:progress="0"
//        android:layout_below="@+id/statusText"/>
    private class ProgressUIControl {
    	LinearLayout parent = new LinearLayout(DownloadStatus.this);
    	TextView status = new TextView(DownloadStatus.this);
    	ProgressBar progressBar = new ProgressBar(DownloadStatus.this, null, android.R.attr.progressBarStyleHorizontal);
    	
    	public ProgressUIControl() {
    		parent.setOrientation(LinearLayout.VERTICAL);
    		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); 
    		parent.addView(status, lp);
    		parent.addView(progressBar, lp);
    		progressBar.setMax(100);
    		showMsg("Starting...");
		}

    	private void showMsg(String msg) {
        	status.setText(msg);
        }
    	
    	private void showPercent(int percent) {
        	progressBar.setProgress(percent);
        }
    	
    }
}
