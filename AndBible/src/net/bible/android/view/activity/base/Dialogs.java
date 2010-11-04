package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import net.bible.android.view.util.Hourglass;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

public class Dialogs {

	private Activity activity;
	
	public static final int TOO_MANY_JOBS = 121;
	public static final int ERROR_MSG = 122;
	private String errorMsg;

	private Hourglass hourglass = new Hourglass();

	public Dialogs(Activity activity) {
		super();
		this.activity = activity;
	}

    public void showErrorMsg(String msg) {
    	errorMsg = msg;
    	activity.showDialog(ERROR_MSG);    	
    }

	protected void onPrepareDialog(int id, Dialog dialog) {
		// TODO Auto-generated method stub
        switch (id) {
        case ERROR_MSG:
        	AlertDialog alertDialog = (AlertDialog)dialog;
        	alertDialog.setMessage(errorMsg);
        };
	}

    public Dialog onCreateDialog(int id) {
        switch (id) {
            case Hourglass.HOURGLASS_KEY:
                hourglass.show(activity);
                return hourglass.getHourglass();
            case TOO_MANY_JOBS:
            	return new AlertDialog.Builder(activity)
            		   .setMessage(activity.getText(R.string.too_many_jobs))
            	       .setCancelable(false)
            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int buttonId) {
            	        	   ((AndBibleActivity)activity).dialogOnClick(TOO_MANY_JOBS, buttonId);
            	           }
            	       }).create();
            case ERROR_MSG:
            	return new AlertDialog.Builder(activity)
            		   .setMessage(errorMsg)
            	       .setCancelable(false)
            	       .setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
            	           public void onClick(DialogInterface dialog, int buttonId) {
            	        	   ((AndBibleActivity)activity).dialogOnClick(ERROR_MSG, buttonId);
            	           }
            	       }).create();
        }
        return null;
    }

    public void dismissHourglass() {
    	hourglass.dismiss();
    }

}
