package net.bible.android.control.page.window;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import net.bible.android.activity.R;
import net.bible.android.control.event.EventManager;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.page.window.WindowLayout.WindowState;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Books;
import org.crosswire.jsword.passage.Key;
import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import android.support.v7.internal.view.menu.MenuBuilder;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

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
		reset(eventManager);
	}

	@Test
	public void testGetActiveWindow() throws Exception {
		// should always be one default window that is active by default
		assertThat(windowControl.getActiveWindow().getScreenNo(), equalTo(1));
	}

	@Test
	public void testSetActiveWindow() throws Exception {
		Window window1 = windowControl.getActiveWindow();
		
		Window newWindow = windowControl.addNewWindow();
		assertThat(window1, equalTo(windowControl.getActiveWindow()));
		
		windowControl.setActiveWindow(newWindow);
		assertThat(newWindow, equalTo(windowControl.getActiveWindow()));
	}

	@Test
	public void testIsCurrentActiveWindow() throws Exception {
		Window activeWindow = windowControl.getActiveWindow();
		assertThat(windowControl.isActiveWindow(activeWindow), is(true));
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
		assertThat(windowRepository.getWindows(), hasItem(newWindow));

		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
	public void testMinimiseWindow() throws Exception {
		Window newWindow = windowControl.addNewWindow();
		reset(eventManager);

		windowControl.minimiseWindow(newWindow);
		assertThat(windowRepository.getVisibleWindows(), not(hasItem(newWindow)));
		
		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
	public void testMinimiseOnlyWindowPrevented() throws Exception {
		Window onlyWindow = windowControl.getActiveWindow();
		windowControl.minimiseWindow(onlyWindow);
		assertThat(windowRepository.getVisibleWindows(), hasItem(onlyWindow));
		verifyZeroInteractions(eventManager);

		// test still prevented if links window is visible
		windowRepository.getDedicatedLinksWindow().getWindowLayout().setState(WindowState.SPLIT);
		windowControl.minimiseWindow(onlyWindow);
		assertThat(windowRepository.getVisibleWindows(), hasItem(onlyWindow));
		verifyZeroInteractions(eventManager);
	}

	@Test
	public void testMaximiseWindow() throws Exception {
		Window newWindow = windowControl.addNewWindow();
		windowControl.setActiveWindow(newWindow);
		reset(eventManager);

		windowControl.maximiseWindow(newWindow);
		assertThat(windowRepository.getVisibleWindows(), hasItem(newWindow));
		assertThat(windowRepository.getVisibleWindows(), hasSize(1));
		
		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
	public void testMaximiseWindowClosesLinksWindow() throws Exception {
		Window activeWindow = windowControl.getActiveWindow();
		windowControl.addNewWindow(); // add an extra window for good measure
		windowControl.showLinkUsingDefaultBible(PS_139_3);
		windowControl.setActiveWindow(activeWindow);
		assertThat(windowRepository.getVisibleWindows(), hasSize(3));
		reset(eventManager);

		// making window active should remove links window
		windowControl.maximiseWindow(activeWindow);
		assertThat(windowRepository.getVisibleWindows(), contains(activeWindow));

		// showing link should re-display links window despite window being maximised
		windowControl.showLinkUsingDefaultBible(PS_139_3);
		assertThat(windowRepository.getVisibleWindows(), contains(activeWindow, windowRepository.getDedicatedLinksWindow()));
	}

	@Test
		public void testCloseWindow() throws Exception {
			Window newWindow = windowControl.addNewWindow();
			reset(eventManager);
	
			windowControl.closeWindow(newWindow);
			assertThat(windowRepository.getWindows(), not(hasItem(newWindow)));
			
			verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
		}

	@Test
	public void testCloseOnlyWindowPrevented() throws Exception {
		Window onlyWindow = windowRepository.getActiveWindow(); 
		windowControl.closeWindow(onlyWindow);
		assertThat(windowRepository.getWindows(), hasItem(onlyWindow));
		
		verifyZeroInteractions(eventManager);
	}

	@Test
		public void testCloseWindowPreventedIfOnlyOtherIsLinks() throws Exception {
			windowRepository.getDedicatedLinksWindow().getWindowLayout().setState(WindowState.SPLIT);
			Window onlyNormalWindow = windowRepository.getActiveWindow();
			windowControl.closeWindow(onlyNormalWindow);
			assertThat(windowRepository.getWindows(), hasItem(onlyNormalWindow));
			
			verifyZeroInteractions(eventManager);
		}

	@Test
	public void testCloseActiveWindow() throws Exception {
		Window activeWindow = windowControl.getActiveWindow();
		Window newWindow = windowControl.addNewWindow();
		reset(eventManager);

		windowControl.closeWindow(activeWindow);
		assertThat(windowRepository.getActiveWindow(), equalTo(newWindow));
	}

	@Test
	public void testRestoreWindow() throws Exception {
		Window newWindow = windowControl.addNewWindow();
		windowControl.minimiseWindow(newWindow);
		assertThat(windowRepository.getVisibleWindows(), hasSize(1));
		reset(eventManager);

		windowControl.restoreWindow(newWindow);
		assertThat(windowRepository.getWindows(), hasSize(2));
		
		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
	public void testOrientationChange() throws Exception {
		windowControl.orientationChange();
		verify(eventManager, times(1)).post(argThat(isA(NumberOfWindowsChangedEvent.class)));
	}

	@Test
		public void testIsMultiWindow() throws Exception {
			assertThat(windowControl.isMultiWindow(), equalTo(false));
			Window newWindow = windowControl.addNewWindow();
			assertThat(windowControl.isMultiWindow(), equalTo(true));
			windowControl.closeWindow(newWindow);
			assertThat(windowControl.isMultiWindow(), equalTo(false));
		}

	@Test
	public void testUpdateSynchronisedMenuItem() {
		Menu menu = new MenuBuilder(Robolectric.application);
		new MenuInflater(Robolectric.application).inflate(R.menu.main, menu);

		windowControl.updateOptionsMenu(menu);
		MenuItem synchronisedMenuItem = menu.findItem(R.id.windowSynchronise);
		assertThat(synchronisedMenuItem.isChecked(), equalTo(true));
		
		windowControl.getActiveWindow().setSynchronised(false);
		windowControl.updateOptionsMenu(menu);
		assertThat(synchronisedMenuItem.isChecked(), equalTo(false));
	}

	@Test
	public void testDisableMenuItemsIfLinksWindowActive() {
		Window normalWindow = windowControl.getActiveWindow();
		windowControl.addNewWindow();
		
		Menu menu = new MenuBuilder(Robolectric.application);
		new MenuInflater(Robolectric.application).inflate(R.menu.main, menu);
		windowControl.updateOptionsMenu(menu);
		MenuItem synchronisedMenuItem = menu.findItem(R.id.windowSynchronise);
		MenuItem moveFirstMenuItem = menu.findItem(R.id.windowMoveFirst);
		MenuItem minimiseMenuItem = menu.findItem(R.id.windowMinimise);

		assertThat(synchronisedMenuItem.isEnabled(), equalTo(true));
        Window linksWindow = windowRepository.getDedicatedLinksWindow();
        windowControl.setActiveWindow(linksWindow);
		windowControl.updateOptionsMenu(menu);
		assertThat(synchronisedMenuItem.isEnabled(), equalTo(false));
		assertThat(moveFirstMenuItem.isEnabled(), equalTo(false));
		assertThat(minimiseMenuItem.isEnabled(), equalTo(false));
		
		// check menu items are re-enabled when a normal window becomes active
		windowControl.setActiveWindow(normalWindow);
		windowControl.updateOptionsMenu(menu);
		assertThat(synchronisedMenuItem.isEnabled(), equalTo(true));
	}

	@Test
	public void testDisableMenuItemsIfOnlyOneWindow() {
		// the links window should not allow minimise or close to work if only 1 other window
		windowRepository.getDedicatedLinksWindow().getWindowLayout().setState(WindowState.SPLIT);
		
		Menu menu = new MenuBuilder(Robolectric.application);
		new MenuInflater(Robolectric.application).inflate(R.menu.main, menu);

		windowControl.updateOptionsMenu(menu);
		MenuItem minimiseMenuItem = menu.findItem(R.id.windowMinimise);
		MenuItem closeMenuItem = menu.findItem(R.id.windowClose);
		assertThat(minimiseMenuItem.isEnabled(), equalTo(false));
		assertThat(closeMenuItem.isEnabled(), equalTo(false));
	}

	@Test
	public void testCannotMoveFirstWindowFirst() {
		windowControl.addNewWindow();
		
		Menu menu = new MenuBuilder(Robolectric.application);
		new MenuInflater(Robolectric.application).inflate(R.menu.main, menu);

		windowControl.updateOptionsMenu(menu);
		MenuItem moveFirstMenuItem = menu.findItem(R.id.windowMoveFirst);
		assertThat(moveFirstMenuItem.isEnabled(), equalTo(false));
	}
}
