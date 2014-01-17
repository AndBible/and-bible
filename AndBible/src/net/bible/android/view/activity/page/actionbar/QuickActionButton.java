package net.bible.android.view.activity.page.actionbar;

import android.support.v4.view.MenuItemCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;

abstract public class QuickActionButton implements OnMenuItemClickListener {
	
	private MenuItem menuItem;
	private int showAsActionFlags;
	
	abstract protected String getTitle();
	abstract protected boolean canShow();

	public QuickActionButton(int showAsActionFlags) {
		this.showAsActionFlags = showAsActionFlags;
	}

	public void addToMenu(Menu menu) {
		if (menuItem==null) {
			menuItem = menu.add("");
			MenuItemCompat.setShowAsAction(menuItem, showAsActionFlags);
			menuItem.setOnMenuItemClickListener(this);
			update();
		}
	}

	public void update() {
        if (menuItem!=null) {
        	// canShow means must show because we rely on AB logic
            menuItem.setVisible(canShow());

            menuItem.setTitle(getTitle());
        }
	}
	
	protected MenuItem getMenuItem() {
		return menuItem;
	}
}
