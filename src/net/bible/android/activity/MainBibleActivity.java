package net.bible.android.activity;

import net.bible.android.CurrentPassage;
import net.bible.android.util.ActivityBase;
import net.bible.android.util.DataPipe;
import net.bible.android.view.BibleContentManager;
import net.bible.android.view.BibleSwipeListener;
import net.bible.android.view.BibleView;
import net.bible.android.view.PassageChangeMediator;
import net.bible.service.history.HistoryManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.index.IndexStatus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;

/** The main activity screen showing Bible text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class MainBibleActivity extends ActivityBase {

	private BibleContentManager bibleContentManager;
	
	private BibleView bibleWebView;
	
	private static final int REFRESH_DISPLAY_ON_FINISH = 2;

	private static final String TAG = "MainBibleActivity";

	// detect swipe left/right
	private GestureDetector gestureDetector;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main_bible_view);

        // create related objects
        gestureDetector = new GestureDetector( new BibleSwipeListener(this) );
        bibleWebView = (BibleView)findViewById(R.id.main_text);
    	bibleContentManager = new BibleContentManager(bibleWebView, this);

        PassageChangeMediator.getInstance().setMainBibleActivity(this);
        
    	HistoryManager.getInstance().initialise();
    	
        restoreState();

    	// initialise title etc
    	onPassageChanged();
    }

    /** adding android:configChanges to manifest causes this method to be called on flip, etc instead of a new instance and onCreate, which would cause a new observer -> duplicated threads
     */
    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = false;
        int requestCode = 1;
        
    	// Activities
    	{
    		Intent handlerIntent = null;
	        // Handle item selection
	        switch (item.getItemId()) {
	        case R.id.chooseBookButton:
	        	handlerIntent = new Intent(this, ChooseDocument.class);
	        	break;
	        case R.id.selectPassageButton:
	        	handlerIntent = new Intent(this, ChoosePassageBook.class);
	        	break;
	        case R.id.searchButton:
	        	handlerIntent = getSearchIntent();
	        	break;
	        case R.id.settingsButton:
	        	handlerIntent = new Intent(this, SettingsActivity.class);
	        	// force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
	        	requestCode = REFRESH_DISPLAY_ON_FINISH;
	        	break;
	        case R.id.historyButton:
	        	handlerIntent = new Intent(this, History.class);
	        	break;
	        case R.id.notesButton:
	        	handlerIntent = new Intent(this, NotesActivity.class);
	        	// pump the notes into the viewer (there must be an easier way other than Parcelable)
	        	//todo refactor so the notes are loaded by the Notes viewer using a separate SAX parser 
	        	DataPipe.getInstance().pushNotes(bibleContentManager.getNotesList());
	        	break;
	        case R.id.downloadButton:
	        	handlerIntent = new Intent(this, Download.class);
	        	break;
	        }
	
	        if (handlerIntent!=null) {
	        	startActivityForResult(handlerIntent, requestCode);
	        	isHandled = true;
	        } 
    	}

    	//Dialogs
    	{
//    		if (!isHandled) {
//                switch (item.getItemId()) {
//                case R.id.notesButton:
//                	showDialog(DIALOG_NOTES);
//                	isHandled = true;
//                	break;
//                }
//    		}
    	}

    	if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
        return isHandled;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && HistoryManager.getInstance().canGoBack()) {
       		HistoryManager.getInstance().goBack();
       		return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) { 
    	Log.d(TAG, "Activity result:"+resultCode);
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if (requestCode == REFRESH_DISPLAY_ON_FINISH) {
    		Log.i(TAG, "Refresh on finish");
    		bibleContentManager.updateText(true);
    	}
    }

    private Intent getSearchIntent() {
    	Book book = CurrentPassage.getInstance().getCurrentDocument();
    	IndexStatus indexStatus = book.getIndexStatus();
    	Log.d(TAG, "Index status:"+indexStatus);
    	if (indexStatus.equals(IndexStatus.DONE)) {
    		Log.d(TAG, "Index status is DONE");
    	    return new Intent(this, Search.class);
    	} else {
    		Log.d(TAG, "Index status is NOT DONE");
    	    return new Intent(this, SearchIndex.class);
    	}
    }
    
    public void onPassageChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
		    	String passageDesc = CurrentPassage.getInstance().toString();
		    	setTitle(passageDesc);
			}
		});
    }
  
    
    @Override
	protected void onPause() {
    	Log.i(TAG, "Saving instance state");
		super.onPause();
    	SharedPreferences settings = getSharedPreferences(TAG, 0);
		CurrentPassage.getInstance().saveState(settings);
	}

    private void restoreState() {
    	try {
        	Log.i(TAG, "Restore instance state");
        	SharedPreferences settings = getSharedPreferences(TAG, 0);
    		CurrentPassage.getInstance().restoreState(settings);
    	} catch (Exception e) {
    		Log.e(TAG, "Restore error", e);
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    // handle swipe left and right
    // http://android-journey.blogspot.com/2010_01_01_archive.html
    //http://android-journey.blogspot.com/2010/01/android-gestures.html
    // above dropped in favour of simpler method below
    //http://developer.motorola.com/docstools/library/The_Widget_Pack_Part_3_Swipe/
	@Override
	public boolean dispatchTouchEvent(MotionEvent motionEvent) {
		this.gestureDetector.onTouchEvent(motionEvent);
		return super.dispatchTouchEvent(motionEvent);
	}
	
 }