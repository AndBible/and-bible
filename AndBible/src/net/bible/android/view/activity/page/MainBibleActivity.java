package net.bible.android.view.activity.page;

import net.bible.android.activity.R;
import net.bible.android.control.BibleContentManager;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.references.NotesActivity;
import net.bible.android.view.util.DataPipe;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

/** The main activity screen showing Bible text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class MainBibleActivity extends CustomTitlebarActivityBase {

	private DocumentViewManager documentViewManager;
	
	private BibleContentManager bibleContentManager;
	
	private static final String TAG = "MainBibleActivity";

	// handle requests from main menu
	private MainMenuCommandHandler mainMenuCommandHandler = new MainMenuCommandHandler(this);
	
	// detect swipe left/right
	private GestureDetector gestureDetector;
	private BibleGestureListener gestureListener;
	
	private long lastContextMenuCreateTimeMillis;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main_bible_view);

        setIntegrateWithHistoryManager(true);
        
        // create related objects
        gestureListener = new BibleGestureListener(MainBibleActivity.this);
        gestureDetector = new GestureDetector( gestureListener );
        
        documentViewManager = new DocumentViewManager(this);
        documentViewManager.buildView();

    	bibleContentManager = new BibleContentManager(documentViewManager);

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

		// essentially if the current page is Bible then we need to recalculate verse offsets
		// if not then don't redisplay because it would force the page to the top which would be annoying if you are half way down a gen book page
		if (!ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().isSingleKey()) {
			// force a recalculation of verse offsets
			bibleContentManager.updateText(true);
		}
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
    	documentViewManager.getDocumentView().pageDown(false);
    }

	/** 
     * on Click handlers
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean isHandled = mainMenuCommandHandler.handleMenuRequest(item.getItemId());
        
     	if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item);
        }
        
     	return isHandled;
    }

    @Override 
    public void onActivityResult(int requestCode, int resultCode, Intent data) { 
    	Log.d(TAG, "Activity result:"+resultCode);
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	boolean handled = mainMenuCommandHandler.restartIfRequiredOnReturn(requestCode);
    	
    	if (handled || mainMenuCommandHandler.isDisplayRefreshRequired(requestCode)) {
    		preferenceSettingsChanged();
    		handled = true;
    	}

    	if (handled || mainMenuCommandHandler.isDocumentChanged(requestCode)) {
    		updateSuggestedDocuments();
    		handled = true;
    	}
    }

    @Override
    protected void preferenceSettingsChanged() {
    	documentViewManager.getDocumentView().applyPreferenceSettings();
		bibleContentManager.updateText(true);
		gestureListener.setSensePageDownTap(!isStrongsShown());
    }
    
    /** allow current page to save any settings or data before being changed
     */
    public void onBeforePageChange() {
    	// save current scroll position so history can return to correct place in document
		float screenPosn = getCurrentPosition();
		CurrentPageManager.getInstance().getCurrentPage().setCurrentYOffsetRatio(screenPosn);
		
		documentViewManager.getDocumentView().save();
    }
    
    /** called just before starting work to change teh current passage
     */
    public void onPassageChangeStarted() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				documentViewManager.buildView();
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
		    	updatePageTitle();
		    	setPageTitleVisible(true);
		    	setProgressBar(false);
		    	updateSuggestedDocuments();
				gestureListener.setSensePageDownTap(!isStrongsShown());
			}
		});
    }

    /** called by PassageChangeMediator after a new passage has been changed and displayed
     */
    public void onVerseChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
		    	updatePageTitle();
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

		// allow webView to stop monitoring tilt 
		documentViewManager.getDocumentView().pausing();
	}
    
    @Override
    protected void onResume() {
    	super.onResume();

    	// allow webView to start monitoring tilt 
		documentViewManager.getDocumentView().resuming();
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

    /** called from gesture listener if the context menu is not displayed automatically
     */
    public void openContextMenu() {
		Log.d(TAG,  "openContextMenu");
		super.openContextMenu(documentViewManager.getDocumentView().asView());
    }

    
    public boolean isContextMenuRecentlyCreated() {
		return (System.currentTimeMillis()-lastContextMenuCreateTimeMillis)<1500;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	Log.d(TAG, "onCreateContextMenu");
    	// keep track of timing here because sometimes a child openContextMenu is called rather than the one in this activity, 
    	// but the activities onCreateContextMenu always seems to be called
		lastContextMenuCreateTimeMillis = System.currentTimeMillis();
		
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
        case R.id.myNoteAddEdit:
        	CurrentPageManager.getInstance().showMyNote();
        	return true;
		case R.id.copy:
			ControlFactory.getInstance().getPageControl().copyToClipboard();
			return true;
		case R.id.shareVerse:
			ControlFactory.getInstance().getPageControl().shareVerse();
			return true;
        case R.id.selectText:
        	Toast.makeText(this, R.string.select_text_help, Toast.LENGTH_LONG).show();
        	documentViewManager.getDocumentView().selectAndCopyText();
        	return true;
		}

		return false; 
	}
    
    /** return percentage scrolled down page
     */
    public float getCurrentPosition() {
    	return documentViewManager.getDocumentView().getCurrentPosition();
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
