package net.bible.android.view.activity.page;

import net.bible.android.SharedConstants;
import net.bible.android.activity.R;
import net.bible.android.control.BibleContentManager;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.bookmark.Bookmarks;
import net.bible.android.view.activity.download.Download;
import net.bible.android.view.activity.help.Help;
import net.bible.android.view.activity.navigation.ChooseDocument;
import net.bible.android.view.activity.navigation.History;
import net.bible.android.view.activity.references.NotesActivity;
import net.bible.android.view.activity.settings.SettingsActivity;
import net.bible.android.view.activity.speak.Speak;
import net.bible.android.view.util.DataPipe;
import net.bible.service.common.CommonUtils;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;

/** The main activity screen showing Bible text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class MainBibleActivity extends CustomTitlebarActivityBase {

	private BibleContentManager bibleContentManager;
	
	private BibleView bibleWebView;
	
	// request codes passed to and returned from sub-activities
	private static final int STD_REQUEST_CODE = 1;
	private static final int REFRESH_DISPLAY_ON_FINISH = 2;
	private static final int UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH = 3;

	private String mPrevLocalePref = "";
	
	private static final String TAG = "MainBibleActivity";

	// detect swipe left/right
	private GestureDetector gestureDetector;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main_bible_view);

        setIntegrateWithHistoryManager(true);
        
        // create related objects
        gestureDetector = new GestureDetector( new BibleGestureListener(MainBibleActivity.this) );
        bibleWebView = (BibleView)findViewById(R.id.main_text);
    	bibleContentManager = new BibleContentManager(bibleWebView);

        PassageChangeMediator.getInstance().setMainBibleActivity(MainBibleActivity.this);
        
        restoreState();

    	// initialise title etc
    	onPassageChanged();

    	registerForContextMenu(bibleWebView);
    }

    /** adding android:configChanges to manifest causes this method to be called on flip, etc instead of a new instance and onCreate, which would cause a new observer -> duplicated threads
     */
    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// essentially if the current page is Bible then we need to recalculate verse offsets
		// if not then don't redisplay because it would force the page to the top which would be annoying if you are half way down a gen book page
		if (!ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().isSingleKey()) {
			// force a recalculation of verse offsets
			bibleContentManager.updateText(true);
		}
	}

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = false;
        int requestCode = STD_REQUEST_CODE;
        
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
	        	handlerIntent = ControlFactory.getInstance().getSearchControl().getSearchIntent(CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument());
	        	break;
	        case R.id.settingsButton:
	        	handlerIntent = new Intent(this, SettingsActivity.class);
	        	// force the bible view to be refreshed after returning from settings screen because notes, verses, etc. may be switched on or off
	        	mPrevLocalePref = CommonUtils.getLocalePref();
	        	requestCode = REFRESH_DISPLAY_ON_FINISH;
	        	break;
	        case R.id.historyButton:
	        	handlerIntent = new Intent(this, History.class);
	        	break;
	        case R.id.bookmarksButton:
	        	handlerIntent = new Intent(this, Bookmarks.class);
	        	break;
	        case R.id.speakButton:
	        	handlerIntent = new Intent(this, Speak.class);
	        	break;
	        case R.id.downloadButton:
	        	if (CommonUtils.getSDCardMegsFree()<SharedConstants.REQUIRED_MEGS_FOR_DOWNLOADS) {
	            	Dialogs.getInstance().showErrorMsg(R.string.storage_space_warning);
	        	} else if (!CommonUtils.isInternetAvailable()) {
	            	Dialogs.getInstance().showErrorMsg(R.string.no_internet_connection);
	        	} else {
	        		handlerIntent = new Intent(this, Download.class);
	        		requestCode = UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH;
	        	}
	        	break;
	        case R.id.helpButton:
	        	handlerIntent = new Intent(this, Help.class);
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
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		Log.d(TAG, "Keycode:"+keyCode);
		// common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT
		if (BibleKeyHandler.getInstance().onKeyUp(keyCode, event)) {
			return true;
		} else if ((keyCode == KeyEvent.KEYCODE_SEARCH && CurrentPageManager.getInstance().getCurrentPage().isSearchable())) {
			Intent intent = ControlFactory.getInstance().getSearchControl().getSearchIntent(CurrentPageManager.getInstance().getCurrentPage().getCurrentDocument());
			if (intent!=null) {
				startActivityForResult(intent, STD_REQUEST_CODE);
			}
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}
    
	/** user tapped bottom of screen
	 */
    public void scrollScreenDown() {
    	bibleWebView.pageDown(false);
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) { 
    	Log.d(TAG, "Activity result:"+resultCode);
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if (requestCode == REFRESH_DISPLAY_ON_FINISH) {
    		Log.i(TAG, "Refresh on finish");
    		if (!CommonUtils.getLocalePref().equals(mPrevLocalePref)) {
    			// must restart to change locale
    			PendingIntent intent = PendingIntent.getActivity(this.getBaseContext(), 0, new Intent(getIntent()), getIntent().getFlags());
    			AlarmManager mgr = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
    			mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, intent);
    			System.exit(2);
    			return;
    		} else {
    			preferenceSettingsChanged();
    		}
    	} else if (requestCode == UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH) {
    		updateSuggestedDocuments();
    	}
    }

    @Override
    protected void preferenceSettingsChanged() {
		bibleWebView.applyPreferenceSettings();
		bibleContentManager.updateText(true);
    }
    
    /** called just before starting work to change teh current passage
     */
    public void onPassageChangeStarted() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				setProgressBar(true);
		    	setPageTitleVisible(false);
			}
		});
    }
    /** called by PassageChangeMediator after a new passage has been changed and displayed
     */
    public void onPassageChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				PageControl pageControl = ControlFactory.getInstance().getPageControl();
		    	setDocumentTitle(pageControl.getCurrentDocumentTitle());
		    	setPageTitle(pageControl.getCurrentPageTitle());
		    	setPageTitleVisible(true);
		    	setProgressBar(false);
		    	updateSuggestedDocuments();
			}
		});
    }

    /** called by PassageChangeMediator after a new passage has been changed and displayed
     */
    public void onVerseChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
		    	String passageDesc = ControlFactory.getInstance().getPageControl().getCurrentPageTitle();
		    	setPageTitle(passageDesc);
			}
		});
    }
    
    @Override
	protected void onPause() {
    	//TODO do this at the application level because these prefs are not activity specific
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
		// must return true for menu to be displayed
		return true;
	}

    public void openContextMenu() {
    	openContextMenu(bibleWebView);
    }
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	Log.d(TAG, "oncreatecontextmenu ");
		super.onCreateContextMenu(menu, v, menuInfo);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.document_viewer_context_menu, menu);

		// allow current page type to add, delete or disable menu items
		CurrentPageManager.getInstance().getCurrentPage().updateContextMenu(menu);
	}

    @Override
	public boolean onContextItemSelected(MenuItem item) {
		super.onContextItemSelected(item);
		
		switch (item.getItemId()) {
        case R.id.notes:
        	Intent handlerIntent = new Intent(this, NotesActivity.class);
        	// pump the notes into the viewer (there must be an easier way other than Parcelable)
        	//TODO refactor so the notes are loaded by the Notes viewer using a separate SAX parser 
        	DataPipe.getInstance().pushNotes(bibleContentManager.getNotesList());
        	startActivity(handlerIntent);
        	return true;
        case R.id.add_bookmark:
			ControlFactory.getInstance().getBookmarkControl().bookmarkCurrentVerse();
			return true;
		case R.id.copy:
			ControlFactory.getInstance().getPageControl().copyToClipboard();
			return true;
		case R.id.shareVerse:
			ControlFactory.getInstance().getPageControl().shareVerse();
			return true;
		}

		return false; 
	}

    /** return percentage scrolled down page
     */
    public float getCurrentPosition() {
    	// see http://stackoverflow.com/questions/1086283/getting-document-position-in-a-webview
        int contentHeight = bibleWebView.getContentHeight();
        int scrollY = bibleWebView.getScrollY();
        float ratio = ((float) scrollY / ((float) contentHeight));

        return ratio;
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