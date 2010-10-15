package net.bible.android.activity;

 import java.util.Set;

import net.bible.android.activity.base.ProgressActivityBase;

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
public class DownloadStatus extends ProgressActivityBase {
	private static final String TAG = "DownloadStatus";
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying "+TAG+" view");
        setContentView(R.layout.download_status);

        Log.d(TAG, "Finished displaying Search Index view");
    }

    protected void setMainText(String text) {
    	((TextView)findViewById(R.id.progressStatusMessage)).setText(text);
    }
    
    public void onMore(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, DownloadStatus.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }

    public void onOkay(View v) {
    	Log.i(TAG, "CLICKED");
    	Intent resultIntent = new Intent(this, MainBibleActivity.class);
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
}
