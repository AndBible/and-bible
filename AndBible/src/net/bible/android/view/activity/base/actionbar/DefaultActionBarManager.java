package net.bible.android.view.activity.base.actionbar;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v7.app.ActionBar;
import android.view.Menu;

public class DefaultActionBarManager implements ActionBarManager {

	@Override
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		setDefaultActionBarColor(actionBar);
		
		actionBar.setDisplayShowHomeEnabled(false);
	}

	@Override
	public void updateButtons() {
		//NOP
	}
	
	protected void setDefaultActionBarColor(ActionBar actionBar) {
		changeColor(Color.BLACK, actionBar);
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
