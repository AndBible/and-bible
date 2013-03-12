package net.bible.android.view.activity.page;

import java.util.Map;

import net.bible.android.activity.R;
import net.bible.android.control.BibleContentManager;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundListener;
import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.CurrentActivityHolder;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.page.screen.DocumentViewManager;
import net.bible.android.view.util.TouchOwner;
import net.bible.service.device.ScreenSettings;

import android.content.Intent;
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

/** The main activity screen showing Bible text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class MainBibleActivity extends CustomTitlebarActivityBase {

	private DocumentViewManager documentViewManager;
	
	private BibleContentManager bibleContentManager;
	
	private SplitScreenControl splitScreenControl;
	
	private static final String TAG = "MainBibleActivity";

	// handle requests from main menu
	private MenuCommandHandler mainMenuCommandHandler;
	
	// detect swipe left/right
	private GestureDetector gestureDetector;
	private BibleGestureListener gestureListener;

	private boolean mWholeAppWasInBackground = false;
	
	private long lastContextMenuCreateTimeMillis;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "Creating MainBibleActivity");

        super.onCreate(savedInstanceState, true);
        
        setContentView(R.layout.main_bible_view);

        // create related objects
        gestureListener = new BibleGestureListener(MainBibleActivity.this);
        gestureDetector = new GestureDetector( gestureListener );

        splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
        
        documentViewManager = new DocumentViewManager(this);
        documentViewManager.buildView();

    	bibleContentManager = new BibleContentManager(documentViewManager);

    	mainMenuCommandHandler = new MenuCommandHandler(this);
    	
        PassageChangeMediator.getInstance().setMainBibleActivity(MainBibleActivity.this);

        // force the screen to be populated
		PassageChangeMediator.getInstance().forcePageUpdate();

    	// need to know when app is returned to foreground to check the screen colours
    	CurrentActivityHolder.getInstance().addAppToBackgroundListener(new AppToBackgroundListener() {
			@Override
			public void applicationNowInBackground(AppToBackgroundEvent e) {
				mWholeAppWasInBackground = true;
			}

			@Override
			public void applicationReturnedFromBackground(AppToBackgroundEvent e) {
				//NOOP
			}
		});
    	
		ControlFactory.getInstance().getSplitScreenControl().addSplitScreenEventListener(new SplitScreenEventListener() {
			
			@Override
			public void currentSplitScreenChanged(Screen activeScreen) {
				MainBibleActivity.this.updateToolbarButtonText();				
			}
			
			@Override
			public void splitScreenSizeChange(boolean isMoveFinished, Map<Screen, Integer> screenVerseMap) {
				// Noop
			}
			@Override
			public void updateSecondaryScreen(Screen updateScreen, String html, int verseNo) {
				// NOOP - handle in BibleWebView				
			}
			@Override
			public void scrollSecondaryScreen(Screen screen, int verseNo) {
				// NOOP - handle in BibleWebView
			}
			@Override
			public void numberOfScreensChanged(Map<Screen, Integer> screenVerseMap) {
				// Noop
			}
		});
    }

    /** called if the app is re-entered after returning from another app.
     * Trigger redisplay in case mobile has gone from light to dark or vice-versa
     */
    @Override
    protected void onRestart() {
    	super.onRestart();

    	if (mWholeAppWasInBackground) {
			mWholeAppWasInBackground = false;
			refreshIfNightModeChange();
    	}
    }

    @Override
	protected void onScreenTurnedOff() {
		super.onScreenTurnedOff();
		documentViewManager.getDocumentView().onScreenTurnedOff();
	}

    @Override
	protected void onScreenTurnedOn() {
		super.onScreenTurnedOn();
		refreshIfNightModeChange();
		documentViewManager.getDocumentView().onScreenTurnedOn();
	}

    /** if using auto night mode then may need to refresh
     */
    private void refreshIfNightModeChange() {
    	// colour may need to change which affects View colour and html
		// first refresh the night mode setting using light meter if appropriate
		ScreenSettings.updateNightModeValue();
		// then update text if colour changes
    	if (documentViewManager.getDocumentView().changeBackgroundColour()) {
			PassageChangeMediator.getInstance().forcePageUpdate();
    	}

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
			PassageChangeMediator.getInstance().forcePageUpdate();
		} else if (splitScreenControl.isSplit()) {
			// need to layout split screens differently
			splitScreenControl.orientationChange();
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
    	
    	if (mainMenuCommandHandler.restartIfRequiredOnReturn(requestCode)) {
    		// restart done in above
    	} else if (mainMenuCommandHandler.isDisplayRefreshRequired(requestCode)) {
    		preferenceSettingsChanged();
    	} else if (mainMenuCommandHandler.isDocumentChanged(requestCode)) {
    		updateToolbarButtonText();
    	}
    }

    @Override
    protected void preferenceSettingsChanged() {
    	documentViewManager.getDocumentView().applyPreferenceSettings();
		PassageChangeMediator.getInstance().forcePageUpdate();
    }
    
    /** allow current page to save any settings or data before being changed
     */
    public void onBeforePageChange() {
    	// save current scroll position so history can return to correct place in document
		float screenPosn = getCurrentPosition();
		CurrentPageManager.getInstance().getCurrentPage().setCurrentYOffsetRatio(screenPosn);
		
		documentViewManager.getDocumentView().save();
    }
    
    /** called just before starting work to change the current passage
     */
    public void onPassageChangeStarted() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
				documentViewManager.buildView();
				
				setProgressBar(true);
			}
		});
    }
    
    /** called by PassageChangeMediator after a new passage has been changed and displayed
     */
    public void onPassageChanged() {
    	runOnUiThread(new Runnable() {
			@Override
			public void run() {
		    	setProgressBar(false);
		    	updateToolbarButtonText();
		    	// don't sense taps at bottom of screen if Strongs numbers link might be there or Map zoom control might be there
				gestureListener.setSensePageDownTap(!isStrongsShown() && !CurrentPageManager.getInstance().isMapShown());
			}
		});
    }

    @Override
    protected void onResume() {
    	super.onResume();

    	// allow webView to start monitoring tilt by setting focus which causes tilt-scroll to resume 
		documentViewManager.getDocumentView().asView().requestFocus();
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
		
		// if there is no backup file then disable the restore menu item
		ControlFactory.getInstance().getBackupControl().updateOptionsMenu(menu);
		
		// must return true for menu to be displayed
		return true;
	}

    /** called from gesture listener if the context menu is not displayed automatically
     */
    public void openContextMenu() {
		Log.d(TAG,  "openContextMenu");
		super.openContextMenu(documentViewManager.getDocumentView().asView());
    }


    /** Attempt to prevent two context menus being created one on top of the other
     * 
     *  The MyNote triggers it's own context menu which causes 2 to be displayed
     *  I have also seen 2 displayed in normal view
     *  Avoid 2 by preventing display twice within 1.5 seconds
     */
    private boolean isLastContextMenuRecentlyCreated() {
		boolean isRecent = (System.currentTimeMillis()-lastContextMenuCreateTimeMillis)<1500;
		lastContextMenuCreateTimeMillis = System.currentTimeMillis();

		return isRecent;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		if (!TouchOwner.getInstance().isTouchOwned()) {
			if (mainMenuCommandHandler.isIgnoreLongPress()) {
				Log.d(TAG, "Ignoring long press to allow it to pass thro to WebView handler");
				return;
			}
	    	Log.d(TAG, "onCreateContextMenu");
	    	
	    	// keep track of timing here because sometimes a child openContextMenu is called rather than the one in this activity, 
	    	// but the activities onCreateContextMenu always seems to be called
	    	if (isLastContextMenuRecentlyCreated()) {
	    		return;
	    	}
	
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.document_viewer_context_menu, menu);
	
			// allow current page type to add, delete or disable menu items
			CurrentPageManager.getInstance().getCurrentPage().updateContextMenu(menu);
		}
	}

    @Override
	public boolean onContextItemSelected(MenuItem item) {
        boolean isHandled = mainMenuCommandHandler.handleMenuRequest(item.getItemId());
        
     	if (!isHandled) {
            isHandled = super.onContextItemSelected(item);
        }
        
     	return isHandled;
	}
    
    /** return percentage scrolled down page
     */
    public float getCurrentPosition() {
    	return documentViewManager.getDocumentView().getCurrentPosition();
    }
    
    /** user swiped right */
    public void next() {
    	if (getDocumentViewManager().getDocumentView().isPageNextOkay()) {
    		CurrentPageManager.getInstance().getCurrentPage().next();
    	}		
    }
    
    /** user swiped left */
    public void previous() {
    	if (getDocumentViewManager().getDocumentView().isPagePreviousOkay()) {
    		CurrentPageManager.getInstance().getCurrentPage().previous();
    	}
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

	protected DocumentViewManager getDocumentViewManager() {
		return documentViewManager;
	}

	protected BibleContentManager getBibleContentManager() {
		return bibleContentManager;
	}
 }
