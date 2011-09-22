package net.bible.android.view.activity.base;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.view.util.Hourglass;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.Toast;

public class Dialogs {

	public static final int TOO_MANY_JOBS = 121;
	private String errorMsg;

	private Hourglass hourglass = new Hourglass();
	
	private static final String TAG = "Dialogs";

	private static final Dialogs singleton = new Dialogs();
	
	public static Dialogs getInstance() {
		return singleton;
	}
	
	private Dialogs() {
		super();
	}

    public void showErrorMsg(int msgId) {
    	showErrorMsg(BibleApplication.getApplication().getString(msgId));
    }
    public void showErrorMsg(String msg) {
    	showErrorMsg(msg, new Callback() {
			@Override
			public void okay() {
				// by default do nothing when user clicks okay
			}
		});
    }

    public void showErrorMsg(int msgId, final Callback okayCallback) {
    	showErrorMsg(BibleApplication.getApplication().getString(msgId), okayCallback);
    }

    public void showErrorMsg(final String msg, final Callback okayCallback) {
    	Log.d(TAG, "showErrorMesage message:"+msg);
    	try {
			final Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
			if (activity!=null) {
				activity.runOnUiThread(new Runnable() {
	
					@Override
					public void run() {
				    	new AlertDialog.Builder(activity)
						   .setMessage(msg)
					       .setCancelable(false)
					       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
					           public void onClick(DialogInterface dialog, int buttonId) {
					        	   okayCallback.okay();
					           }
					       }).show();
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
