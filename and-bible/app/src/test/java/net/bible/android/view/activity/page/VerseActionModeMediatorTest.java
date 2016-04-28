package net.bible.android.view.activity.page;

import android.support.v7.view.ActionMode;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.MockitoTestControlFactory;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowLayout;

import org.crosswire.jsword.passage.Verse;
import org.crosswire.jsword.passage.VerseRange;
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
import static org.mockito.Mockito.mock;
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

	@Mock
	private VerseMenuCommandHandler verseMenuCommandHandler;

	@InjectMocks
	private VerseActionModeMediator verseActionModeMediator;

	@Mock
	private ActionMode actionMode;

	@Before
	public void setup() {
		ControlFactory.setInstance(new MockitoTestControlFactory());

		when(pageControl.getCurrentBibleVerse()).thenReturn(TestData.DEFAULT_VERSE);
		when(mainBibleActivity.showVerseActionModeMenu(any(ActionMode.Callback.class))).thenReturn(actionMode);
	}

	@Test
	public void testVerseLongPress() throws Exception {

		verseActionModeMediator.verseLongPress(TestData.SELECTED_VERSE_NO);

		verify(mainBibleActivity).showVerseActionModeMenu(any(ActionMode.Callback.class));
		verify(bibleView).highlightVerse(TestData.SELECTED_VERSE_NO);
		verify(bibleView).enableVerseTouchSelection();
	}

	@Test
	public void testUnselectVerseOnEndActionMode() throws Exception {

		// setup action mode and get callback
		verseActionModeMediator.verseLongPress(TestData.SELECTED_VERSE_NO);
		ArgumentCaptor<ActionMode.Callback> callback = ArgumentCaptor.forClass(ActionMode.Callback.class);
		verify(mainBibleActivity).showVerseActionModeMenu(callback.capture());

		// call destroy actionmode and check verse is unhighlighted
		callback.getValue().onDestroyActionMode(null);
		verify(bibleView).clearVerseHighlight();
		verify(bibleView).disableVerseTouchSelection();
	}

	@Test
	public void testChangeWindowClearsActionMode() throws Exception {

		// setup actionmode
		verseActionModeMediator.verseLongPress(TestData.SELECTED_VERSE_NO);

		// publish window change event
		EventBus.getDefault().post(new CurrentWindowChangedEvent(new Window(3, WindowLayout.WindowState.MAXIMISED)));

		verify(actionMode).finish();
	}

	@Test
	public void testActionIsCalled() throws Exception {

		// setup action mode and get callback
		verseActionModeMediator.verseLongPress(TestData.SELECTED_VERSE_NO);
		ArgumentCaptor<ActionMode.Callback> callback = ArgumentCaptor.forClass(ActionMode.Callback.class);
		verify(mainBibleActivity).showVerseActionModeMenu(callback.capture());

		// call destroy actionmode and check verse is unhighlighted
		MenuItem menuItem = mock(MenuItem.class);
		when(menuItem.getItemId()).thenReturn(R.id.compareTranslations);

		callback.getValue().onActionItemClicked(null, menuItem);

		verify(verseMenuCommandHandler).handleMenuRequest(R.id.compareTranslations, new VerseRange(TestData.SELECTED_VERSE.getVersification(), TestData.SELECTED_VERSE));
	}

	@Test
	public void testExpandToNextVerse() throws Exception {

		// setup action mode and get callback
		verseActionModeMediator.verseLongPress(TestData.SELECTED_VERSE_NO);
		verify(bibleView).highlightVerse(TestData.SELECTED_VERSE_NO);
		verify(bibleView).enableVerseTouchSelection();

		verseActionModeMediator.verseTouch(TestData.SELECTED_VERSE_NO+1);
		verify(bibleView).highlightVerse(TestData.SELECTED_VERSE_NO+1);
	}

	private interface TestData {
		Verse DEFAULT_VERSE = new Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, 3, 16);
		int SELECTED_VERSE_NO = 3;
		Verse SELECTED_VERSE = new Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, 3, SELECTED_VERSE_NO);
	}
}