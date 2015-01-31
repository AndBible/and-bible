package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScreenManagerTest {
	private ScreenManager screenManager;

	@Before
	public void setUp() throws Exception {
		screenManager = new ScreenManager();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetScreen() throws Exception {
		assertThat(screenManager.getScreen(1).getScreenNo(), equalTo(1));
	}

	@Test
	public void testGetCurrentActiveScreen() throws Exception {
		assertThat(screenManager.getCurrentActiveScreen().getScreenNo(), equalTo(0));
	}

	@Test
	public void testSetCurrentActiveScreen() throws Exception {
		Screen screen2 = screenManager.getScreen(2);
		screenManager.setCurrentActiveScreen(screen2);
		assertThat(screenManager.getCurrentActiveScreen().getScreenNo(), equalTo(2));
	}

}
