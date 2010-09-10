package net.bible.android.activity;

import net.bible.android.util.ActivityBase;
import net.bible.service.sword.SwordApi;

import org.crosswire.common.util.Reporter;
import org.crosswire.common.util.ReporterEvent;
import org.crosswire.common.util.ReporterListener;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

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

        // allow call back and continuation in the ui thread after JSword has been initialised
        final Handler uiHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
			    postBasicInitialisationControl();
			}
        };

        // initialise JSword in another thread (takes a long time) then call main ui thread Handler to continue
        // use another thread to allow the splash screen to be displayed and an hourglass to run
        new Thread() {
        	public void run() {
        		try {
	                installJSwordErrorReportListener();
	                // force Sword to initialise itself
	                SwordApi.getInstance().getBibles();
        		} finally {
        			uiHandler.dispatchMessage(new Message());
        		}
        	}
        }.start();
    }
    
    private void postBasicInitialisationControl() {
        if (SwordApi.getInstance().getBibles().size()==0) {
        	Log.i(TAG, "Invoking download activity because no bibles exist");
        	gotoDownloadActivity();
        } else {
        	Log.i(TAG, "Going to main bible view");
        	gotoMainBibleActivity();
        }
    }

    private void gotoDownloadActivity() {
    	showDialog(CAN_DOWNLOAD_DLG);
    }

    private void gotoMainBibleActivity() {
    	Intent handlerIntent = new Intent(this, MainBibleActivity.class);
    	startActivityForResult(handlerIntent, 2);
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
            		   .setMessage("Download bibles from internet?")
            	       .setCancelable(false)
            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int id) {
        	    	           	Intent handlerIntent = new Intent(StartupActivity.this, Download.class);
        	    	        	startActivityForResult(handlerIntent, 1);
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

    /** JSword calls back to this listener in the event of some types of error
     * 
     */
    private void installJSwordErrorReportListener() {
        Reporter.addReporterListener(new ReporterListener() {
			@Override
			public void reportException(ReporterEvent ev) {
				Log.e(TAG, ev.getMessage(), ev.getException());
				//todo: make sure this runs on ui thread
				Toast.makeText(getApplicationContext(), ev.getMessage(), Toast.LENGTH_LONG);
			}

			@Override
			public void reportMessage(ReporterEvent ev) {
				Log.w(TAG, ev.getMessage(), ev.getException());
				//todo: make sure this runs on ui thread
				Toast.makeText(getApplicationContext(), ev.getMessage(), Toast.LENGTH_SHORT);
			}
        });
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
    	if (requestCode == 2) {
    		Log.i(TAG, "Returned from MainBibleActivity so exit");
    		finish();
    	}
    }
}
