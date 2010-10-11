package net.bible.android.activity;

import net.bible.android.activity.base.ProgressActivityBase;
import net.bible.android.util.CommonUtil;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;
import org.crosswire.common.progress.WorkEvent;
import org.crosswire.common.progress.WorkListener;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/** Prompt user to wait until first bible has downloaded before going to main screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class EnsureBibleDownloaded extends ProgressActivityBase {
	
	private static final String TAG = "EnsureBibleDownloaded";
	
	private int clickCount = 0;

	private WorkListener workListener;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying ensure-bible-downloaded");
        setContentView(R.layout.ensure_bible_downloaded);
    
        if (SwordApi.getInstance().getBibles().size()>0) {
        	gotoMainScreen();
        } else {
        	startMonitoring();
        }
    }
    
    private void startMonitoring() {
    	if (workListener==null) {
			workListener = new WorkListener() {
	
				@Override
				public void workProgressed(WorkEvent ev) {
					if (ev.getJob().isFinished()) {
						downloadComplete(ev.getJob());
					}
				}
	
				@Override
				public void workStateChanged(WorkEvent ev) {
					// ignore this event
				}
			};
			JobManager.addWorkListener(workListener);
    	}
    }

    public void downloadComplete(Progress prog) {
    	Log.i(TAG, "CLICKED");
        if (SwordApi.getInstance().getBibles().size()>0) {
        	gotoMainScreen();
        }

        // can't find downloaded bible, wait a sec and try again
        CommonUtil.pause(2);
        if (SwordApi.getInstance().getBibles().size()>0) {
        	gotoMainScreen();
        } else {
        	if (JobManager.getJobs().size()==0) {
        		runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// something went wrong with the download
						showErrorMsg(getString(R.string.download_complete_no_bibles));
					}
        		});
        	}
        	
        }
    }

    /** user pressed contimue
     */
    public void onContinue(View v) {
    	Log.i(TAG, "CLICKED");
        if (SwordApi.getInstance().getBibles().size()>0) {
        	
        	gotoMainScreen();
        } else {
        	TextView warn = (TextView)findViewById(R.id.waitForBibleNotYet);
        	// if already warned then alternate the wait text
        	if (clickCount++%2==0) {
        		warn.setText(R.string.wait_for_bible_not_yet);        		
        	} else {
        		warn.setText(R.string.please_wait);
        	}
        	
    		warn.setVisibility(View.VISIBLE);
        }
    }
    
    private void gotoMainScreen() {
		Intent intent = new Intent(this, MainBibleActivity.class);
    	startActivity(intent);
    	finish();
    }
    
    @Override
	protected void onStop() {
		super.onStop();
    	JobManager.removeWorkListener(workListener);
	}

}
