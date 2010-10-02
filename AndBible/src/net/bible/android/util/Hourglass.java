package net.bible.android.util;

import net.bible.android.activity.R;
import net.bible.android.application.ScriptureApplication;
import android.app.ProgressDialog;
import android.content.Context;

/** Helper class to show HourGlass
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
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
		hourglass.setMessage(ScriptureApplication.getApplication().getText(R.string.please_wait));
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
