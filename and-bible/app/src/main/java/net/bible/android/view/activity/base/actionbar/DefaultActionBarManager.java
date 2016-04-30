package net.bible.android.view.activity.base.actionbar;

import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.view.Menu;

import net.bible.android.view.util.UiUtils;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class DefaultActionBarManager implements ActionBarManager {

	private ActionBar actionBar;
	
	@Override
	public void prepareOptionsMenu(Activity activity, Menu menu, ActionBar actionBar) {
		this.actionBar = actionBar;
		
		UiUtils.setActionBarColor(actionBar);

		// remove space on left reserved for home and up icons
		actionBar.setDisplayShowHomeEnabled(false);
	}

	@Override
	public void updateButtons() {
		UiUtils.setActionBarColor(actionBar);
	}
}
