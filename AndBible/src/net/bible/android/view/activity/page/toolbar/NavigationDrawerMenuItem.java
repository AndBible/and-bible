package net.bible.android.view.activity.page.toolbar;

import android.view.MenuItem;

public class NavigationDrawerMenuItem {
	
	private MenuItem menuItem;

	public NavigationDrawerMenuItem(MenuItem menuItem) {
		this.menuItem = menuItem;
	}

	@Override
	public String toString() {
		return menuItem.getTitle().toString();
	}
	
	public int getId() {
		return menuItem.getItemId();
	}
}
