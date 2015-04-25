package net.bible.android.control.page.splitscreen;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.splitscreen.NumberOfWindowsChangedEvent;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class WindowControlTest {

	private static final Book BOOK_KJV = Books.installed().getBook("KJV");
	private static final Verse PS_139_3 = new Verse(Versifications.instance().getVersification("KJV"), BibleBook.PS, 139, 3);

	private EventManager eventManager;

	private WindowRepository windowRepository;
	
	private WindowControl windowControl;
	
	@Before
	public void setUp() throws Exception {
		eventManager = mock(EventManager.class);
		windowRepository = new WindowRepository(eventManager);
		windowControl = new WindowControl(windowRepository, eventManager);
	}

	@Test
	public void testGetActiveWindow() throws Exception {
		// should always be one default window that is active by default
		assertThat(windowControl.getActiveWindow().getScreenNo(), equalTo(1));
	}

	@Test
	public void testIsCurrentActiveWindow() throws Exception {
		Window activeWindow = windowControl.getActiveWindow();
		assertThat(windowControl.isCurrentActiveWindow(activeWindow), is(true));
	}

	@Test
	public void testShowLink() throws Exception {
		windowControl.showLink(BOOK_KJV, PS_139_3);
		
		Window linksWindow = windowRepository.getDedicatedLinksWindow();
		assertThat(linksWindow.getPageManager().getCurrentBible().getSingleKey(), equalTo((Key)PS_139_3));
		assertThat(linksWindow.getPageManager().getCurrentBible().getSingleKey(), equalTo((Key)PS_139_3));
		assertThat(linksWindow.getWindowLayout().getState(), equalTo(WindowLayout.WindowState.SPLIT));
		assertThat(windowRepository.isMultiWindow(), is(true));
	}

	@Test
	public void testAddNewWindow() throws Exception {
		Window newWindow = windowControl.addNewWindow();
		assertThat(windowControl.getWindowRepository().getWindows(), hasItem(newWindow));

		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
	public void testMinimiseWindow() throws Exception {
		Window newWindow = windowControl.addNewWindow();
		reset(eventManager);

		windowControl.minimiseWindow(newWindow);
		assertThat(windowControl.getWindowRepository().getVisibleWindows(), not(hasItem(newWindow)));
		
		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
	public void testRemoveWindow() throws Exception {
	}

	@Test
	public void testRestoreWindow() throws Exception {
	}

	@Test
	public void testOrientationChange() throws Exception {
	}

	@Test
	public void testSynchronizeScreens() throws Exception {
	}

	@Test
	public void testIsSplit() throws Exception {
	}

	@Test
	public void testGetCurrentActiveWindow() throws Exception {
	}

	@Test
	public void testSetCurrentActiveWindow() throws Exception {
	}

}
