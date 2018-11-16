package net.bible.android.view.activity.page.screen;

import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ApplicationScope;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;

import javax.inject.Inject;

@ApplicationScope
public class WindowMenuCommandHandler {
	
	private final WindowControl windowControl;

	@Inject
	public WindowMenuCommandHandler(WindowControl windowControl) {
		this.windowControl = windowControl;
	}
	
	/** 
     * on Click handlers
     */
    public boolean handleMenuRequest(MenuItem menuItem) {
        boolean isHandled = false;
        
        // Handle item selection
        Window activeWindow = windowControl.getActiveWindow();
		switch (menuItem.getItemId()) {
		case R.id.windowNew:
			windowControl.addNewWindow();
			isHandled = true;
			break;
		case R.id.windowMaximise:
			if (activeWindow.isMaximised()) {
				windowControl.unmaximiseWindow(activeWindow);
				menuItem.setChecked(false);
			} else {
				windowControl.maximiseWindow(activeWindow);
				menuItem.setChecked(true);
			}
			isHandled = true;
			break;
		case R.id.windowMinimise:
			windowControl.minimiseCurrentWindow();
			isHandled = true;
			break;
		case R.id.windowClose:
			windowControl.closeCurrentWindow();
			isHandled = true;
			break;
		case R.id.windowMoveFirst:
			windowControl.moveCurrentWindowToFirst();
			isHandled = true;
			break;
		case R.id.windowSynchronise:
			if (activeWindow.isSynchronised()) {
				windowControl.unsynchroniseCurrentWindow();
				menuItem.setChecked(false);
			} else {
				windowControl.synchroniseCurrentWindow();
				menuItem.setChecked(true);
			}
			isHandled = true;
			break;
        }
        
        return isHandled;
	}
}
