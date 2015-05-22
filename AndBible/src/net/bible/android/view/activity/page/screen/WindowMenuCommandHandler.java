package net.bible.android.view.activity.page.screen;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.window.WindowControl;
import android.view.MenuItem;

public class WindowMenuCommandHandler {
	
	private WindowControl windowControl;

	public WindowMenuCommandHandler() {
		windowControl = ControlFactory.getInstance().getWindowControl();
	}
	
	/** 
     * on Click handlers
     */
    public boolean handleMenuRequest(MenuItem menuItem) {
        boolean isHandled = false;
        
        // Handle item selection
        switch (menuItem.getItemId()) {
		case R.id.windowNew:
			windowControl.addNewWindow();
			isHandled = true;
			break;
		case R.id.windowMinimise:
			windowControl.minimiseCurrentWindow();
			isHandled = true;
			break;
		case R.id.windowClose:
			windowControl.removeCurrentWindow();
			isHandled = true;
			break;
		case R.id.windowMoveFirst:
			windowControl.moveCurrentWindowToFirst();
			isHandled = true;
			break;
		case R.id.windowSynchronised:
			if (windowControl.getActiveWindow().isSynchronised()) {
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
