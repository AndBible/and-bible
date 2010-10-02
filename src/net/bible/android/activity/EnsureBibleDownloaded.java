package net.bible.android.activity;

import net.bible.service.sword.SwordApi;
import android.app.Activity;
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
public class EnsureBibleDownloaded extends Activity {
	
	private static final String TAG = "EnsureBibleDownloaded";
	
	private int clickCount = 0;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Displaying ensure-bible-downloaded");
        setContentView(R.layout.ensure_bible_downloaded);
    
        if (SwordApi.getInstance().getBibles().size()>0) {
        	gotoMainScreen();
        }        
    }
    
    public void onContinue(View v) {
    	Log.i(TAG, "CLICKED");
        if (SwordApi.getInstance().getBibles().size()>0) {
        	gotoMainScreen();
        } else {
        	TextView warn = (TextView)findViewById(R.id.waitForBibleNotYet);
        	// if already warned then alternate the wait text
        	if (clickCount++%2==0) {
        		warn.setText(R.string.please_wait);
        	} else {
        		warn.setText(R.string.wait_for_bible_not_yet);        		
        	}
        	
    		warn.setVisibility(View.VISIBLE);
        }
    }
    

    
    private void gotoMainScreen() {
		Intent intent = new Intent(this, MainBibleActivity.class);
    	startActivity(intent);
    	finish();
    }
}
