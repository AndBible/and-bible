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
import android.webkit.WebView;

public class BibleContentManager {
	
	// bible and verse displayed on the screen
	private Book displayedBible;
	private Key displayedVerse;
	private List<Note> notesList;

	private WebView bibleWebView;
	private Context context;
	
	private static final String TAG = "BibleView";
	
	private String NO_CONTENT = "No content for selected verse";
	
	public BibleContentManager(WebView bibleWebView, Context context) {
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
    	CurrentPassage currentPassage = CurrentPassage.getInstance();
		Book bible = currentPassage.getCurrentDocument();
		Key verse = currentPassage.getKey();

		// scrolling right in a commentary can sometimes cause duplicate updates and I don't know why - catch them for now
		if (!bible.equals(displayedBible) || !verse.equals(displayedVerse)) {
			new UpdateTextTask().execute(currentPassage);
		}
    		
    }

    private class UpdateTextTask extends AsyncTask<CurrentPassage, Integer, String> {
    	
		@Override
        protected String doInBackground(CurrentPassage... currentPassage) {
        	String text = "Error";
        	try {
	    		Book bible = currentPassage[0].getCurrentDocument();
	    		// if bible show whole chapter
	    		Key verse = currentPassage[0].getKey();
	
	    		SharedPreferences preferences = context.getSharedPreferences("net.bible.android.activity_preferences", 0);
	    		// wait until after current verse has been fetched because the following may change the current verse 
//    			scrollTo(0, 0);
	    		
	            Log.d(TAG, "Loading "+verse);
	    		//setText("Loading "+verse.toString());
	            SwordApi swordApi = SwordApi.getInstance();
	            swordApi.setPreferences(preferences);
	            
	            notesList = new ArrayList<Note>();
	            
	            FormattedDocument formattedDocument = swordApi.readHtmlText(bible, verse, 200);
	            text = formattedDocument.getHtmlPassage();
	            notesList = formattedDocument.getNotesList();
	            
	            if (StringUtils.isEmpty(text)) {
	            	text = NO_CONTENT;
	            }
	
	            displayedBible = bible;
	            displayedVerse = verse;
		            
        	} catch (Exception e) {
        		Log.e(TAG, "Error getting bible text", e);
        		text = "Error getting bible text: "+e.getMessage();
        	}
        	return text;
        }

        protected void onPostExecute(String htmlFromDoInBackground) {
            Log.d(TAG, "Loading "+htmlFromDoInBackground);
            showText(htmlFromDoInBackground);
        }
    }
	private void showText(String text) {
        bibleWebView.loadDataWithBaseURL("http://baseUrl", text, "text/html", "UTF-8", "http://historyUrl");
    }

	public List<Note> getNotesList() {
		return notesList;
	}
	
}
