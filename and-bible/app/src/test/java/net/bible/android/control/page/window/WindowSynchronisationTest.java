package net.bible.android.control.page.window;

import net.bible.android.TestBibleApplication;
import net.bible.android.activity.BuildConfig;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.service.sword.SwordDocumentFacade;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import javax.inject.Provider;

import robolectric.MyRobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

@RunWith(MyRobolectricTestRunner.class)
@Config(constants = BuildConfig.class, application = TestBibleApplication.class)
public class WindowSynchronisationTest {

	private EventManager eventManager;

	private WindowRepository windowRepository;
	
	private WindowControl windowControl;

	@Before
	public void setUp() throws Exception {
		eventManager = ABEventBus.getDefault();
		Provider<CurrentPageManager> mockCurrentPageManagerProvider = new Provider<CurrentPageManager>() {
			@Override
			public CurrentPageManager get() {
				return new CurrentPageManager(null, new SwordDocumentFacade(null), null);
			}
		};
		windowRepository = new WindowRepository(mockCurrentPageManagerProvider);
		windowControl = new WindowControl(windowRepository, eventManager);
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
