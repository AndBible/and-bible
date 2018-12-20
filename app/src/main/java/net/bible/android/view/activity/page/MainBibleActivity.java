/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.view.activity.page;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;
import androidx.appcompat.view.ActionMode;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.BibleContentManager;
import net.bible.android.control.PassageChangeMediator;
import net.bible.android.control.backup.BackupControl;
import net.bible.android.control.event.ABEventBus;
import net.bible.android.control.document.DocumentControl;
import net.bible.android.control.event.ToastEvent;
import net.bible.android.control.event.apptobackground.AppToBackgroundEvent;
import net.bible.android.control.event.passage.PassageChangeStartedEvent;
import net.bible.android.control.event.passage.PassageChangedEvent;
import net.bible.android.control.event.passage.PreBeforeCurrentPageChangeEvent;
import net.bible.android.control.event.passage.SynchronizeWindowsEvent;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.page.CurrentPage;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.control.search.SearchControl;
import net.bible.android.view.activity.DaggerMainBibleActivityComponent;
import net.bible.android.view.activity.MainBibleActivityModule;
import net.bible.android.view.activity.base.CustomTitlebarActivityBase;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.android.view.activity.page.actionbar.BibleActionBarManager;
import net.bible.android.view.activity.page.actionmode.VerseActionModeMediator;
import net.bible.android.view.activity.page.screen.DocumentViewManager;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import javax.inject.Inject;


/** The main activity screen showing Bible text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class MainBibleActivity extends CustomTitlebarActivityBase implements VerseActionModeMediator.ActionModeMenuDisplay {
	static final int BACKUP_SAVE_REQUEST = 0;
	static final int BACKUP_RESTORE_REQUEST = 1;
	private static final int SDCARD_READ_REQUEST = 2;

	private static final String SCREEN_KEEP_ON_PREF = "screen_keep_on_pref";
	private static final String REQUEST_SDCARD_PERMISSION_PREF = "request_sdcard_permission_pref";

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

	private BackupControl backupControl;

	private SearchControl searchControl;

	private DocumentControl documentControl;

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
		ABEventBus.getDefault().register(this);

		// force the screen to be populated
		PassageChangeMediator.getInstance().forcePageUpdate();
		refreshScreenKeepOn();
		requestSdcardPermission();
	}

	private void refreshScreenKeepOn() {
		boolean keepOn = CommonUtils.getSharedPreferences().getBoolean(SCREEN_KEEP_ON_PREF, false);
		if(keepOn) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		}
	}



	@Override
	protected void onDestroy() {
		super.onDestroy();
		ABEventBus.getDefault().unregister(this);
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
		refreshScreenKeepOn();
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
		else {
			bibleActionBarManager.updateButtons();
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
			ABEventBus.getDefault().post(new SynchronizeWindowsEvent());
		} else if (mainMenuCommandHandler.isDocumentChanged(requestCode)) {
			updateActionBarButtons();
		}

	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch(requestCode) {
			case BACKUP_SAVE_REQUEST:
				if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					backupControl.backupDatabase();
				} else {
					Dialogs.getInstance().showMsg(R.string.error_occurred);
				}
				break;
			case BACKUP_RESTORE_REQUEST:
				if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					backupControl.restoreDatabase();
				} else {
					Dialogs.getInstance().showMsg(R.string.error_occurred);
				}
				break;
			case SDCARD_READ_REQUEST:
				if(grantResults.length>0) {
					if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
						documentControl.enableManualInstallFolder();
					} else {
						documentControl.turnOffManualInstallFolderSetting();
					}
				}
				break;
		}
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected void preferenceSettingsChanged() {
		documentViewManager.getDocumentView().applyPreferenceSettings();
		PassageChangeMediator.getInstance().forcePageUpdate();
		requestSdcardPermission();
	}

	private void requestSdcardPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			boolean requestSdCardPermission = CommonUtils.getSharedPreferences().getBoolean(REQUEST_SDCARD_PERMISSION_PREF, false);
			if(requestSdCardPermission && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
				requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, SDCARD_READ_REQUEST);
			}
		}
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
		updateActionBarButtons();
	}

	/**
	 * called just before starting work to change the current passage
	 */
	public void onEventMainThread(PassageChangeStartedEvent event) {
		documentViewManager.buildView();
	}

	/**
	 * called by PassageChangeMediator after a new passage has been changed and displayed
	 */
	public void onEventMainThread(PassageChangedEvent event) {
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
		if (this.gestureDetector.onTouchEvent(motionEvent)) {
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
	void setDocumentControl(DocumentControl documentControl) {
		this.documentControl = documentControl;
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

