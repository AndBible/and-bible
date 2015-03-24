package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WindowRepositoryTest {
	private WindowRepository windowRepository;

	@Before
	public void setUp() throws Exception {
		windowRepository = new WindowRepository();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
		public void testGetWindow() throws Exception {
			assertThat(windowRepository.getWindow(1).getScreenNo(), equalTo(1));
		}

	@Test
		public void testGetCurrentActiveWindow() throws Exception {
			assertThat(windowRepository.getCurrentActiveWindow().getScreenNo(), equalTo(1));
		}

	@Test
		public void testSetCurrentActiveWindow() throws Exception {
			Window screen2 = windowRepository.getWindow(2);
			windowRepository.setCurrentActiveWindow(screen2);
			assertThat(windowRepository.getCurrentActiveWindow().getScreenNo(), equalTo(2));
		}

}
