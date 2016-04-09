package net.bible.android.view.activity.page;

import net.bible.android.activity.R;
import net.bible.android.control.BibleContentManager;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.passage.PassageChangeStartedEvent;
import net.bible.android.control.event.passage.PassageChangedEvent;
import net.bible.android.control.event.passage.PreBeforeCurrentPageChangeEvent;
import net.bible.android.control.event.touch.ShowContextMenuEvent;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.page.actionbar.BibleActionBarManager;
import net.bible.android.view.activity.page.screen.DocumentViewManager;
import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.view.ActionMode;
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
import de.greenrobot.event.EventBus;

/** The main activity screen showing Bible text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class MainBibleActivity extends CustomTitlebarActivityBase implements VerseActionModeMediator.ActionModeMenuDisplay {

	private DocumentViewManager documentViewManager;
	
	private BibleContentManager bibleContentManager;
	
	private WindowControl windowControl;
	
	private static final String TAG = "MainBibleActivity";

	// handle requests from main menu
	private MenuCommandHandler mainMenuCommandHandler;
	
	private static BibleActionBarManager bibleActionBarManager = new BibleActionBarManager();
	
	// detect swipe left/right
	private GestureDetectorCompat gestureDetector;

	private boolean mWholeAppWasInBackground = false;
	
	private long lastContextMenuCreateTimeMillis;

	private boolean isContextMenuOpenedFromMainMenu = false;

	// swipe fails on older versions of Android (2.2, 2.3, but not 3.0+) if event not passed to parent - don't know why
	// scroll occurs on later versions after double-tap maximize
    private boolean alwaysDispatchTouchEventToSuper = !CommonUtils.isHoneycombPlus();

	public MainBibleActivity() {
		super(bibleActionBarManager, R.menu.main);
	}
	
    /** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "Creating MainBibleActivity");
        super.onCreate(savedInstanceState, true);
        
        setContentView(R.layout.main_bible_view);

		ControlFactory.getInstance().provide(this);

        // create related objects
		BibleGestureListener gestureListener = new BibleGestureListener(MainBibleActivity.this);
        gestureDetector = new GestureDetectorCompat(this, gestureListener );

        windowControl = ControlFactory.getInstance().getWindowControl();
        
        documentViewManager = new DocumentViewManager(this);
        documentViewManager.buildView();

    	bibleContentManager = new BibleContentManager(documentViewManager);

    	mainMenuCommandHandler = new MenuCommandHandler(this);
    	
        // register for passage change and appToBackground events
        EventBus.getDefault().register(this);

        // force the screen to be populated
		PassageChangeMediator.getInstance().forcePageUpdate();
    }

    @Override
	protected void onDestroy() {
		super.onDestroy();
		EventBus.getDefault().unregister(this);
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

	/**
	 *  Need to know when app is returned to foreground to check the screen colours
	 */
    public void onEvent(AppToBackgroundEvent event) {
    	if (event.isMovedToBackground()) {
    		mWholeAppWasInBackground = true;
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
		if (ScreenSettings.isNightModeChanged()) {
			// then update text if colour changed
			documentViewManager.getDocumentView().changeBackgroundColour();
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
		} else if (windowControl.isMultiWindow()) {
			// need to layout multiple windows differently
			windowControl.orientationChange();
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		Log.d(TAG, "Keycode:" + keyCode);
		// common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT
		if (BibleKeyHandler.getInstance().onKeyUp(keyCode, event)) {
			return true;
		} else if ((keyCode == KeyEvent.KEYCODE_SEARCH && ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().isSearchable())) {
			Intent intent = ControlFactory.getInstance().getSearchControl().getSearchIntent(ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().getCurrentDocument());
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
     * on Click handlers.  Go through each handler until one returns true
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mainMenuCommandHandler.handleMenuRequest(item) ||
               super.onOptionsItemSelected(item);
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
    		updateActionBarButtons();
    	}
    }

    @Override
    protected void preferenceSettingsChanged() {
    	documentViewManager.getDocumentView().applyPreferenceSettings();
		PassageChangeMediator.getInstance().forcePageUpdate();
    }
    
    /** allow current page to save any settings or data before being changed
     */
    public void onEvent(PreBeforeCurrentPageChangeEvent event) {
    	CurrentPage currentPage = ControlFactory.getInstance().getCurrentPageControl().getCurrentPage();
    	if (currentPage!=null) {
	    	// save current scroll position so history can return to correct place in document
			float screenPosn = getCurrentPosition();
			currentPage.setCurrentYOffsetRatio(screenPosn);
    	}
    }
    
	public void onEvent(CurrentWindowChangedEvent event) {
		MainBibleActivity.this.updateActionBarButtons();
		
    	// onPrepareOptionsMenu only called once on Android 2.2, 2.3, 3.0: http://stackoverflow.com/questions/29925104/onprepareoptionsmenu-only-called-once-on-android-2-3
    	// so forcefully invalidate it on old versions
    	if (!CommonUtils.isIceCreamSandwichPlus()) {
    		supportInvalidateOptionsMenu();
    	}
	}

    /** called just before starting work to change the current passage
     */
    public void onEventMainThread(PassageChangeStartedEvent event) {
		documentViewManager.buildView();
		setProgressBar(true);
    }
    
    /** called by PassageChangeMediator after a new passage has been changed and displayed
     */
    public void onEventMainThread(PassageChangedEvent event) {
    	setProgressBar(false);
    	updateActionBarButtons();
    }

    @Override
    protected void onResume() {
    	super.onResume();

    	// allow webView to start monitoring tilt by setting focus which causes tilt-scroll to resume
		documentViewManager.getDocumentView().asView().requestFocus();
    }

    /**
     * Some menu items must be hidden for certain document types
     */
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
    	// construct the options menu
		super.onPrepareOptionsMenu(menu);
		
		// disable some options depending on document type
		ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().updateOptionsMenu(menu);
		
		// if there is no backup file then disable the restore menu item
		ControlFactory.getInstance().getBackupControl().updateOptionsMenu(menu);

		// set Synchronised checkbox correctly
		ControlFactory.getInstance().getWindowControl().updateOptionsMenu(menu);

		// must return true for menu to be displayed
		return true;
	}

	/**
	 * Event raised by javascript as a result of longtap
	 */
	@Override
	public void showVerseActionModeMenu(final ActionMode.Callback actionModeCallbackHandler) {
		Log.d(TAG, "showVerseActionModeMenu");

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				startSupportActionMode(actionModeCallbackHandler);
			}
		});
	}

//	public void onEventMainThread(ShowContextMenuEvent event) {
//		Log.d(TAG, "showActionModeMenu");
//		//TODO newVerseSelect
//		startSupportActionMode(new ActionMode.Callback() {
//			@Override
//			public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
//				// Inflate our menu from a resource file
//				actionMode.getMenuInflater().inflate(R.menu.document_viewer_context_menu, menu);
//
//				// Return true so that the action mode is shown
//				return true;
//			}
//
//			@Override
//			public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
//				// As we do not need to modify the menu before displayed, we return false.
//				return false;
//			}
//
//			@Override
//			public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
//				// Similar to menu handling in Activity.onOptionsItemSelected()
//				mainMenuCommandHandler.handleMenuRequest(menuItem);
//
//				return false;
//			}
//
//			@Override
//			public void onDestroyActionMode(ActionMode actionMode) {
//				// Allows you to be notified when the action mode is dismissed
//			}
//		});
//
		//TODO newVerseSelect
//		super.openContextMenu(documentViewManager.getDocumentView().asView());
//    }

	//TODO newVerseSelect
//    /** called from Main menu
//     */
//    public void openContextMenuFromMainMenu() {
//		Log.d(TAG,  "openContextMenuFromMainMenu");
//		// this is reset after context menu is displayed
//		isContextMenuOpenedFromMainMenu = true;
//
//		super.openContextMenu(documentViewManager.getDocumentView().asView());
//    }


//    /** Attempt to prevent two context menus being created one on top of the other
//     *
//     *  The MyNote triggers it's own context menu which causes 2 to be displayed
//     *  I have also seen 2 displayed in normal view
//     *  Avoid 2 by preventing display twice within 1.5 seconds
//     */
//    private boolean isLastContextMenuRecentlyCreated() {
//		boolean isRecent = (System.currentTimeMillis()-lastContextMenuCreateTimeMillis)<1500;
//		lastContextMenuCreateTimeMillis = System.currentTimeMillis();
//
//		return isRecent;
//	}

//	@Override
//	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
//		super.onCreateContextMenu(menu, v, menuInfo);
//		Thread.dumpStack();
//
//		if (!TouchOwner.getInstance().isTouchOwned()) {
//			if (mainMenuCommandHandler.isIgnoreLongPress()) {
//				Log.d(TAG, "Ignoring long press to allow it to pass thro to WebView handler");
//				return;
//			}
//	    	Log.d(TAG, "onCreateContextMenu");
//
//	    	// keep track of timing here because sometimes a child openContextMenu is called rather than the one in this activity,
//	    	// but the activities onCreateContextMenu always seems to be called
//	    	if (isLastContextMenuRecentlyCreated()) {
//	    		return;
//	    	}
//
//			// for some reason going twice to MyNote via popup crashes on Android 2.3 but only if called via long-press
//	    	if (!CommonUtils.isIceCreamSandwichPlus() && !isContextMenuOpenedFromMainMenu) {
//	    		return;
//	    	}
//
//	    	MenuInflater inflater = getMenuInflater();
//			inflater.inflate(R.menu.document_viewer_context_menu, menu);
//
//			// allow current page type to add, delete or disable menu items
//			ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().updateContextMenu(menu);
//		}
//
//		// reset this flag required to disable crashing action in Android 2.3
//		isContextMenuOpenedFromMainMenu = false;
//	}

//    @Override
//	public boolean onContextItemSelected(MenuItem item) {
//        boolean isHandled = mainMenuCommandHandler.handleMenuRequest(item);
//
//     	if (!isHandled) {
//            isHandled = super.onContextItemSelected(item);
//        }
//
//     	return isHandled;
//	}

    /** return percentage scrolled down page
     */
    public float getCurrentPosition() {
    	return documentViewManager.getDocumentView().getCurrentPosition();
    }
    
    /** user swiped right */
    public void next() {
    	if (getDocumentViewManager().getDocumentView().isPageNextOkay()) {
    		ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().next();
    	}		
    }
    
    /** user swiped left */
    public void previous() {
    	if (getDocumentViewManager().getDocumentView().isPagePreviousOkay()) {
    		ControlFactory.getInstance().getCurrentPageControl().getCurrentPage().previous();
    	}
    }

	// handle swipe left and right
    // http://android-journey.blogspot.com/2010_01_01_archive.html
    //http://android-journey.blogspot.com/2010/01/android-gestures.html
    // above dropped in favour of simpler method below
    //http://developer.motorola.com/docstools/library/The_Widget_Pack_Part_3_Swipe/
	@Override
	public boolean dispatchTouchEvent(MotionEvent motionEvent) {
		// should only call super if below returns false
		if (this.gestureDetector.onTouchEvent(motionEvent) && !alwaysDispatchTouchEventToSuper) {
			return true;
		} else {
			return super.dispatchTouchEvent(motionEvent);
		}
	}

	protected DocumentViewManager getDocumentViewManager() {
		return documentViewManager;
	}

	protected BibleContentManager getBibleContentManager() {
		return bibleContentManager;
	}
}