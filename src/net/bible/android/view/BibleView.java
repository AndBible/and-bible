package net.bible.android.view;

import java.util.Observable;
import java.util.Observer;

import net.bible.android.CurrentPassage;
import net.bible.service.sword.SwordApi;

import org.apache.commons.lang.StringUtils;
import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;

public class BibleView extends BibleGestureNavigation {
	
	// bible and verse displayed on the screen
	private Book displayedBible;
	private Key displayedVerse;
	
	private static final String TAG = "BibleView";
	
	private String NO_CONTENT = "No content for selected verse";
	
	public BibleView(Context context) {
		super(context);
		initialise();		
	}
	public BibleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialise();		
	}
	public BibleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialise();		
	}
	
	private void initialise() {
		CurrentPassage.getInstance().addObserver(new Observer() {
			@Override
			public void update(Observable observable, Object data) {
				updateText();
			}
		});
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
	
	    		// wait until after current verse has been fetched because the following may change the current verse 
//    			scrollTo(0, 0);
	    		
	            Log.d(TAG, "Loading "+verse);
	    		//setText("Loading "+verse.toString());
	            text = SwordApi.getInstance().readHtmlText(bible, verse, 200);
	
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
        loadData(text, "text/html", "UTF-8");
    }
}
