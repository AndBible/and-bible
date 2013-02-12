package net.bible.android.control.page;

import net.bible.android.activity.R;
import net.bible.service.format.HtmlMessageFormatter;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.passage.Key;

import android.os.AsyncTask;
import android.util.Log;

abstract public class UpdateTextTask extends AsyncTask<CurrentPage, Integer, String> {
	
	private int verseNo;
	private float yScreenOffsetRatio;
	
	private static final String TAG = "UpdateTextTask";
	
    /** callbacks from base class when result is ready */
	abstract protected void showText(String text, int verseNo, float yOffsetRatio);
	
	@Override
	protected void onPreExecute() {
		//NOOP
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
            
            text = currentPage.getCurrentPageContent();
            
    	} catch (Exception e) {
    		Log.e(TAG, "Error getting bible text", e);
    		//TODO use resource
    		text = HtmlMessageFormatter.format("Error getting bible text: "+e.getMessage());
    	} catch (OutOfMemoryError oom) {
    		Log.e(TAG, "Out of memory error", oom);
    		System.gc();
    		text = HtmlMessageFormatter.format(R.string.error_page_too_large);
    	}
    	return text;
    }

    protected void onPostExecute(String htmlFromDoInBackground) {
        Log.d(TAG, "Loading html:"+htmlFromDoInBackground);
        showText(htmlFromDoInBackground, verseNo, yScreenOffsetRatio);
    }
}
