package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.android.control.page.splitscreen.WindowLayout.WindowState;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WindowTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetRestoreStateJson() throws Exception {
		Window window = new Window(2, WindowState.MINIMISED);
		WindowLayout layout = window.getWindowLayout();
		layout.setSynchronised(true);
		layout.setWeight(1.23456f);
		JSONObject json = window.getStateJson();
		System.out.println(json);
		window = new Window();
		window.restoreState(json);
		layout = window.getWindowLayout();
		assertThat(window.getScreenNo(), equalTo(2));
		assertThat(layout.getState(), equalTo(WindowState.MINIMISED));
		assertThat(layout.isSynchronised(), equalTo(true));
		assertThat(layout.getWeight(), equalTo(1.23456f));
	}
}
