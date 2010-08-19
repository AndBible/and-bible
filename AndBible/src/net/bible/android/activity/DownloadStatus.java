package net.bible.android.activity;

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
	
	private TextView mStatusTextView;
	private ProgressBar mProgressBar;
	
	private WorkListener workListener;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying "+TAG+" view");
        setContentView(R.layout.download_status);
    
        mStatusTextView =  (TextView)findViewById(R.id.statusText);
        mProgressBar =  (ProgressBar)findViewById(R.id.progressBar);
        
        initialiseView();
        Log.d(TAG, "Finished displaying Search Index view");
    }

    private void initialiseView() {
        
    	Log.i(TAG, "CLICKED");
    	showMsg("Starting download");

		workListener = new WorkListener() {

			@Override
			public void workProgressed(WorkEvent ev) {
				//int total = ev.getJob().getTotalWork();
				try {
					Progress prog = ev.getJob();
					final int done = prog.getWork();
					final String status = prog.getJobName()+"\n"+prog.getSectionName();
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							try {
								mProgressBar.setProgress(done);
								showMsg(status);
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
				Progress prog = ev.getJob();
				final String status = prog.getJobName()+prog.getSectionName();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						showMsg(status);
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
		// TODO Auto-generated method stub
		super.onStop();
    	JobManager.removeWorkListener(workListener);
	}

    private void showMsg(String msg) {
    	mStatusTextView.setText(msg);
    }
    
}
