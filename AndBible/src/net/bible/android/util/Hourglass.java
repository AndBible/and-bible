package net.bible.android.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;

public class Hourglass {
	
	private ProgressDialog hourglass;
	
	//todo need HourGlass factory method and link to Activity etc but for now just support hourglass from BibleView activity
//	private Activity activity;
	
    public static final int HOURGLASS_KEY = 99;

	public Hourglass() {
		super();
	}

	public void show(Context context) {
		hourglass = new ProgressDialog(context);
		hourglass.setMessage("");
		hourglass.setIndeterminate(true);
		hourglass.setCancelable(false);
		hourglass.show();
	}
	
	public void dismiss() {
		hourglass.dismiss();
	}

	public ProgressDialog getHourglass() {
		return hourglass;
	}
	
	
}
