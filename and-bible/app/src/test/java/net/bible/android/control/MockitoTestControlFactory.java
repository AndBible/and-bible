package net.bible.android.control;

import net.bible.android.control.page.window.ActiveWindowPageManagerProvider;

/**
 * Prevent initialisation of control objects as they will be mocked
 */
public class MockitoTestControlFactory extends ControlFactory {

	private ActiveWindowPageManagerProvider overrideWindowControl;

	private boolean enableDefaults = true;

	public MockitoTestControlFactory() {
	}

}
