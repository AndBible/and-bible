package net.bible.android.view.activity.page.actionmode;

import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import net.bible.android.activity.R;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.CurrentPageManager;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
	private CurrentPageManager currentPageManager;

	@Mock
	private ActionMode actionMode;

	@Before
	public void setup() {
		when(pageControl.getCurrentBibleVerse()).thenReturn(TestData.DEFAULT_VERSE);

		when(actionMode.getMenuInflater()).thenReturn(mock(MenuInflater.class));
	}

	@Test
	public void testVerseLongPress() throws Exception {

		verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE);

		verify(mainBibleActivity).showVerseActionModeMenu(any(ActionMode.Callback.class));
		verify(bibleView).highlightVerse(TestData.SELECTED_CHAPTER_VERSE);
		verify(bibleView).enableVerseTouchSelection();
	}

	@Test
	public void testUnselectVerseOnEndActionMode() throws Exception {

		// setup action mode and get callback
		verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE);
		ArgumentCaptor<ActionMode.Callback> callback = ArgumentCaptor.forClass(ActionMode.Callback.class);
		verify(mainBibleActivity).showVerseActionModeMenu(callback.capture());
		callback.getValue().onCreateActionMode(actionMode, mock(Menu.class));

		// call destroy actionmode and check verse is unhighlighted
		callback.getValue().onDestroyActionMode(null);
		verify(bibleView).clearVerseHighlight();
		verify(bibleView).disableVerseTouchSelection();
	}

	@Test
	public void testChangeWindowClearsActionMode() throws Exception {

		// setup actionmode
		verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE);

		// publish window change event
		EventBus.getDefault().post(new CurrentWindowChangedEvent(new Window(3, WindowLayout.WindowState.MAXIMISED, currentPageManager)));

		assertThat(verseActionModeMediator.isActionMode(), is(false));
	}

	@Test
	public void testActionIsCalled() throws Exception {

		// setup action mode and get callback
		verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE);
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
		verseActionModeMediator.verseLongPress(TestData.SELECTED_CHAPTER_VERSE);
		verify(bibleView).highlightVerse(TestData.SELECTED_CHAPTER_VERSE);
		verify(bibleView).enableVerseTouchSelection();

		verseActionModeMediator.verseTouch(TestData.SELECTED_CHAPTER_VERSE_PLUS_1);
		verify(bibleView).highlightVerse(TestData.SELECTED_CHAPTER_VERSE_PLUS_1);
	}

	private interface TestData {
		Verse DEFAULT_VERSE = new Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, 3, 16);
		ChapterVerse SELECTED_CHAPTER_VERSE = new ChapterVerse(3, 3);
		ChapterVerse SELECTED_CHAPTER_VERSE_PLUS_1 = new ChapterVerse(3, 4);
		Verse SELECTED_VERSE = new Verse(Versifications.instance().getVersification("KJV"), BibleBook.JOHN, SELECTED_CHAPTER_VERSE.getChapter(), SELECTED_CHAPTER_VERSE.getVerse());
	}
}