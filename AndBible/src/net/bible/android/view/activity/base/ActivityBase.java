package net.bible.android.view.activity.base;

import net.bible.android.BibleApplication;
import net.bible.android.view.util.UiUtils;
import android.app.Activity;
import android.app.Dialog;
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
	
	private Dialogs dialogs;
	
    public ActivityBase() {
		super();
		
		dialogs = new Dialogs(this);
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(getLocalClassName(), "onCreate");
        
        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		
		UiUtils.applyTheme(this);
    }
    
    @Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		// TODO Auto-generated method stub
		super.onPrepareDialog(id, dialog);

		dialogs.onPrepareDialog(id, dialog);
	}

	/** for some reason Android insists Dialogs are created in the onCreateDialog method
     * 
     */
    @Override
    protected Dialog onCreateDialog(int id) {
    	return dialogs.onCreateDialog(id);
    }
    
    protected void dismissHourglass() {
    	dialogs.dismissHourglass();
    }

    /** to retry e.g. if internet conn down override this method
     */
    public void dialogOnClick(int dialogId, int buttonId) {
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
	public void showErrorMsg(int msgResId) {
		showErrorMsg(getString(msgResId));
	}

	@Override
    public void showErrorMsg(String msg) {
    	dialogs.showErrorMsg(msg);
    }

	@Override
	protected void onResume() {
		super.onResume();
        Log.i(getLocalClassName(), "onResume");
        BibleApplication.getApplication().iAmNowCurrent(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
        Log.i(getLocalClassName(), "onPause");
        BibleApplication.getApplication().iAmNoLongerCurrent(this);
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
