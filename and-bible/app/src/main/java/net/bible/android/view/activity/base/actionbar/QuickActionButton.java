package net.bible.android.view.activity.base.actionbar;

import android.support.v4.view.MenuItemCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;

import net.bible.android.activity.R;
import net.bible.android.control.speak.SpeakControl;
import net.bible.service.common.CommonUtils;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
abstract public class QuickActionButton implements OnMenuItemClickListener {
	
	private MenuItem menuItem;
	private int showAsActionFlags;
	
	// weak to prevent ref from this (normally static) menu preventing gc of book selector
	private WeakReference<OnClickListener> weakOnClickListener;
	
	abstract protected String getTitle();
	abstract protected boolean canShow();
	
	private int thisItemId = nextItemId++;
	private static int nextItemId = 100;
	
	private static final int NO_ICON = 0;
	
	private SpeakControl speakControl;

	public QuickActionButton(int showAsActionFlags) {
		this.showAsActionFlags = showAsActionFlags;
	}

	public void addToMenu(Menu menu) {
		if (menuItem==null || (menu.findItem(thisItemId) == null)) {
			menuItem = menu.add(Menu.NONE, thisItemId, Menu.NONE, "");
			MenuItemCompat.setShowAsAction(menuItem, showAsActionFlags);
			menuItem.setOnMenuItemClickListener(this);
			update(menuItem);
		}
	}

	public void update() {
        if (menuItem!=null) {
        	update(menuItem);
        }
	}
	protected void update(MenuItem menuItem) {
    	// canShow means must show because we rely on AB logic
        menuItem.setVisible(canShow());

        menuItem.setTitle(getTitle());
        
        int iconResId = getIcon();
        if (iconResId!=NO_ICON) {
        	menuItem.setIcon(iconResId);
        }
	}

	/**
	 * Provide the possibility of handling clicks outside of the button e.g. in Activity 
	 */
	public void registerClickListener(OnClickListener onClickListener) {
		this.weakOnClickListener = new WeakReference<>(onClickListener);
	}

	/**
	 * This is sometimes overridden but can be used to handle clicks in the Activity 
	 */
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		OnClickListener onClickListener = weakOnClickListener.get();
		if (onClickListener!=null) {
			onClickListener.onClick(null);
		}
		update();
		return true;
	}

	
	protected int getIcon() {
		return NO_ICON;
	}
	
	protected boolean isWide() {
		return 4<CommonUtils.getResourceInteger(R.integer.number_of_quick_buttons);
	}
	
	protected boolean isSpeakMode() {
		return speakControl.isSpeaking() || speakControl.isPaused();
	}

	@Inject
	void setSpeakControl(SpeakControl speakControl) {
		this.speakControl = speakControl;
	}
}
