package net.bible.android.view.activity.base;

import net.bible.android.view.util.UiUtils;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Base class for activities
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ActivityBase extends Activity implements AndBibleActivity {
	private static final String TAG = "ActivityBase";
	
    public ActivityBase() {
		super();
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(getLocalClassName(), "onCreate");
        
        // Register current activity in onCreate and onResume
        CurrentActivityHolder.getInstance().setCurrentActivity(this);

        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		
		UiUtils.applyTheme(this);
    }
    
	public void showErrorMsg(int msgResId) {
		Dialogs.getInstance().showErrorMsg(msgResId);
	}

    protected void showHourglass() {
    	Dialogs.getInstance().showHourglass();
    }
    protected void dismissHourglass() {
    	Dialogs.getInstance().dismissHourglass();
    }

    protected void returnErrorToPreviousScreen() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(Activity.RESULT_CANCELED, resultIntent);
    	finish();    
    }
    protected void returnToPreviousScreen() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
    
	@Override
	protected void onResume() {
		super.onResume();
        Log.i(getLocalClassName(), "onResume");
        CurrentActivityHolder.getInstance().setCurrentActivity(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
        Log.i(getLocalClassName(), "onPause");
        CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this);
	}

	@Override
	protected void onRestart() {
		super.onRestart();
        Log.i(getLocalClassName(), "onRestart");
	}

	@Override
	protected void onStart() {
		super.onStart();
        Log.i(getLocalClassName(), "onStart");
	}


	@Override
	protected void onStop() {
		super.onStop();
        Log.i(getLocalClassName(), "onStop");
	}
}
