package net.bible.android.view.activity.page;

import android.util.Log;

import net.bible.android.control.page.CurrentPageManager;

import org.apache.commons.lang3.StringEscapeUtils;

/**
 * Get next or previous page for insertion at the top or bottom of the current webview.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleInfiniteScrollPopulator {

	private final BibleViewTextInserter bibleViewtextInserter;
	private final CurrentPageManager currentPageManager;

	private static final String TAG = "BibleInfiniteScrollPop";

	public BibleInfiniteScrollPopulator(BibleViewTextInserter bibleViewtextInserter, CurrentPageManager pageManager) {
		this.bibleViewtextInserter = bibleViewtextInserter;
		currentPageManager = pageManager;
	}

	public void requestMoreTextAtTop(String textId) {
		Log.d(TAG, "requestMoreTextAtTop");
		//TODO do in background thread
		// get page fragment for previous chapter
		String fragment = currentPageManager.getCurrentPage().getPreviousPageFragment();
		fragment = StringEscapeUtils.escapeEcmaScript(fragment);
		bibleViewtextInserter.insertTextAtTop(textId, fragment);
	}

	public void requestMoreTextAtEnd(String textId) {
		Log.d(TAG, "requestMoreTextAtEnd");
		//TODO do in background thread
		// get page fragment for previous chapter
		String fragment = currentPageManager.getCurrentPage().getNextPageFragment();
		fragment = StringEscapeUtils.escapeEcmaScript(fragment);
		bibleViewtextInserter.insertTextAtEnd(textId, fragment);
	}
}
