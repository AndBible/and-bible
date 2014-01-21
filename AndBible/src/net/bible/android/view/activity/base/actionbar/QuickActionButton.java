package net.bible.android.view.activity.base.actionbar;

import android.support.v4.view.MenuItemCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

abstract public class QuickActionButton implements OnMenuItemClickListener {
	
	private MenuItem menuItem;
	private int showAsActionFlags;
	
	abstract protected String getTitle();
	abstract protected boolean canShow();
	
	private static final int NO_ICON = 0;

	public QuickActionButton(int showAsActionFlags) {
		this.showAsActionFlags = showAsActionFlags;
	}

	public void addToMenu(Menu menu) {
		if (menuItem==null) {
			menuItem = menu.add("");
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
	
	protected int getIcon() {
		return NO_ICON;
	}
}
