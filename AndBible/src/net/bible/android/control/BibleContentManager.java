package net.bible.android.control;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.control.page.CurrentBiblePage;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.DocumentViewManager;
import net.bible.service.format.FormattedDocument;
import net.bible.service.format.Note;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.os.AsyncTask;
import android.util.Log;

/** Control content of main view screen
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleContentManager {
	
	// bible and verse displayed on the screen
	private Book displayedBible;
	private Key displayedVerse;
	private List<Note> notesList;

	private DocumentViewManager documentViewManager;
	
	private static final String TAG = "BibleContentManager";
	
	public BibleContentManager(DocumentViewManager documentViewManager) {
		this.documentViewManager = documentViewManager;
		
		PassageChangeMediator.getInstance().setBibleContentManager(this);
	}
	
    public void updateText() {
    	updateText(false);
    }
    
    public void updateText(boolean forceUpdate) {
    	CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		Book document = currentPage.getCurrentDocument();
		Key key = currentPage.getKey();

		// check for duplicate screen update requests
		if (!forceUpdate && document.equals(displayedBible) && key.equals(displayedVerse)) {
			Log.w(TAG, "Duplicated screen update. Doc:"+document.getInitials()+" Key:"+key);
		}
		new UpdateTextTask().execute(currentPage);
    }

    private class UpdateTextTask extends AsyncTask<CurrentPage, Integer, String> {
    	private int verseNo;
    	private float yScreenOffsetRatio;
    	@Override
    	protected void onPreExecute() {
    		PassageChangeMediator.getInstance().contentChangeStarted();
    	}
    	
		@Override
        protected String doInBackground(CurrentPage... currentPageArgs) {
            Log.d(TAG, "Loading html in background");
        	String text = "Error";
        	try {
        		CurrentPage currentPage = currentPageArgs[0]; 
	    		Book document = currentPage.getCurrentDocument();
	    		// if bible show whole chapter
	    		Key key = currentPage.getKey();
	    		// but allow for jump to specific verse e.g. after search result
	    		if (currentPage instanceof CurrentBiblePage) {
	    			verseNo = ((CurrentBiblePage)currentPage).getCurrentVerseNo();
	    		}
	    		yScreenOffsetRatio = currentPage.getCurrentYOffsetRatio();
	
	            Log.d(TAG, "Loading document:"+document.getInitials()+" key:"+key);
	            
	            notesList = new ArrayList<Note>();
	            
	            FormattedDocument formattedDocument = currentPage.getCurrentPageContent();
	            text = formattedDocument.getHtmlPassage();
	            notesList = formattedDocument.getNotesList();
	
	            displayedBible = document;
	            displayedVerse = key;
		            
        	} catch (Exception e) {
        		Log.e(TAG, "Error getting bible text", e);
        		text = "Error getting bible text: "+e.getMessage();
	    	} catch (OutOfMemoryError oom) {
	    		Log.e(TAG, "Out of memory error", oom);
	    		System.gc();
	    		text = "Error: document section is too large.";
	    	}
        	return text;
        }

        protected void onPostExecute(String htmlFromDoInBackground) {
            Log.d(TAG, "Loading html:"+htmlFromDoInBackground);
            showText(htmlFromDoInBackground, verseNo, yScreenOffsetRatio);
    		PassageChangeMediator.getInstance().contentChangeFinished();
        }
    }
	private void showText(String text, int verseNo, float yOffsetRatio) {
		if (documentViewManager!=null) {
			DocumentView view = documentViewManager.getDocumentView();
			view.show(text, verseNo, yOffsetRatio);
		} else {
			Log.w(TAG, "Document view not yet registered");
		}
    }

	public List<Note> getNotesList() {
		return notesList;
	}
}
