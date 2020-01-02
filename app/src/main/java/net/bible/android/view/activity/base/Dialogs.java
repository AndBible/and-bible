/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

package net.bible.android.view.activity.base;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.report.ErrorReportControl;
import net.bible.android.view.util.Hourglass;

/**
 * Class to manage the display of various dialogs
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class Dialogs {

	private ErrorReportControl errorReportControl;
	
	private Hourglass hourglass = new Hourglass();

	private Callback doNothingCallback = new Callback() {
		@Override
		public void okay() {
			// by default do nothing when user clicks okay
		}
	};

	private static final String TAG = "Dialogs";

	private static final Dialogs singleton = new Dialogs();
	
	public static Dialogs getInstance() {
		return singleton;
	}
	
	private Dialogs() {
		super();
		errorReportControl = BibleApplication.Companion.getApplication().getApplicationComponent().errorReportControl();
	}

    public void showMsg(int msgId, String param) {
    	showErrorMsg(BibleApplication.Companion.getApplication().getString(msgId, param));
    }
    public void showMsg(int msgId, boolean isCancelable, final Callback okayCallback) {
    	showMsg(BibleApplication.Companion.getApplication().getString(msgId), isCancelable, okayCallback, null);
    }
    public void showMsg(int msgId) {
    	showErrorMsg(BibleApplication.Companion.getApplication().getString(msgId));
    }
    public void showErrorMsg(int msgId) {
    	showErrorMsg(BibleApplication.Companion.getApplication().getString(msgId));
    }
    public void showErrorMsg(int msgId, String param) {
    	showErrorMsg(BibleApplication.Companion.getApplication().getString(msgId, param));
    }
    public void showErrorMsg(String msg) {
		showErrorMsg(msg, doNothingCallback);
    }

    public void showErrorMsg(int msgId, final Callback okayCallback) {
    	showErrorMsg(BibleApplication.Companion.getApplication().getString(msgId), okayCallback);
    }

    /**
     * Show error message and allow reporting of exception via e-mail to and-bible
     */
    public void showErrorMsg(int msgId, final Exception e) {
    	showErrorMsg(BibleApplication.Companion.getApplication().getString(msgId), e);
    }
    
    /**
     * Show error message and allow reporting of exception via e-mail to and-bible
     */
    public void showErrorMsg(String message, final Exception e) {
    	Callback reportCallback = new Callback() {
    		@Override
    		public void okay() {
    			errorReportControl.sendErrorReportEmail(e);
    		}
    	};

    	showMsg(message, false, doNothingCallback, reportCallback);
    }

    public void showErrorMsg(final String msg, final Callback okayCallback) {
    	showMsg(msg, false, okayCallback, null);
    }
    
    private void showMsg(final String msg, final boolean isCancelable, final Callback okayCallback, final Callback reportCallback) {
    	Log.d(TAG, "showErrorMesage message:"+msg);
    	try {
			final Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
			if (activity!=null) {
				activity.runOnUiThread(new Runnable() {
	
					@Override
					public void run() {
				    	AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(activity)
						   .setMessage(msg)
					       .setCancelable(isCancelable)
					       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
					           public void onClick(DialogInterface dialog, int buttonId) {
					        	   okayCallback.okay();
					           }
					       });
				    	
				    	// if cancelable then show a Cancel button
				    	if (isCancelable) {
					    	dlgBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
						           public void onClick(DialogInterface dialog, int buttonId) {
						        	   // do nothing
						           }
						       });
				    	}
				    	
				    	// enable report to andbible errors email list
				    	if (reportCallback!=null) {
					    	dlgBuilder.setNeutralButton(R.string.report_error, new DialogInterface.OnClickListener() {
						           public void onClick(DialogInterface dialog, int buttonId) {
						        	   reportCallback.okay();
						           }
						       });
				    	}

				    	dlgBuilder.show();
					}
				});
			} else {
				Toast.makeText(BibleApplication.Companion.getApplication().getApplicationContext(), msg, Toast.LENGTH_LONG).show();
			}
    	} catch (Exception e) {
    		Log.e(TAG, "Error showing error message.  Original error msg:"+msg, e);
    	}
    }

    public void showHourglass() {
        hourglass.show();
    }

    public void dismissHourglass() {
    	hourglass.dismiss();
    }
}
