package net.bible.android.activity;

import net.bible.android.activity.base.ActivityBase;
import net.bible.android.activity.base.Dialogs;
import net.bible.android.currentpagecontrol.BibleContentManager;
import net.bible.android.currentpagecontrol.CurrentBiblePage;
import net.bible.android.currentpagecontrol.CurrentPageManager;
import net.bible.android.currentpagecontrol.PassageChangeMediator;
import net.bible.android.device.TextToSpeechController;
import net.bible.android.util.CommonUtil;
import net.bible.android.util.DataPipe;
import net.bible.android.view.BibleSwipeListener;
import net.bible.android.view.BibleView;
import net.bible.service.history.HistoryManager;
import net.bible.service.sword.SwordApi;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.index.IndexStatus;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
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
        gestureDetector = new GestureDetector( new BibleSwipeListener(MainBibleActivity.this) );
        bibleWebView = (BibleView)findViewById(R.id.main_text);
    	bibleContentManager = new BibleContentManager(bibleWebView, MainBibleActivity.this);

        PassageChangeMediator.getInstance().setMainBibleActivity(MainBibleActivity.this);
        
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
	        	handlerIntent = new Intent(this, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
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
	        	if (!CommonUtil.isInternetAvailable()) {
	            	showErrorMsg(getString(R.string.no_internet_connection));
	        	} else {
	        		handlerIntent = new Intent(this, Download.class);
	        	}
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
	        switch (item.getItemId()) {
	        case R.id.speakButton:
	        	// speak current chapter or stop speech if already speaking
	        	TextToSpeechController tts = TextToSpeechController.getInstance();
	        	if (tts.isSpeaking()) {
	        		Log.d(TAG, "TTS is speaking so stop it");
	        		tts.stop();
	        	} else {
	        		Log.d(TAG, "Tell TTS to say current chapter");
		        	tts.speak(this, CurrentPageManager.getInstance().getCurrentPage());
	        	}
	        	isHandled = true;
	        	break;
	        }
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
    	//xxxtodo is search relevant to all doc types? - some don't have verse keys so need to check it works
    	Book book = CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument();
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
    
    /** called just before starting work to change teh current passage
     */
    public void onPassageChangeStarted() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setProgressBarIndeterminateVisibility(true);
		    	setTitle("");
			}
		});
    }
    /** called by PassageChangeMediator after a new passage has been changed and displayed
     */
    public void onPassageChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
		    	String passageDesc = CurrentPageManager.getInstance().getCurrentPage().getKeyDescription();
		    	setTitle(passageDesc);
				setProgressBarIndeterminateVisibility(false);
			}
		});
    }
  
    
    @Override
	protected void onPause() {
    	Log.i(TAG, "Saving instance state");
		super.onPause();
    	SharedPreferences settings = getSharedPreferences(TAG, 0);
		CurrentPageManager.getInstance().saveState(settings);
	}

    private void restoreState() {
    	try {
        	Log.i(TAG, "Restore instance state");
        	SharedPreferences settings = getSharedPreferences(TAG, 0);
    		CurrentPageManager.getInstance().restoreState(settings);
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
    
 
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		CurrentPageManager.getInstance().getCurrentPage().updateOptionsMenu(menu);
		// must return for menu to be displayed
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
	
	private void showSplashScreen() {
		Intent intent = new Intent(this, SplashScreen.class);
		startActivity(intent);
	}
 }