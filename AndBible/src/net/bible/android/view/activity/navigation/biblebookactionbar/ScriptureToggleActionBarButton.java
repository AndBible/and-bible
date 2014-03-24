package net.bible.android.view.activity.navigation.biblebookactionbar;

import java.lang.ref.WeakReference;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.navigation.NavigationControl;
import net.bible.android.view.activity.base.actionbar.QuickActionButton;
import android.support.v4.view.MenuItemCompat;
import android.view.MenuItem;

/** Quick change bible toolbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ScriptureToggleActionBarButton extends QuickActionButton {

	// weak to prevent ref from this (normally static) menu preventing gc of book selector
	private WeakReference<ScriptureToggleEventHandler> weakScriptureToggleEventHandler;
	
	private NavigationControl navigationControl = ControlFactory.getInstance().getNavigationControl();
	
	public ScriptureToggleActionBarButton() {
		// overridden by canShow
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
	}
	
	public void registerScriptureToggleEventHandler(ScriptureToggleEventHandler scriptureToggleEventHandler) {
		this.weakScriptureToggleEventHandler = new WeakReference<ScriptureToggleEventHandler>(scriptureToggleEventHandler);
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		navigationControl.toggleBibleBookSelectorScriptureDisplay();
		ScriptureToggleEventHandler scriptureToggleEventHandler = weakScriptureToggleEventHandler.get();
		if (scriptureToggleEventHandler!=null) {
			scriptureToggleEventHandler.onChange(navigationControl.isCurrentlyShowingScripture());
		}
		update();
		return true;
	}

	/** 
	 * Show a different icon to represent switch to 'Appendix' or back to scripture
	 */
	@Override
	protected int getIcon() {
		return navigationControl.isCurrentlyShowingScripture() ? R.drawable.ic_action_new : R.drawable.ic_action_undo;
	}
	
	@Override
	protected String getTitle() {
		// TODO Auto-generated method stub
		return "";
	}

	@Override
	protected boolean canShow() {
		return navigationControl.getBibleBooks(false).size()>0;
	}
}
