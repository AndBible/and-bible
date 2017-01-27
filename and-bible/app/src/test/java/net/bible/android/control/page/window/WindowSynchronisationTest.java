package net.bible.android.control.page.window;

import net.bible.android.activity.BuildConfig;
import net.bible.android.control.TestControlFactory;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.page.CurrentPageManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import javax.inject.Provider;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class WindowSynchronisationTest {

	private EventManager eventManager;

	private WindowRepository windowRepository;
	
	private WindowControl windowControl;

	private Provider<CurrentPageManager> mockCurrentPageManagerProvider;

	@Before
	public void setUp() throws Exception {
		eventManager = ABEventBus.getDefault();
		mockCurrentPageManagerProvider = mock(Provider.class);
		windowRepository = new WindowRepository(mockCurrentPageManagerProvider);
		windowRepository.initialise();
		windowControl = new WindowControl(windowRepository);
		
		TestControlFactory testControlFactory = new TestControlFactory();
		testControlFactory.setWindowControl(windowControl);
	}

	@Test
	public void testSynchronizeScreens() throws Exception {
		Window window2 = windowControl.addNewWindow();
		assertThat(window2.getPageManager().getCurrentBible().getCurrentVerseNo(), not(equalTo(7)));

		Window mainWindow = windowControl.getActiveWindow();
		mainWindow.getPageManager().getCurrentBible().setCurrentVerseNo(7);
		assertThat(mainWindow.getPageManager().getCurrentBible().getCurrentVerseNo(), equalTo(7));

		Thread.sleep(500);
		assertThat(window2.getPageManager().getCurrentBible().getCurrentVerseNo(), equalTo(7));
	}

}
