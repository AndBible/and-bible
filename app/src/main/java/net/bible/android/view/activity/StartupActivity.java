/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

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
import net.bible.android.control.WarmUp;
import net.bible.android.view.activity.base.Callback;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.download.FirstDownload;
import net.bible.android.view.activity.installzip.InstallZip;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.common.CommonUtils;

import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;

/** Called first to show download screen if no documents exist
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class StartupActivity extends CustomTitlebarActivityBase {

	private WarmUp warmUp;

	private static final String TAG = "StartupActivity";

	private static final int DOWNLOAD_DOCUMENT_REQUEST = 2;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup_view);

		buildActivityComponent().inject(this);

        // do not show an actionBar/title on the splash screen
        getSupportActionBar().hide();
        
        TextView versionTextView = (TextView)findViewById(R.id.versionText);
        String versionMsg = BibleApplication.getApplication().getString(R.string.version_text, CommonUtils.getApplicationVersionName());
        versionTextView.setText(versionMsg);
        
        //See if any errors occurred during app warmUp, especially upgrade tasks
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
        	// this aborts further warmUp but leaves blue splashscreen activity
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
	                warmUp.warmUpSwordNow();
        		} finally {
        			// switch back to ui thread to continue
        			uiHandler.post(uiThreadRunnable);
        		}
        	}
        }.start();
    }
    
    private void postBasicInitialisationControl() {
        if (getSwordDocumentFacade().getBibles().size()==0) {
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
    		if (getSwordDocumentFacade().getBibles().size()>0) {
        		Log.i(TAG, "Bibles now exist so go to main bible view");
				// select appropriate default verse e.g. John 3.16 if NT only
				getPageControl().setFirstUseDefaultVerse();

    			gotoMainBibleActivity();
    		} else {
        		Log.i(TAG, "No Bibles exist so exit");
    			finish();
    		}
    	}
    }

	@Inject
	void setWarmUp(WarmUp warmUp) {
		this.warmUp = warmUp;
	}
}
