package net.bible.android.view.activity.base;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import net.bible.android.BibleApplication;
import net.bible.android.view.activity.ActivityComponent;
import net.bible.android.view.activity.DaggerActivityComponent;
import net.bible.android.view.util.locale.LocaleHelper;
import net.bible.android.view.activity.navigation.History;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.android.view.util.UiUtils;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;
import net.bible.service.history.HistoryTraversal;
import net.bible.service.history.HistoryTraversalFactory;
import net.bible.service.sword.SwordDocumentFacade;

import javax.inject.Inject;

/** Base class for activities
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class ActivityBase extends AppCompatActivity implements AndBibleActivity {

	// standard request code for startActivityForResult
	public static final int STD_REQUEST_CODE = 1;
	
	// Special result that requests all activities to exit until the main/top Activity is reached
    public static final int RESULT_RETURN_TO_TOP           = 900;

	private SharedActivityState sharedActivityState = SharedActivityState.getInstance();
	
	private boolean isScreenOn = true;

	// some screens are highly customised and the theme looks odd if it changes
	private boolean allowThemeChange = true;
	
	private View mContentView;

	private HistoryTraversal historyTraversal;

	private boolean integrateWithHistoryManagerInitialValue;

	private SwordDocumentFacade swordDocumentFacade;
	
	private static final String TAG = "ActivityBase";
	
	/** Called when the activity is first created. */
    @SuppressLint("MissingSuperCall")
	@Override
    public void onCreate(Bundle savedInstanceState) {
    	this.onCreate(savedInstanceState, false);
    }

    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState, boolean integrateWithHistoryManager) {
    	if (allowThemeChange) {
    		UiUtils.applyTheme(this);
    	}

		super.onCreate(savedInstanceState);
    	
        Log.i(getLocalClassName(), "onCreate:"+this);

		this.integrateWithHistoryManagerInitialValue = integrateWithHistoryManager;

        // Register current activity in onCreate and onResume
        CurrentActivityHolder.getInstance().setCurrentActivity(this);

        // fix for null context class loader (http://code.google.com/p/android/issues/detail?id=5697)
        // this affected jsword dynamic classloading
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		
        setFullScreen(isFullScreen());

		// if locale is overridden then have to force title to be translated here
		LocaleHelper.translateTitle(this);
    }

	protected ActivityComponent buildActivityComponent() {
		return DaggerActivityComponent.builder()
				.applicationComponent(BibleApplication.getApplication().getApplicationComponent())
				.build();
	}

    @Override
	public void startActivity(Intent intent) {
    	historyTraversal.beforeStartActivity();
    	
		super.startActivity(intent);
	}
	@Override
	public void startActivityForResult(Intent intent, int requestCode) {
    	historyTraversal.beforeStartActivity();

    	super.startActivityForResult(intent, requestCode);
	}

	/**
	 * Override locale.  If user has selected a different ui language to the devices default language
	 */
	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(LocaleHelper.onAttach(newBase));
	}

	/**	This will be called automatically for you on 2.0 or later
	 */
	@Override
	public void onBackPressed() {
		if (!historyTraversal.goBack()) {
			super.onBackPressed();
		}
	}
	
	/**
	 * Change fullscreen state for this and all future activities
	 */
    public void toggleFullScreen() {
    	sharedActivityState.toggleFullScreen();
    	setFullScreen(sharedActivityState.isFullScreen());
    }
	
    /**
     * Are all activities currently in full screen mode
     */
	public boolean isFullScreen() {
		return sharedActivityState.isFullScreen();
	}
	
	private void setFullScreen(boolean isFullScreen) {
    	if (!isFullScreen) {
    		Log.d(TAG, "NOT Fullscreen");
    		// http://stackoverflow.com/questions/991764/hiding-title-in-a-fullscreen-mode
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			setLightsOutMode(false);
    	} else {
    		Log.d(TAG, "Fullscreen");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
			setLightsOutMode(true);
    	}
	}
	
	@SuppressLint("NewApi")
	private void setLightsOutMode(boolean isLightsOut) {
		if (CommonUtils.isHoneycombPlus() && mContentView!=null) {
	    	if (isLightsOut) {
				mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
	    	} else {
				mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
	    	}
		}
	}

	/** called by Android 2.0 +
	 */
	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		// ignore long press on search because it causes errors
		if (keyCode == KeyEvent.KEYCODE_SEARCH) {
	    	// ignore
			return true;
		}
		
		//TODO make Long press Back work for screens other than main window e.g. does not work from search screen because wrong window is displayed
	    if (keyCode == KeyEvent.KEYCODE_BACK && this instanceof MainBibleActivity) {
			Log.d(TAG, "Back Long");
	        // a long press of the back key. do our work, returning true to consume it.  by returning true, the framework knows an action has
	        // been performed on the long press, so will set the cancelled flag for the following up event.
	    	Intent intent = new Intent(this, History.class);
	    	startActivityForResult(intent, 1);
	        return true;
	    }
	    
		//TODO make Long press back - currently the History screen does not show the correct screen after item selection if not called from main window 
	    if (keyCode == KeyEvent.KEYCODE_BACK) {
	    	// ignore
	    	return true;
	    }

	    return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean isIntegrateWithHistoryManager() {
		return historyTraversal.isIntegrateWithHistoryManager();
	}
	@Override
	public void setIntegrateWithHistoryManager(boolean integrateWithHistoryManager) {
		historyTraversal.setIntegrateWithHistoryManager(integrateWithHistoryManager);
	}
	
    /** allow activity to enhance intent to correctly restore state */
	public Intent getIntentForHistoryList() {
		return getIntent();
	}

	public void showErrorMsg(int msgResId) {
		Dialogs.getInstance().showErrorMsg(msgResId);
	}

    protected void showHourglass() {
    	Dialogs.getInstance().showHourglass();
    }
    protected void dismissHourglass() {
    	Dialogs.getInstance().dismissHourglass();
    }

    protected void returnErrorToPreviousScreen() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(Activity.RESULT_CANCELED, resultIntent);
    	finish();    
    }
    protected void returnToPreviousScreen() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(Activity.RESULT_OK, resultIntent);
    	finish();    
    }
    
    protected void returnToTop() {
    	// just pass control back to the previous screen
    	Intent resultIntent = new Intent(this, this.getClass());
    	setResult(RESULT_RETURN_TO_TOP, resultIntent);
    	finish();    
    }
    
	@Override
	protected void onResume() {
		super.onResume();
        Log.i(getLocalClassName(), "onResume:"+this);
        CurrentActivityHolder.getInstance().setCurrentActivity(this);
        
        //allow action to be called on screen being turned on
		if (!isScreenOn && ScreenSettings.isScreenOn()) {
			onScreenTurnedOn();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
        Log.i(getLocalClassName(), "onPause:"+this);
		if (isScreenOn && !ScreenSettings.isScreenOn()) {
			onScreenTurnedOff();
		}
	}
	
	protected void onScreenTurnedOff() {
		Log.d(TAG, "Window turned off");
		isScreenOn = false;
	}

	protected void onScreenTurnedOn() {
		Log.d(TAG, "Window turned on");
		isScreenOn = true;
	}

	@Override
	protected void onRestart() {
		super.onRestart();
        Log.i(getLocalClassName(), "onRestart:"+this);
	}

	@Override
	protected void onStart() {
		super.onStart();
        Log.i(getLocalClassName(), "onStart:"+this);
	}


	@Override
	protected void onStop() {
		super.onStop();
        Log.i(getLocalClassName(), "onStop:"+this);
        // screen can still be considered as current screen if put on stand-by
        // removing this if causes speech to stop when screen is put on stand-by
        if (isScreenOn) {
	        // call this onStop, although it is not guaranteed to be called, to ensure an overlap between dereg and reg of current activity, otherwise AppToBackground is fired mistakenly
	        CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this);
        }
	}

	public void setAllowThemeChange(boolean allowThemeChange) {
		this.allowThemeChange = allowThemeChange;
	}
	
	/** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
	 * and set the new titlebar layout just after
	 */
    @Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);

        mContentView = getWindow().getDecorView().findViewById(android.R.id.content);
    }

	public View getContentView() {
		return mContentView;
	}

	/**
	 * Each activity instance needs its own HistoryTraversal object
	 * @param historyTraversalFactory
	 */
	@Inject
	void setNewHistoryTraversal(HistoryTraversalFactory historyTraversalFactory) {
		// Ensure we don't end up overwriting the initialised class
		if (historyTraversal==null) {
			this.historyTraversal = historyTraversalFactory.createHistoryTraversal(integrateWithHistoryManagerInitialValue);
		}
	}

	@Inject
	void setSwordDocumentFacade(SwordDocumentFacade swordDocumentFacade) {
		this.swordDocumentFacade = swordDocumentFacade;
	}

	protected SwordDocumentFacade getSwordDocumentFacade() {
		return swordDocumentFacade;
	}
}
