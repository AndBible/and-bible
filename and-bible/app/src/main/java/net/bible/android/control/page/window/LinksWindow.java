package net.bible.android.control.page.window;

import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.WindowLayout.WindowState;

import org.crosswire.jsword.book.Book;

/**
 * Window used when user selects a link
 */
public class LinksWindow extends Window {

	// must be -ve so as not to interfere with incrementing window number sequence
	protected static final int DEDICATED_LINK_WINDOW_SCREEN_NO = -999;

	public LinksWindow(WindowState windowState, CurrentPageManager currentPageManager) {
		super(DEDICATED_LINK_WINDOW_SCREEN_NO, windowState, currentPageManager);
		setSynchronised(false);
	}

	@Override
	public boolean isLinksWindow() {
		return true;
	}

	/**
	 * Page state should reflect active window when links window is being used after being closed.  
	 * Not enough to select default bible because another module type may be selected in link.
	 */
	protected void initialisePageStateIfClosed(Window activeWindow) {
		// set links window state from active window if it was closed 
		if (getWindowLayout().getState().equals(WindowState.CLOSED) && !activeWindow.isLinksWindow()) {
			// initialise links window documents from active window
			getPageManager().restoreState(activeWindow.getPageManager().getStateJson());
		}
	}
}
