package net.bible.android.control.page.window;

import net.bible.android.TestBibleApplication;
import net.bible.android.activity.BuildConfig;
import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.window.WindowLayout.WindowState;
import net.bible.service.sword.SwordDocumentFacade;
import net.bible.test.PassageTestData;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import robolectric.MyRobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(MyRobolectricTestRunner.class)
@Config(constants = BuildConfig.class, application = TestBibleApplication.class)
public class WindowTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetRestoreStateJson() throws Exception {
		CurrentPageManager mockCurrentPageManager = new CurrentPageManager(null, new SwordDocumentFacade(null), null, null);

		// initialise Window
		Window window = new Window(2, WindowState.MINIMISED, mockCurrentPageManager);
		WindowLayout layout = window.getWindowLayout();
		window.setSynchronised(true);
		layout.setWeight(1.23456f);
		
		CurrentPageManager pageManager = window.getPageManager();
		CurrentBiblePage biblePage = pageManager.getCurrentBible();
		biblePage.setCurrentDocumentAndKey(PassageTestData.ESV, PassageTestData.PS_139_2);
		
		// serialize state
		JSONObject json = window.getStateJson();
		System.out.println(json);
		
		// recreate window from saved state
		window = new Window(mockCurrentPageManager);
		window.restoreState(json);
		layout = window.getWindowLayout();
		assertThat(window.getScreenNo(), equalTo(2));
		assertThat(layout.getState(), equalTo(WindowState.MINIMISED));
		assertThat(window.isSynchronised(), equalTo(true));
		assertThat(layout.getWeight(), equalTo(1.23456f));

		pageManager = window.getPageManager();
		biblePage = pageManager.getCurrentBible();
		assertThat(biblePage.getCurrentDocument(), equalTo(PassageTestData.ESV));
		assertThat(biblePage.getSingleKey().getName(), equalTo(PassageTestData.PS_139_2.getName()));
	}
}
