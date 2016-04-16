package net.bible.android.view.activity.page;

import android.support.v7.view.ActionMode;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.MockitoTestControlFactory;
import net.bible.android.control.TestControlFactory;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowLayout;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.versification.BibleBook;
import org.crosswire.jsword.versification.system.Versifications;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import de.greenrobot.event.EventBus;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@RunWith(MockitoJUnitRunner.class)
public class VerseActionModeMediatorTest {

	@Mock
	private VerseActionModeMediator.ActionModeMenuDisplay mainBibleActivity;

	@Mock
	private VerseActionModeMediator.VerseHighlightControl bibleView;

	@Mock
	private PageControl pageControl;

	@InjectMocks
	private VerseActionModeMediator verseActionModeMediator;

	@Mock
	private ActionMode actionMode;

	@Before
	public void setup() {
		ControlFactory.setInstance(new MockitoTestControlFactory());

		when(pageControl.getCurrentBibleVerse()).thenReturn(TestData.VERSE);
		when(mainBibleActivity.showVerseActionModeMenu(any(ActionMode.Callback.class))).thenReturn(actionMode);
	}

	@Test
	public void testVerseLongPress() throws Exception {
		int selectedVerse = 3;

		verseActionModeMediator.verseLongPress(selectedVerse);

		verify(mainBibleActivity).showVerseActionModeMenu(any(ActionMode.Callback.class));
		verify(bibleView).highlightVerse(3);
	}

	@Test
	public void testUnselectVerseOnEndActionMode() throws Exception {
		int selectedVerse = 3;

		// setup action mode and get callback
		verseActionModeMediator.verseLongPress(selectedVerse);
		ArgumentCaptor<ActionMode.Callback> callback = ArgumentCaptor.forClass(ActionMode.Callback.class);
		verify(mainBibleActivity).showVerseActionModeMenu(callback.capture());

		// call destroy actionmode and check verse is unhighlighted
		callback.getValue().onDestroyActionMode(null);
		verify(bibleView).clearVerseHighlight();
	}

	@Test
	public void testChangeWindowClearsActionMode() throws Exception {
		int selectedVerse = 3;

		// setup actionmode
		verseActionModeMediator.verseLongPress(selectedVerse);

		// publish window change event
		EventBus.getDefault().post(new CurrentWindowChangedEvent(new Window(3, WindowLayout.WindowState.MAXIMISED)));

		verify(actionMode).finish();
	}

	private interface TestData {
		Verse VERSE = new Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, 3, 16);
	}
}