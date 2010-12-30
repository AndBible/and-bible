package net.bible.android.view.activity;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.view.activity.base.ActivityBase;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

/** Called first to show download screen if no documents exist
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class StartupActivity extends ActivityBase {

	private static final int CAN_DOWNLOAD_DLG = 10;
	
	private static final String TAG = "StartupActivity";

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup_view);
        
        TextView versionTextView = (TextView)findViewById(R.id.versionText);
        String versionMsg = BibleApplication.getApplication().getString(R.string.version_text, CommonUtils.getApplicationVersionName());
        versionTextView.setText(versionMsg);
        
        // check for SD card 
        //TODO it would be great to check in the Application but how to show dialog from Application?
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        	showErrorMsg(R.string.no_sdcard_error);
        	return;
        }
    
        // allow call back and continuation in the ui thread after JSword has been initialised
        final Handler uiHandler = new Handler();
        final Runnable uiThreadRunnable = new Runnable() {
			@Override
			public void run() {
			    postBasicInitialisationControl();
			}
        };

        // initialise JSword in another thread (takes a long time) then call main ui thread Handler to continue
        // this allows the splash screen to be displayed and an hourglass to run
        new Thread() {
        	public void run() {
        		try {
	                // force Sword to initialise itself
	                SwordApi.getInstance().getBibles();
        		} finally {
        			// switch back to ui thread to continue
        			uiHandler.post(uiThreadRunnable);
        		}
        	}
        }.start();
    }
    
    private void postBasicInitialisationControl() {
        if (SwordApi.getInstance().getBibles().size()==0) {
        	Log.i(TAG, "Invoking download activity because no bibles exist");
        	askIfGotoDownloadActivity();
        } else {
        	Log.i(TAG, "Going to main bible view");
        	gotoMainBibleActivity();
        }
    }

	private void askIfGotoDownloadActivity() {
    	showDialog(CAN_DOWNLOAD_DLG);
    }
    private void doGotoDownloadActivity() {
    	if (CommonUtils.isInternetAvailable()) {
	       	Intent handlerIntent = new Intent(StartupActivity.this, Download.class);
	    	startActivityForResult(handlerIntent, 1);
	    	
	    	// tidy up these resources
	    	removeDialog(CAN_DOWNLOAD_DLG);
	    	finish();
		} else {
			Dialogs.getInstance().showErrorMsg(getString(R.string.no_internet_connection), new Callback() {
				@Override
				public void okay() {
		    		finish();
				}
			});
		}
    }

	private void gotoMainBibleActivity() {
		Log.i(TAG, "Going to MainBibleActivity");
    	Intent handlerIntent = new Intent(this, MainBibleActivity.class);
    	startActivity(handlerIntent);
    	finish();
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
    	Dialog superDlg = super.onCreateDialog(id);
    	if (superDlg!=null) {
    		return superDlg;
    	}
    	
        switch (id) {
            case CAN_DOWNLOAD_DLG:
            	return new AlertDialog.Builder(StartupActivity.this)
            		   .setMessage(R.string.download_confirmation)
            	       .setCancelable(false)
            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int id) {
            	        	   doGotoDownloadActivity();
            	           }
            	       })
            	       .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int id) {
           	                  StartupActivity.this.finish();
            	           }
            	       }).create();
        }
        return null;
    }

    /** on return from download we may go to bible
     *  on return from bible just exit
     */
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
    }
}
