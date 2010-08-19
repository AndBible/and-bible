package net.bible.android.activity;

import net.bible.android.util.ActivityBase;
import net.bible.service.sword.SwordApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class StartupActivity extends ActivityBase {

	private static final int CAN_DOWNLOAD_DLG = 10;
	
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
