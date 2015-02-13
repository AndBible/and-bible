package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ScreenRepositoryTest {
	private ScreenRepository screenRepository;

	@Before
	public void setUp() throws Exception {
		screenRepository = new ScreenRepository();
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetScreen() throws Exception {
		assertThat(screenRepository.getScreen(1).getScreenNo(), equalTo(1));
	}

	@Test
	public void testGetCurrentActiveScreen() throws Exception {
		assertThat(screenRepository.getCurrentActiveScreen().getScreenNo(), equalTo(0));
	}

	@Test
	public void testSetCurrentActiveScreen() throws Exception {
		Screen screen2 = screenRepository.getScreen(2);
		screenRepository.setCurrentActiveScreen(screen2);
		assertThat(screenRepository.getCurrentActiveScreen().getScreenNo(), equalTo(2));
	}

}
