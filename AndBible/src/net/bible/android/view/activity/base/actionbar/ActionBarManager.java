package net.bible.android.view.activity.base.actionbar;

import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.view.Menu;

public interface ActionBarManager {

	public abstract void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar);

	public abstract void updateButtons();

}