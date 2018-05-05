package net.bible.android.view.activity.page;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.view.ActionMode;
import android.util.Log;

import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.BibleContentManager;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.passage.SynchronizeWindowsEvent;
import net.bible.android.control.event.passage.PassageChangeStartedEvent;
import net.bible.android.control.event.passage.PassageChangedEvent;
import net.bible.android.control.event.passage.PreBeforeCurrentPageChangeEvent;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.DaggerMainBibleActivityComponent;
import net.bible.android.view.activity.MainBibleActivityModule;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.page.actionbar.BibleActionBarManager;
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator;
import net.bible.android.view.activity.page.screen.DocumentViewManager;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import javax.inject.Inject;

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

	// handle requests from main menu
	private MenuCommandHandler mainMenuCommandHandler;

	private BibleActionBarManager bibleActionBarManager;

	// detect swipe left/right
	private GestureDetectorCompat gestureDetector;

	private BibleKeyHandler bibleKeyHandler;

	private boolean mWholeAppWasInBackground = false;

	// swipe fails on older versions of Android (2.2, 2.3, but not 3.0+) if event not passed to parent - don't know why
	// scroll occurs on later versions after double-tap maximize
	private boolean alwaysDispatchTouchEventToSuper = !CommonUtils.isHoneycombPlus();

	private BackupControl backupControl;

	private SearchControl searchControl;

	private static final String TAG = "MainBibleActivity";

	public MainBibleActivity() {
		super(R.menu.main);
	}

	/**
	 * Called when the activity is first created.
	 */
	@SuppressLint("MissingSuperCall")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "Creating MainBibleActivity");
		super.onCreate(savedInstanceState, true);

		setContentView(R.layout.main_bible_view);

		DaggerMainBibleActivityComponent.builder()
				.applicationComponent(BibleApplication.getApplication().getApplicationComponent())
				.mainBibleActivityModule(new MainBibleActivityModule(this))
				.build()
				.inject(this);

		super.setActionBarManager(bibleActionBarManager);

		// create related objects
		BibleGestureListener gestureListener = new BibleGestureListener(MainBibleActivity.this);
		gestureDetector = new GestureDetectorCompat(this, gestureListener);

		documentViewManager.buildView();

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

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if(menuInfo != null) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.link_context_menu, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		BibleView.BibleViewContextMenuInfo info = (BibleView.BibleViewContextMenuInfo) item.getMenuInfo();
		if(info != null) {
			info.activate(item.getItemId());
			return true;
		}
		return false;
	}

	/**
	 * called if the app is re-entered after returning from another app.
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
	 * Need to know when app is returned to foreground to check the screen colours
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

	/**
	 * if using auto night mode then may need to refresh
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

	/**
	 * adding android:configChanges to manifest causes this method to be called on flip, etc instead of a new instance and onCreate, which would cause a new observer -> duplicated threads
	 */
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// essentially if the current page is Bible then we need to recalculate verse offsets
		// if not then don't redisplay because it would force the page to the top which would be annoying if you are half way down a gen book page
		if (!windowControl.getActiveWindowPageManager().getCurrentPage().isSingleKey()) {
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
		if (bibleKeyHandler.onKeyUp(keyCode, event)) {
			return true;
		} else if ((keyCode == KeyEvent.KEYCODE_SEARCH && windowControl.getActiveWindowPageManager().getCurrentPage().isSearchable())) {
			Intent intent = searchControl.getSearchIntent(windowControl.getActiveWindowPageManager().getCurrentPage().getCurrentDocument());
			if (intent != null) {
				startActivityForResult(intent, STD_REQUEST_CODE);
			}
			return true;
		}

		return super.onKeyUp(keyCode, event);
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
		Log.d(TAG, "Activity result:" + resultCode);
		super.onActivityResult(requestCode, resultCode, data);

		if (mainMenuCommandHandler.restartIfRequiredOnReturn(requestCode)) {
			// restart done in above
		} else if (mainMenuCommandHandler.isDisplayRefreshRequired(requestCode)) {
			preferenceSettingsChanged();
			EventBus.getDefault().post(new SynchronizeWindowsEvent());
		} else if (mainMenuCommandHandler.isDocumentChanged(requestCode)) {
			updateActionBarButtons();
		}
	}

	@Override
	protected void preferenceSettingsChanged() {
		documentViewManager.getDocumentView().applyPreferenceSettings();
		PassageChangeMediator.getInstance().forcePageUpdate();
	}

	/**
	 * allow current page to save any settings or data before being changed
	 */
	public void onEvent(PreBeforeCurrentPageChangeEvent event) {
		CurrentPage currentPage = windowControl.getActiveWindowPageManager().getCurrentPage();
		if (currentPage != null) {
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

	/**
	 * called just before starting work to change the current passage
	 */
	public void onEventMainThread(PassageChangeStartedEvent event) {
		documentViewManager.buildView();
		setProgressBar(true);
	}

	/**
	 * called by PassageChangeMediator after a new passage has been changed and displayed
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
		windowControl.getActiveWindowPageManager().getCurrentPage().updateOptionsMenu(menu);

		// if there is no backup file then disable the restore menu item
		backupControl.updateOptionsMenu(menu);

		// set Synchronised checkbox correctly
		windowControl.updateOptionsMenu(menu);

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
				ActionMode actionMode = startSupportActionMode(actionModeCallbackHandler);

				// Fix for onPrepareActionMode not being called: https://code.google.com/p/android/issues/detail?id=159527
				if (actionMode != null) {
					actionMode.invalidate();
				}
			}
		});
	}

	public void clearVerseActionMode(final ActionMode actionMode) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				actionMode.finish();
			}
		});
	}

	/**
	 * return percentage scrolled down page
	 */
	public float getCurrentPosition() {
		return documentViewManager.getDocumentView().getCurrentPosition();
	}

	/**
	 * user swiped right
	 */
	public void next() {
		if (getDocumentViewManager().getDocumentView().isPageNextOkay()) {
			windowControl.getActiveWindowPageManager().getCurrentPage().next();
		}
	}

	/**
	 * user swiped left
	 */
	public void previous() {
		if (getDocumentViewManager().getDocumentView().isPagePreviousOkay()) {
			windowControl.getActiveWindowPageManager().getCurrentPage().previous();
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

	@Inject
	void setBackupControl(BackupControl backupControl) {
		this.backupControl = backupControl;
	}

	@Inject
	void setDocumentViewManager(DocumentViewManager documentViewManager) {
		this.documentViewManager = documentViewManager;
	}

	@Inject
	void setMainMenuCommandHandler(MenuCommandHandler mainMenuCommandHandler) {
		this.mainMenuCommandHandler = mainMenuCommandHandler;
	}

	@Inject
	void setBibleActionBarManager(BibleActionBarManager bibleActionBarManager) {
		this.bibleActionBarManager = bibleActionBarManager;
	}

	@Inject
	void setSearchControl(SearchControl searchControl) {
		this.searchControl = searchControl;
	}

	@Inject
	void setWindowControl(WindowControl windowControl) {
		this.windowControl = windowControl;
	}

	@Inject
	void setBibleContentManager(BibleContentManager bibleContentManager) {
		this.bibleContentManager = bibleContentManager;
	}

	@Inject
	void setBibleKeyHandler(BibleKeyHandler bibleKeyHandler) {
		this.bibleKeyHandler = bibleKeyHandler;
	}
}

