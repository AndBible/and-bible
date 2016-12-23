package net.bible.android.view.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import net.bible.android.BibleApplication;
import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.Initialisation;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.download.FirstDownload;
import net.bible.android.view.activity.installzip.InstallZip;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.common.CommonUtils;
import net.bible.service.sword.SwordDocumentFacade;

import org.apache.commons.lang.StringUtils;

/** Called first to show download screen if no documents exist
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class StartupActivity extends CustomTitlebarActivityBase {

	private static final String TAG = "StartupActivity";

	private static final int DOWNLOAD_DOCUMENT_REQUEST = 2;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup_view);

        // do not show an actionBar/title on the splash screen
        getSupportActionBar().hide();
        
        TextView versionTextView = (TextView)findViewById(R.id.versionText);
        String versionMsg = BibleApplication.getApplication().getString(R.string.version_text, CommonUtils.getApplicationVersionName());
        versionTextView.setText(versionMsg);
        
        //See if any errors occurred during app initialisation, especially upgrade tasks
        int abortErrorMsgId = BibleApplication.getApplication().getErrorDuringStartup();
        
        // check for SD card 
        // it would be great to check in the Application but how to show dialog from Application?
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        	abortErrorMsgId = R.string.no_sdcard_error;
        }
        
        // show fatal startup msg and close app
        if (abortErrorMsgId!=0) {
        	Dialogs.getInstance().showErrorMsg(abortErrorMsgId, new Callback() {
				@Override
				public void okay() {
					// this causes the blue splashscreen activity to finish and since it is the top the app closes
					finish();					
				}
			});
        	// this aborts further initialisation but leaves blue splashscreen activity
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
        			// allow the splash screen to be displayed immediately
        			CommonUtils.pauseMillis(1);
        			
	                // force Sword to initialise itself
	                Initialisation.getInstance().initialiseNow();
        		} finally {
        			// switch back to ui thread to continue
        			uiHandler.post(uiThreadRunnable);
        		}
        	}
        }.start();
    }
    
    private void postBasicInitialisationControl() {
        if (SwordDocumentFacade.getInstance().getBibles().size()==0) {
        	Log.i(TAG, "Invoking download activity because no bibles exist");
        	askIfGotoDownloadActivity();
        } else {
        	Log.i(TAG, "Going to main bible view");
        	gotoMainBibleActivity();
        }
    }

	private void askIfGotoDownloadActivity() {
		new AlertDialog.Builder(StartupActivity.this)
				.setView(getLayoutInflater().inflate(R.layout.first_time_dialog, null))
				.setInverseBackgroundForced(true) // prevents black text on black bkgnd on Android 2.3 (http://stackoverflow.com/questions/13266901/dark-text-on-dark-background-on-alertdialog-with-theme-sherlock-light)
				.setCancelable(false)
				.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						doGotoDownloadActivity();
					}
				})
				.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						StartupActivity.this.finish();
						// ensure app exits to force Sword to reload or if a sdcard/jsword folder is created it may not be recognised
						System.exit(2);
					}
				}).create().show();
	}

    private void doGotoDownloadActivity() {
    	String errorMessage = null;
    	if (!CommonUtils.isInternetAvailable()) {
    		errorMessage = getString(R.string.no_internet_connection);
    	} else if (CommonUtils.getSDCardMegsFree() < SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
    		errorMessage = getString(R.string.storage_space_warning);
    	}
    	
    	if (StringUtils.isBlank(errorMessage)) {
	       	Intent handlerIntent = new Intent(this, FirstDownload.class);
	    	startActivityForResult(handlerIntent, DOWNLOAD_DOCUMENT_REQUEST);
		} else {
			Dialogs.getInstance().showErrorMsg(errorMessage, new Callback() {
				@Override
				public void okay() {
		    		finish();
				}
			});
		}
    }

	/**
	 * Load from Zip link on first_time_dialog has been clicked
	 */
	public void onLoadFromZip(View v) {
		Log.i(TAG, "Load from Zip clicked");

		Intent handlerIntent = new Intent(this, InstallZip.class);
		startActivityForResult(handlerIntent, DOWNLOAD_DOCUMENT_REQUEST);
	}

	private void gotoMainBibleActivity() {
		Log.i(TAG, "Going to MainBibleActivity");
    	Intent handlerIntent = new Intent(this, MainBibleActivity.class);
    	startActivity(handlerIntent);
    	finish();
    }

	/** on return from download we may go to bible
     *  on return from bible just exit
     */
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) { 
    	Log.d(TAG, "Activity result:"+resultCode);
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if (requestCode == DOWNLOAD_DOCUMENT_REQUEST) {
    		Log.i(TAG, "Returned from Download");
    		if (SwordDocumentFacade.getInstance().getBibles().size()>0) {
        		Log.i(TAG, "Bibles now exist so go to main bible view");
				// select appropriate default verse e.g. John 3.16 if NT only
				ControlFactory.getInstance().getPageControl().setFirstUseDefaultVerse();

    			gotoMainBibleActivity();
    		} else {
        		Log.i(TAG, "No Bibles exist so exit");
    			finish();
    		}
    	}
    }
}
