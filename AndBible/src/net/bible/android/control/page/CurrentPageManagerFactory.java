package net.bible.android.control.page;

import net.bible.android.control.page.window.Window;


public class CurrentPageManagerFactory {

	public CurrentPageManagerFactory() {
	}

	public CurrentPageManager createCurrentPageManager(Window window) {
		CurrentPageManager cpm;
		
		cpm = new CurrentPageManager();
		
		return cpm;
	}
}
