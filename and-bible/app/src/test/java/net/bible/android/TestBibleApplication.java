package net.bible.android;

import android.content.res.Resources;
import android.util.DisplayMetrics;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Override settings if required
 */
public class TestBibleApplication extends BibleApplication {

	private Resources mockResources;

	public TestBibleApplication() {
		System.out.println("TestBibleApplication BibleApplication subclass being used.");

		mockResources = mock(Resources.class);
		when(mockResources.getText(anyInt())).thenReturn("Test text");
		when(mockResources.getDisplayMetrics()).thenReturn(mock(DisplayMetrics.class));
	}

	@Override
	public Resources getResources() {
		return mockResources;
	}
}
