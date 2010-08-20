package net.bible.android.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;
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
		
		initialise();		
	}
	
	private void initialise() {
		CurrentPassage.getInstance().addObserver(new Observer() {
			@Override
			public void update(Observable observable, Object data) {
				updateText();
			}
		});
		
		//****
		// force a reload to get things going
		// whether we need to do this depends if the above listener is set up before the CurerentPassage (it currently is not)
		updateText();
	}

    public void updateText() {
    	updateText(false);
    }
    
    public void updateText(boolean forceUpdate) {
    	CurrentPassage currentPassage = CurrentPassage.getInstance();
		Book bible = currentPassage.getCurrentDocument();
		Key verse = currentPassage.getKey();

		// scrolling right in a commentary can sometimes cause duplicate updates and I don't know why - catch them for now
		if (forceUpdate || (!bible.equals(displayedBible) || !verse.equals(displayedVerse))) {
			new UpdateTextTask().execute(currentPassage);
		}
    }

    private class UpdateTextTask extends AsyncTask<CurrentPassage, Integer, String> {
    	int verseNo;
    	
		@Override
        protected String doInBackground(CurrentPassage... currentPassageArgs) {
        	String text = "Error";
        	try {
        		CurrentPassage currentPassage = currentPassageArgs[0]; 
	    		Book bible = currentPassage.getCurrentDocument();
	    		// if bible show whole chapter
	    		Key verses = currentPassage.getKey();
	    		verseNo = currentPassage.getCurrentVerse();
	
	    		SharedPreferences preferences = context.getSharedPreferences("net.bible.android.activity_preferences", 0);
	    		// wait until after current verse has been fetched because the following may change the current verse 
//    			scrollTo(0, 0);
	    		
	            Log.d(TAG, "Loading "+verses);
	    		//setText("Loading "+verse.toString());
	            SwordApi swordApi = SwordApi.getInstance();
	            swordApi.setPreferences(preferences);
	            
	            notesList = new ArrayList<Note>();
	            
	            FormattedDocument formattedDocument = swordApi.readHtmlText(bible, verses, 200);
	            text = formattedDocument.getHtmlPassage();
	            notesList = formattedDocument.getNotesList();
	            
	            if (StringUtils.isEmpty(text)) {
	            	text = NO_CONTENT;
	            }
	
	            displayedBible = bible;
	            displayedVerse = verses;
		            
        	} catch (Exception e) {
        		Log.e(TAG, "Error getting bible text", e);
        		text = "Error getting bible text: "+e.getMessage();
        	}
        	return text;
        }

        protected void onPostExecute(String htmlFromDoInBackground) {
            Log.d(TAG, "Loading "+htmlFromDoInBackground);
            showText(htmlFromDoInBackground, verseNo);
        }
    }
	private void showText(String text, int verseNo) {
        bibleWebView.show(text, verseNo);
    }

	public List<Note> getNotesList() {
		return notesList;
	}
}
