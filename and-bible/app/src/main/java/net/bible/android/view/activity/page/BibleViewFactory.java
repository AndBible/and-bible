package net.bible.android.view.activity.page;

import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.link.LinkControl;
import net.bible.android.control.mynote.MyNoteControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.PageTiltScrollControlFactory;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.MainBibleActivityScope;

import java.util.Map;
import java.util.WeakHashMap;

import javax.inject.Inject;

/**
 * Build a new BibleView WebView for a Window
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 * The copyright to this program is held by it's author.
 */
@MainBibleActivityScope
public class BibleViewFactory {

	private final MainBibleActivity mainBibleActivity;

	private final PageControl pageControl;

	private final PageTiltScrollControlFactory pageTiltScrollControlFactory;

	private final WindowControl windowControl;

	private final LinkControl linkControl;

	private final BookmarkControl bookmarkControl;

	private final MyNoteControl myNoteControl;

	private Map<Window, BibleView> screenBibleViewMap;

	private static final int BIBLE_WEB_VIEW_ID_BASE = 990;

	@Inject
	public BibleViewFactory(MainBibleActivity mainBibleActivity, PageControl pageControl, PageTiltScrollControlFactory pageTiltScrollControlFactory, WindowControl windowControl, LinkControl linkControl, BookmarkControl bookmarkControl, MyNoteControl myNoteControl) {
		this.mainBibleActivity = mainBibleActivity;
		this.pageControl = pageControl;
		this.pageTiltScrollControlFactory = pageTiltScrollControlFactory;
		this.windowControl = windowControl;
		this.linkControl = linkControl;
		this.bookmarkControl = bookmarkControl;
		this.myNoteControl = myNoteControl;

		screenBibleViewMap = new WeakHashMap<>();
	}

	public BibleView createBibleView(Window window) {
		BibleView bibleView = screenBibleViewMap.get(window);
		if (bibleView==null) {
			final PageTiltScrollControl pageTiltScrollControl = pageTiltScrollControlFactory.getPageTiltScrollControl(window);
			bibleView = new BibleView(this.mainBibleActivity, window, windowControl, pageControl, pageTiltScrollControl, linkControl);

			VerseActionModeMediator bibleViewVerseActionModeMediator = new VerseActionModeMediator(mainBibleActivity, bibleView, pageControl, new VerseMenuCommandHandler(mainBibleActivity, pageControl, bookmarkControl, myNoteControl), bookmarkControl);
			BibleJavascriptInterface bibleJavascriptInterface = new BibleJavascriptInterface(bibleViewVerseActionModeMediator, windowControl);
			bibleView.setBibleJavascriptInterface(bibleJavascriptInterface);
			bibleView.setId(BIBLE_WEB_VIEW_ID_BASE+window.getScreenNo());
			bibleView.initialise();

			screenBibleViewMap.put(window, bibleView);
		}
		return bibleView;
	}
}
