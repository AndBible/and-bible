package net.bible.android.control;

import net.bible.android.BibleApplication;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.Window;

/** allow access to control layer
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ControlFactory {

	private static ControlFactory singleton;
	
	public static ControlFactory getInstance() {
		if (singleton==null) {
			synchronized(ControlFactory.class) {
				if (singleton==null) {
					final ControlFactory controlFactory = new ControlFactory();
					singleton = controlFactory;
				}
			}
		}
		return singleton;
	}
	public static void setInstance(ControlFactory controlFactory) {
		singleton = controlFactory;
	}
	
	protected ControlFactory() {
	}
	
	public CurrentPageManager getCurrentPageControl() {
//		ensureAllInitialised();
		Window activeWindow = BibleApplication.getApplication().getControllerComponent().windowControl().getActiveWindow();
		return activeWindow.getPageManager();
	}
}
