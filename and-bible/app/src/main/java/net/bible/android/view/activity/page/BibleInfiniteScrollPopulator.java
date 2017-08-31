package net.bible.android.view.activity.page;

import android.util.Log;

/**
 * Get next or previous page for insertion at the top or bottom of the current webview.
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleInfiniteScrollPopulator {

	private final BibleViewTextInserter bibleViewtextInserter;

	private static final String TAG = "BibleInfiniteScrollPop";

	public BibleInfiniteScrollPopulator(BibleViewTextInserter bibleViewtextInserter) {
		this.bibleViewtextInserter = bibleViewtextInserter;
	}

	public void requestMoreTextAtTop(String textId) {
		Log.d(TAG, "requestMoreTextAtTop");
		//TODO get page fragment
		String fragment = "<h1>Top</h1><p>Top fragment</p>";
		bibleViewtextInserter.insertTextAtTop(textId, fragment);
	}

	public void requestMoreTextAtEnd(String textId) {
		Log.d(TAG, "requestMoreTextAtEnd");
		//TODO get page fragment
		String fragment = "<h1>End</h1><p>End fragment</p>";
		bibleViewtextInserter.insertTextAtEnd(textId, fragment);
	}
}
