package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import net.bible.android.control.page.splitscreen.Screen.ScreenState;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScreenTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetRestoreStateJson() throws Exception {
		Screen screen = new Screen(2, ScreenState.MINIMISED);
		screen.setSynchronised(true);
		screen.setWeight(1.23456f);
		JSONObject json = screen.getStateJson();
		System.out.println(json);
		screen = new Screen();
		screen.restoreState(json);
		assertThat(screen.getScreenNo(), equalTo(2));
		assertThat(screen.getState(), equalTo(ScreenState.MINIMISED));
		assertThat(screen.isSynchronised(), equalTo(true));
		assertThat(screen.getWeight(), equalTo(1.23456f));
	}

}
