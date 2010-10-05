package net.bible.android.activity;

 import java.util.Set;

import net.bible.android.util.ProgressActivityBase;

import org.crosswire.common.progress.JobManager;
import org.crosswire.common.progress.Progress;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**Show all Progress status
 * see BibleDesktop JobsProgressBar for example use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ProgressStatus extends ProgressActivityBase {
	private static final String TAG = "ProgressStatus";
	
	private TextView taskKillWarningView;
	private TextView noTasksMessageView;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying "+TAG+" view");
        setContentView(R.layout.progress_status);

        initialiseView();
        Log.d(TAG, "Finished displaying Search Index view");
    }

    private void initialiseView() {

    	// prepare to show no tasks msg
    	noTasksMessageView = (TextView)findViewById(R.id.noTasksRunning);
    	taskKillWarningView = (TextView)findViewById(R.id.taskKillWarning);

    	Set<Progress> jobs = JobManager.getJobs();
    	showNoTaskMsg(jobs.size()==0);
    }

    /** virtual method called on ui thread to update progress
     */
    @Override
    protected void updateProgress(Progress prog) {
    	super.updateProgress(prog);

    	// if this is called then ensure the no tasks msg is not also displayed
    	showNoTaskMsg(false);
    }

    private void showNoTaskMsg(boolean bShow) {
    	if (bShow) {
    		noTasksMessageView.setVisibility(View.VISIBLE);
        	// if the no-tasks msg is show then hide the warning relating to running tasks
    		taskKillWarningView.setVisibility(View.INVISIBLE);
    	} else {
    		noTasksMessageView.setVisibility(View.GONE);
    		taskKillWarningView.setVisibility(View.VISIBLE);
    	}
    }
    
    public void onOkay(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, ProgressStatus.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
