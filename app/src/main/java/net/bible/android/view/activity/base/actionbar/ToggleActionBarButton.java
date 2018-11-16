package net.bible.android.view.activity.base.actionbar;

import androidx.core.view.MenuItemCompat;

/** Two state actionbar button
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public abstract class ToggleActionBarButton extends QuickActionButton {

	private boolean isOn = true;
	private int onIcon;
	private int offIcon;
	
	public ToggleActionBarButton(int onIcon, int offIcon) {
		// overridden by canShow
		super(MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		
		this.onIcon = onIcon;
		this.offIcon = offIcon;
	}

	/** 
	 * Show a different icon to represent switch to 'Appendix' or back to scripture
	 */
	@Override
	protected int getIcon() {
		return isOn() ? onIcon : offIcon;
	}
	
	public boolean isOn() {
		return isOn;
	}

	public void setOn(boolean isOn) {
		this.isOn = isOn;
	}
}
