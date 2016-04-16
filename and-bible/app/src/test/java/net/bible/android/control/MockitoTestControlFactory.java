package net.bible.android.control;

import net.bible.android.control.page.window.WindowControl;

/**
 * Prevent initialisation of control objects as they will be mocked
 */
public class MockitoTestControlFactory extends ControlFactory {

	private WindowControl overrideWindowControl;

	private boolean enableDefaults = true;

	public MockitoTestControlFactory() {
	}

	@Override
	protected void createAll() {
		if (enableDefaults) {
			super.createAll();
		}
	}

	@Override
	protected void ensureAllInitialised() {
		if (enableDefaults) {
			super.ensureAllInitialised();
		}
	}
}
