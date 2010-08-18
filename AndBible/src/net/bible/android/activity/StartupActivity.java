package net.bible.android.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import net.bible.android.util.ActivityBase;
import net.bible.android.view.BibleSwipeListener;
import net.bible.service.sword.SwordApi;

public class StartupActivity extends ActivityBase {

	private static final String TAG = "StartupActivity";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup_view);

        if (SwordApi.getInstance().getBibles().size()==0) {
        	Log.i(TAG, "Invoking download activity because no bibles exist");
        	gotoDownloadActivity();
        } else {
        	Log.i(TAG, "Going to main bible view");
        	gotoMainBibleActivity();
        }
    }

    private void gotoDownloadActivity() {
    	Intent handlerIntent = new Intent(this, Download.class);
    	startActivityForResult(handlerIntent, 1);
    }

    private void gotoMainBibleActivity() {
    	Intent handlerIntent = new Intent(this, MainBibleActivity.class);
    	startActivityForResult(handlerIntent, 2);
    }
    
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) { 
    	Log.d(TAG, "Activity result:"+resultCode);
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if (requestCode == 1) {
    		Log.i(TAG, "Returned from Download");
    		if (SwordApi.getInstance().getBibles().size()>0) {
        		Log.i(TAG, "Bibles now exist so go to main bible view");
    			gotoMainBibleActivity();
    		} else {
        		Log.i(TAG, "No Bibles exist so exit");
    			finish();
    		}
    	}
    	if (requestCode == 2) {
    		Log.i(TAG, "Returned from MainBibleActivity so exit");
    		finish();
    	}
    }
}
