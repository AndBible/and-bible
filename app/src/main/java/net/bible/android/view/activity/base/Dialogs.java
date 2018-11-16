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
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
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
		errorReportControl = BibleApplication.getApplication().getApplicationComponent().errorReportControl();
	}

	public void showMsg(int msgId, String param) {
		showErrorMsg(BibleApplication.getApplication().getString(msgId, param));
	}
	public void showMsg(int msgId, boolean isCancelable, final Callback okayCallback) {
		showMsg(BibleApplication.getApplication().getString(msgId), isCancelable, okayCallback, null);
	}
	public void showMsg(int msgId) {
		showErrorMsg(BibleApplication.getApplication().getString(msgId));
	}
	public void showErrorMsg(int msgId) {
		showErrorMsg(BibleApplication.getApplication().getString(msgId));
	}
	public void showErrorMsg(int msgId, String param) {
		showErrorMsg(BibleApplication.getApplication().getString(msgId, param));
	}
	public void showErrorMsg(String msg) {
		showErrorMsg(msg, doNothingCallback);
	}

	public void showErrorMsg(int msgId, final Callback okayCallback) {
		showErrorMsg(BibleApplication.getApplication().getString(msgId), okayCallback);
	}

	/**
	 * Show error message and allow reporting of exception via e-mail to and-bible
	 */
	public void showErrorMsg(int msgId, final Exception e) {
		showErrorMsg(BibleApplication.getApplication().getString(msgId), e);
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
				Toast.makeText(BibleApplication.getApplication().getApplicationContext(), msg, Toast.LENGTH_LONG).show();
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
