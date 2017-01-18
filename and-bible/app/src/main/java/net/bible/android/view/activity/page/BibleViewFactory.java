package net.bible.android.view.activity.page;

import net.bible.android.control.bookmark.BookmarkControl;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.window.Window;
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

	private final BookmarkControl bookmarkControl;

	private Map<Window, BibleView> screenBibleViewMap;

	private static final int BIBLE_WEB_VIEW_ID_BASE = 990;

	@Inject
	public BibleViewFactory(MainBibleActivity mainBibleActivity, PageControl pageControl, BookmarkControl bookmarkControl) {
		this.mainBibleActivity = mainBibleActivity;
		this.pageControl = pageControl;
		this.bookmarkControl = bookmarkControl;

		screenBibleViewMap = new WeakHashMap<>();
	}

	public BibleView createBibleView(Window window) {
		BibleView bibleView = screenBibleViewMap.get(window);
		if (bibleView==null) {
			bibleView = new BibleView(this.mainBibleActivity, window);

			VerseActionModeMediator bibleViewVerseActionModeMediator = new VerseActionModeMediator(mainBibleActivity, bibleView, pageControl, new VerseMenuCommandHandler(mainBibleActivity, pageControl, bookmarkControl), bookmarkControl);
			BibleJavascriptInterface bibleJavascriptInterface = new BibleJavascriptInterface(bibleViewVerseActionModeMediator);
			bibleView.setBibleJavascriptInterface(bibleJavascriptInterface);
			bibleView.setId(BIBLE_WEB_VIEW_ID_BASE+window.getScreenNo());
			bibleView.initialise();

			screenBibleViewMap.put(window, bibleView);
		}
		return bibleView;
	}
}
