package net.bible.android.control;

import net.bible.android.control.page.window.WindowControl;

public class TestControlFactory extends ControlFactory {

	private WindowControl overrideWindowControl; 
	
	public TestControlFactory() {
		setInstance(this);
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
