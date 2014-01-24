package net.bible.android.view.activity.base.actionbar;

import net.bible.android.activity.R;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;
import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.view.Menu;

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
	
	protected void setActionBarColor() {
		changeColor(ScreenSettings.isNightMode()? night : day, actionBar);
	}

    protected void changeColor(int newColor, ActionBar actionBar) {

        Drawable colorDrawable = new ColorDrawable(newColor);
//        Drawable bottomDrawable = CurrentActivityHolder.getInstance().getCurrentActivity().getResources().getDrawable(R.drawable.actionbar_bottom);
//        LayerDrawable ld = new LayerDrawable(new Drawable[] { colorDrawable, bottomDrawable });

        actionBar.setBackgroundDrawable(colorDrawable);

//        actionBar.setDisplayShowTitleEnabled(false);
//        actionBar.setDisplayShowTitleEnabled(true);
    }
}
