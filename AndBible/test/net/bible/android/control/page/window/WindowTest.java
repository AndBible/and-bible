package net.bible.android.control.page.window;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.android.control.page.window.WindowLayout.WindowState;

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
		// initialise Window
		Window window = new Window(2, WindowState.MINIMISED);
		WindowLayout layout = window.getWindowLayout();
		window.setSynchronised(true);
		layout.setWeight(1.23456f);
		
		// serialize state
		JSONObject json = window.getStateJson();
		System.out.println(json);
		
		// recreate window from saved state
		window = new Window();
		window.restoreState(json);
		layout = window.getWindowLayout();
		assertThat(window.getScreenNo(), equalTo(2));
		assertThat(layout.getState(), equalTo(WindowState.MINIMISED));
		assertThat(window.isSynchronised(), equalTo(true));
		assertThat(layout.getWeight(), equalTo(1.23456f));
	}
}
