package net.bible.android;

import android.content.res.Resources;

/**
 * Override settings if required
 */
public class TestBibleApplication extends BibleApplication {

	public TestBibleApplication() {
		System.out.println("TestBibleApplication BibleApplication subclass being used.");
	}

	public Resources getLocalizedResources(String language) {
		return getApplication().getResources();
	}
}
