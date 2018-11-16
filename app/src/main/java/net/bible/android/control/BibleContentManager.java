package net.bible.android.control;

import android.util.Log;

import net.bible.android.control.page.ChapterVerse;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.screen.DocumentViewManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import javax.inject.Inject;

/** Control content of main view screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
@MainBibleActivityScope
public class BibleContentManager {

	private final DocumentViewManager documentViewManager;

	private final WindowControl windowControl;
	
	// previous document and verse (currently displayed on the screen)
	private Book previousDocument;
	private Key previousVerse;
	
	private static final String TAG = "BibleContentManager";

	@Inject
	public BibleContentManager(DocumentViewManager documentViewManager, WindowControl windowControl) {
		this.documentViewManager = documentViewManager;
		this.windowControl = windowControl;

		PassageChangeMediator.getInstance().setBibleContentManager(this);
	}
	
	/* package */ void updateText(Window window) {
		updateText(false, window);
	}
	
	/* package */ void updateText(boolean forceUpdate, Window window) {
		if(window == null) {
			window = windowControl.getActiveWindow();
		}
		CurrentPage currentPage = window.getPageManager().getCurrentPage();
		Book document = currentPage.getCurrentDocument();
		Key key = currentPage.getKey();

		// check for duplicate screen update requests
		if (!forceUpdate && 
				document!=null && document.equals(previousDocument) && 
				key!=null && key.equals(previousVerse)) {
			Log.w(TAG, "Duplicated screen update. Doc:"+document.getInitials()+" Key:"+key.getOsisID());
		} else {
			previousDocument = document;
			previousVerse = key;
		}
		new UpdateMainTextTask().execute(window);
	}

	private class UpdateMainTextTask extends UpdateTextTask {
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			PassageChangeMediator.getInstance().contentChangeStarted();
		}

		protected void onPostExecute(String htmlFromDoInBackground) {
			super.onPostExecute(htmlFromDoInBackground);
			PassageChangeMediator.getInstance().contentChangeFinished();
		}

		/** callback from base class when result is ready */
		@Override
		protected void showText(String text, Window window, ChapterVerse chapterVerse, float yOffsetRatio) {
			if (documentViewManager!=null) {
				DocumentView view = documentViewManager.getDocumentView(window);
				view.show(text, chapterVerse, yOffsetRatio);
			} else {
				Log.w(TAG, "Document view not yet registered");
			}
		}
	}
}
