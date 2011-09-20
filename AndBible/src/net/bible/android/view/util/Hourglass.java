package net.bible.android.view.util;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import android.app.Activity;
import android.app.ProgressDialog;

/** Helper class to show HourGlass
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class Hourglass {
	
	private ProgressDialog hourglass;
	
	public Hourglass() {
		super();
	}

	public void show() {
		final Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
		if (activity!=null) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					hourglass = new ProgressDialog(activity);
					hourglass.setMessage(BibleApplication.getApplication().getText(R.string.please_wait));
					hourglass.setIndeterminate(true);
					hourglass.setCancelable(false);
					hourglass.show();
				}
			});
		}
	}
	
	public void dismiss() {
		if (hourglass!=null) {
			final Activity activity = CurrentActivityHolder.getInstance().getCurrentActivity();
			if (activity!=null) {
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						hourglass.dismiss();
						hourglass = null;
					}
				});
			}
		}
	}

	public ProgressDialog getHourglass() {
		return hourglass;
	}
}
