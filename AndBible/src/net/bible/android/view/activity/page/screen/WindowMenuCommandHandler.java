package net.bible.android.view.activity.page.screen;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.splitscreen.WindowControl;
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
		case R.id.splitNew:
			windowControl.addNewWindow();
			isHandled = true;
			break;
		case R.id.splitMinimise:
			windowControl.minimiseCurrentWindow();
			isHandled = true;
			break;
		case R.id.splitDelete:
			windowControl.removeCurrentWindow();
			isHandled = true;
			break;
		case R.id.splitMoveFirst:
			windowControl.moveCurrentWindowToFirst();
			isHandled = true;
			break;
		case R.id.splitLink:
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
