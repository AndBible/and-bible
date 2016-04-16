package net.bible.android.control;

import net.bible.android.control.page.window.WindowControl;

/**
 * Use with RoboElectric or to overide certain control objects
 */
public class TestControlFactory extends ControlFactory {

	private WindowControl overrideWindowControl; 

	public TestControlFactory() {
	}

	public void setWindowControl(WindowControl windowControl) {
		overrideWindowControl = windowControl;
	}
	
	public WindowControl getWindowControl() {
		if (overrideWindowControl!=null) {
			return overrideWindowControl;
		} else {
			return super.getWindowControl();
		}
	}

}
