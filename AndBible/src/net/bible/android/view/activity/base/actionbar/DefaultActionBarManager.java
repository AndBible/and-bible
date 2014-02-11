package net.bible.android.view.activity.base.actionbar;

import net.bible.android.activity.R;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.view.Menu;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DefaultActionBarManager implements ActionBarManager {

	private ActionBar actionBar;
	
	private static final int night = CommonUtils.getResourceColor(R.color.actionbar_background_night);
	private static final int day = CommonUtils.getResourceColor(R.color.actionbar_background_day);
	
	@Override
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		this.actionBar = actionBar;
		
		setActionBarColor();
		
		actionBar.setDisplayShowHomeEnabled(false);
	}

	@Override
	public void updateButtons() {
		setActionBarColor();
	}
	
	/** change actionBar colour according to day/night state
	 */
	protected void setActionBarColor() {
		changeColor(ScreenSettings.isNightMode()? night : day, actionBar);
	}

    private void changeColor(final int newColor, final ActionBar actionBar) {
    	if (actionBar!=null) {
    		CurrentActivityHolder.getInstance().runOnUiThread(new Runnable() {

				@Override
				public void run() {
			        Drawable colorDrawable = new ColorDrawable(newColor);
			        actionBar.setBackgroundDrawable(colorDrawable);
			    }
    		});
    	}
    }
}
