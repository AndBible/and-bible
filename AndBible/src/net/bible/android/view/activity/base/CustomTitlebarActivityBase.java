package net.bible.android.view.activity.base;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.actionbar.ActionBarManager;
import net.bible.android.view.activity.base.toolbar.DefaultToolbar;
import net.bible.android.view.activity.base.toolbar.Toolbar;
import android.content.res.Configuration;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;

/**
 * Base class for activities with a custom title bar
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public abstract class CustomTitlebarActivityBase extends ActivityBase {
	
	private View mTitleBar;
	
	private Toolbar mToolbar;
	private ActionBarManager actionBarManager;
	private int optionsMenuId;
	
	private ProgressBar mProgressBarIndeterminate;

	private View mContentView;
	
	private static final String TAG = "CustomTitlebarActivityBase";

	// called whenever something like strong preferences have been changed by the user.  Should refresh the screen
	protected abstract void preferenceSettingsChanged();
	
	public CustomTitlebarActivityBase(ActionBarManager actionBarManager, int optionsMenuId) {
		this.actionBarManager = actionBarManager;
		this.optionsMenuId = optionsMenuId;
	}
	
	/** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
	 * and set the new titlebar layout just after
	 */
    @Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);

        mTitleBar = findViewById(R.id.titleBar);
        mContentView = mTitleBar.getRootView();
        
        initialiseToolbar(mTitleBar);
        
        mProgressBarIndeterminate = (ProgressBar)findViewById(R.id.progressCircular);
        
        // force the toolbar buttons to be shown correctly
        getToolbar().updateButtons();
    }
    
    /** 
     * load the default menu items from xml config 
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	// Inflate the menu
        getMenuInflater().inflate(optionsMenuId, menu);
        
        boolean showMenu = super.onCreateOptionsMenu(menu);
        
        return showMenu;
    }

    /**
     * Allow some menu items to be hidden or otherwise altered
     */
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
        actionBarManager.prepareOptionsMenu(this, menu, getSupportActionBar());
		
		// must return true for menu to be displayed
		return true;
	}

    
    public void toggleFullScreen() {
    	super.toggleFullScreen();
    	
    	if (!isFullScreen()) {
    		Log.d(TAG, "Showing title bar");
    		mTitleBar.setVisibility(View.VISIBLE);
    		getSupportActionBar().show();
    	} else {
    		Log.d(TAG, "Hiding title bar");
    		mTitleBar.setVisibility(View.GONE);
    		getSupportActionBar().hide();
    	}

    	mContentView.requestLayout();
    }

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		// the title bar has different widths depending on the orientation
		updateToolbarButtonText();
	}

	/** update the quick links in the title bar
     */
    public void updateToolbarButtonText() {
        getToolbar().updateButtons();
    	
        actionBarManager.updateButtons();
    }


    
    protected void initialiseToolbar(View toolBarContainer) {
    	getToolbar().initialise(toolBarContainer);
    }
    
    protected Toolbar getToolbar() {
    	if (mToolbar==null) {
    		mToolbar = new DefaultToolbar();
    	}
    	return mToolbar;
    }

    //TODO move this somewhere appropriate or call PageControl directly
	/** return true if Strongs numbers are shown */
	public boolean isStrongsShown() {
		return ControlFactory.getInstance().getPageControl().isStrongsShown();
	}

	public void setProgressBar(boolean on) {
		mProgressBarIndeterminate.setVisibility(on ? View.VISIBLE : View.GONE);
	}
}
