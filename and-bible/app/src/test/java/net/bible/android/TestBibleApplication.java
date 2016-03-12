package net.bible.android;

import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

/**
 * Override settings if required
 */
public class TestBibleApplication extends BibleApplication implements TestLifecycleApplication {

	public TestBibleApplication() {
		System.out.println("TestBibleApplication BibleApplication subclass being used.");
	}

	@Override
	public void beforeTest(Method method) {

	}

	@Override
	public void prepareTest(Object test) {

	}

	@Override
	public void afterTest(Method method) {

	}
}
