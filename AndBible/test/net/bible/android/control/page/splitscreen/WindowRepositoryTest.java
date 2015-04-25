package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.EventManagerStub;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WindowRepositoryTest {
	private WindowRepository windowRepository;

	@Before
	public void setUp() throws Exception {
		EventManager eventManager = new EventManagerStub();
		windowRepository = new WindowRepository(eventManager);
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
		Window newWindow = windowRepository.addNewWindow();
		assertThat(windowRepository.getCurrentActiveWindow().getScreenNo(), not(equalTo(newWindow.getScreenNo())));
		windowRepository.setCurrentActiveWindow(newWindow);
		assertThat(windowRepository.getCurrentActiveWindow().getScreenNo(), equalTo(newWindow.getScreenNo()));
	}

}
