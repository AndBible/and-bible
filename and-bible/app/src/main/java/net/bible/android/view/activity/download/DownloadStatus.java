package net.bible.android.view.activity.download;

 import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.ProgressActivityBase;

import org.crosswire.common.progress.Progress;

/**Show all Progress status
 * see BibleDesktop JobsProgressBar for example use
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DownloadStatus extends ProgressActivityBase {
	private static final String TAG = "DownloadStatus";
	
	private boolean mIsOkayButtonEnabled = true;
	private Button mOkayButton;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying "+TAG+" view");
        setContentView(R.layout.download_status);
        
		mOkayButton = (Button)findViewById(R.id.okButton);
        enableOkay();

        Log.d(TAG, "Finished displaying Download Status view");
    }

    
    @Override
	protected void jobFinished(Progress job) {
		super.jobFinished(job);
		enableOkay();
	}


	@Override
	protected void updateProgress(Progress prog) {
		super.updateProgress(prog);
		fastDisableOkay();
	}


	/** called on job finishing and must be accurate
	 */
	private void enableOkay() {
		mIsOkayButtonEnabled = isAllJobsFinished();
   		mOkayButton.setEnabled(mIsOkayButtonEnabled);
    }
	/** called in tight loop so must be quick and ensure disabled
	 */
	private void fastDisableOkay() {
		if (mIsOkayButtonEnabled) {
			mIsOkayButtonEnabled = isAllJobsFinished();
	   		mOkayButton.setEnabled(mIsOkayButtonEnabled);
		}
    }
	
    protected void setMainText(String text) {
    	((TextView)findViewById(R.id.progressStatusMessage)).setText(text);
    }
    
    public void onOkay(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, DownloadStatus.class);
    	setResult(Download.DOWNLOAD_FINISH, resultIntent);
    	finish();    
    }
}
