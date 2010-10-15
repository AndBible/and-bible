package net.bible.android.currentpagecontrol;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.view.BibleView;
import net.bible.service.format.FormattedDocument;
import net.bible.service.format.Note;
import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.content.SharedPreferences;
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

	private BibleView bibleWebView;
	private Context context;
	
	private static final String TAG = "BibleView";
	
	private String NO_CONTENT = "No content for selected verse";
	
	public BibleContentManager(BibleView bibleWebView, Context context) {
		this.context = context;
		this.bibleWebView = bibleWebView;
		
		PassageChangeMediator.getInstance().setBibleContentManager(this);
	}
	
    public void updateText() {
    	updateText(false);
    }
    
    public void updateText(boolean forceUpdate) {
    	CurrentPage currentPage = CurrentPageManager.getInstance().getCurrentPage();
		Book document = currentPage.getCurrentDocument();
		Key key = currentPage.getKey();

//		 scrolling right in a commentary can sometimes cause duplicate updates and I don't know why - catch them for now
//		if (forceUpdate || (!bible.equals(displayedBible) || !verse.equals(displayedVerse))) {
//			new UpdateTextTask().execute(currentPassage);
//		}
		if (!forceUpdate && document.equals(displayedBible) && key.equals(displayedVerse)) {
			Log.w(TAG, "Duplicated screen update. Doc:"+document.getInitials()+" Key:"+key);
		}
		new UpdateTextTask().execute(currentPage);
    }

    private class UpdateTextTask extends AsyncTask<CurrentPage, Integer, String> {
    	int verseNo;
    	@Override
    	protected void onPreExecute() {
    		PassageChangeMediator.getInstance().contentChangeStarted();
    	}
    	
		@Override
        protected String doInBackground(CurrentPage... currentPassageArgs) {
            Log.d(TAG, "Loading html in background");
        	String text = "Error";
        	try {
        		CurrentPage currentPassage = currentPassageArgs[0]; 
	    		Book document = currentPassage.getCurrentDocument();
	    		// if bible show whole chapter
	    		Key key = currentPassage.getKey();
	
	    		SharedPreferences preferences = context.getSharedPreferences("net.bible.android.activity_preferences", 0);
	    		
	            Log.d(TAG, "Loading document:"+document.getInitials()+" key:"+key);
	    		//setText("Loading "+verse.toString());
	            SwordApi swordApi = SwordApi.getInstance();
	            swordApi.setPreferences(preferences);
	            
	            notesList = new ArrayList<Note>();
	            
	            FormattedDocument formattedDocument = swordApi.readHtmlText(document, key, 200);
	            text = formattedDocument.getHtmlPassage();
	            notesList = formattedDocument.getNotesList();
	            
	            if (StringUtils.isEmpty(text)) {
	            	text = NO_CONTENT;
	            }
	
	            displayedBible = document;
	            displayedVerse = key;
		            
        	} catch (Exception e) {
        		Log.e(TAG, "Error getting bible text", e);
        		text = "Error getting bible text: "+e.getMessage();
        	}
        	return text;
        }

        protected void onPostExecute(String htmlFromDoInBackground) {
            Log.d(TAG, "Loading html:"+htmlFromDoInBackground);
            showText(htmlFromDoInBackground, verseNo);
    		PassageChangeMediator.getInstance().contentChangeFinished();
        }
    }
	private void showText(String text, int verseNo) {
        bibleWebView.show(text, verseNo);
    }

	public List<Note> getNotesList() {
		return notesList;
	}
}
