package net.bible.android.control;

import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.UpdateTextTask;
import net.bible.android.control.page.splitscreen.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.screen.DocumentViewManager;
import net.bible.service.common.CommonUtils;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.util.Log;

/** Control content of main view screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleContentManager {

	private DocumentViewManager documentViewManager;
	
	// previous document and verse (currently displayed on the screen)
	private Book previousDocument;
	private Key previousVerse;
	
	private static final String TAG = "BibleContentManager";
	
	public BibleContentManager(DocumentViewManager documentViewManager) {
		this.documentViewManager = documentViewManager;
		
		PassageChangeMediator.getInstance().setBibleContentManager(this);
	}
	
    /* package */ void updateText() {
    	updateText(false);
    }
    
    /* package */ void updateText(boolean forceUpdate) {
    	Screen screen = CommonUtils.getActiveSplitScreen();
    	CurrentPage currentPage = CurrentPageManager.getInstance(screen).getCurrentPage();
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
		new UpdateMainTextTask().execute(screen);
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
    	protected void showText(String text, Screen screen, int verseNo, float yOffsetRatio) {
    		if (documentViewManager!=null) {
    			DocumentView view = documentViewManager.getDocumentView(screen);
    			view.show(text, verseNo, yOffsetRatio);
    		} else {
    			Log.w(TAG, "Document view not yet registered");
    		}
        }
    }
}
