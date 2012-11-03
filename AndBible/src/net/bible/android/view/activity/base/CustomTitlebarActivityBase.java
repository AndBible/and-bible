package net.bible.android.view.activity.base;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.toolbar.BibleToolbarButton;
import net.bible.android.view.activity.base.toolbar.CommentaryToolbarButton;
import net.bible.android.view.activity.base.toolbar.DictionaryToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakFFToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakRewToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakStopToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakToolbarButton;
import net.bible.android.view.activity.navigation.ChooseDocument;
import net.bible.service.common.CommonUtils;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

public abstract class CustomTitlebarActivityBase extends ActivityBase {
	
	public enum HeaderButton {DOCUMENT, PAGE, BIBLE, COMMENTARY, DICTIONARY, TOGGLE_STRONGS, SPEAK, SPEAK_STOP, SPEAK_FF, SPEAK_REW};

	private View mTitleBar;
	
	private Button mDocumentTitleLink;
	private Button mPageTitleLink;
	private ProgressBar mProgressBarIndeterminate;

	private List<ToolbarButton> mToolbarButtonList;
	
	private ToggleButton mStrongsToggle;
	
	private View mContentView;
	
	private static final String TAG = "CustomTitlebarActivityBase";

	// called whenever something like strong preferences have been changed by the user.  Should refresh the screen
	protected abstract void preferenceSettingsChanged();
	
	/** custom title bar code to add the FEATURE_CUSTOM_TITLE just before setContentView
	 * and set the new titlebar layout just after
	 */
    @Override
	public void setContentView(int layoutResID) {
    	requestWindowFeature(Window.FEATURE_NO_TITLE);
		super.setContentView(layoutResID);

        mTitleBar = findViewById(R.id.titleBar);
        mContentView = mTitleBar.getRootView();
        
        mToolbarButtonList = new ArrayList<ToolbarButton>();
        mToolbarButtonList.add(new BibleToolbarButton(mTitleBar));
        mToolbarButtonList.add(new CommentaryToolbarButton(mTitleBar));
        mToolbarButtonList.add(new DictionaryToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakStopToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakRewToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakFFToolbarButton(mTitleBar));
        
        mDocumentTitleLink = (Button)findViewById(R.id.titleDocument);
        mPageTitleLink = (Button)findViewById(R.id.titlePassage);
        mProgressBarIndeterminate = (ProgressBar)findViewById(R.id.progressCircular);
        
        mStrongsToggle = (ToggleButton)findViewById(R.id.strongsToggle);
        
        mDocumentTitleLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.DOCUMENT);
            }
        });

        mPageTitleLink.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.PAGE);
            }
        });

        mStrongsToggle.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
            	handleHeaderButtonPress(HeaderButton.TOGGLE_STRONGS);
			}
		});
    }
    
	/** Central method to initiate handling of header button presses
     *  Also allows subclasses to know when a button has been pressed
     * 
     * @param buttonType
     */
    protected void handleHeaderButtonPress(HeaderButton buttonType) {
    	try {
	    	switch (buttonType) {
	    	case DOCUMENT:
	        	Intent docHandlerIntent = new Intent(CustomTitlebarActivityBase.this, ChooseDocument.class);
	        	startActivityForResult(docHandlerIntent, 1);
	    		break;
	    	case PAGE:
	        	Intent pageHandlerIntent = new Intent(CustomTitlebarActivityBase.this, CurrentPageManager.getInstance().getCurrentPage().getKeyChooserActivity());
	        	startActivityForResult(pageHandlerIntent, 1);
	    		break;
	    	case TOGGLE_STRONGS:
				// update the show-strongs pref setting according to the ToggleButton
				CommonUtils.getSharedPreferences().edit().putBoolean("show_strongs_pref", mStrongsToggle.isChecked()).commit();
				// redisplay the current page
				preferenceSettingsChanged();
	    		break;
	    	default:
	    		Log.e(TAG, "Unknown button pressed");
	    	}
    	} catch (Exception e) {
    		Log.e(TAG, "Error pressing header button", e);
    		showErrorMsg(R.string.error_occurred);
    	}
    }
    
    public void toggleFullScreen() {
    	super.toggleFullScreen();
    	
    	if (!isFullScreen()) {
    		Log.d(TAG, "Showing title bar");
    		mTitleBar.setVisibility(View.VISIBLE);
    	} else {
    		Log.d(TAG, "Hiding title bar");
    		mTitleBar.setVisibility(View.GONE);
    	}

    	mContentView.requestLayout();
    }

    @Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		updatePageTitle();
		
		updateSuggestedDocuments();

//		// the title bar has different widths depending on the orientation
//		int titleBarTitleWidthPixels = getResources().getDimensionPixelSize(R.dimen.title_bar_title_width);
//		Log.d(TAG, "Title bar width:"+titleBarTitleWidthPixels);
//		mPageTitle.setWidth(titleBarTitleWidthPixels);
	}

	/** refresh page title which shows current verse for bibles and commentaries or chapter for books etc 
	 */
	protected void updatePageTitle() {
		// page title varies in length depending on orientation so need to redisplay it on rotation
		setPageTitle(ControlFactory.getInstance().getPageControl().getCurrentPageTitle());
	}

	/** update the quick links in the title bar
     */
    public void updateSuggestedDocuments() {
        for (ToolbarButton button : mToolbarButtonList) {
        	button.update();
        }
        
        boolean showStrongsToggle = isStrongsRelevant();
        mStrongsToggle.setVisibility(showStrongsToggle? View.VISIBLE : View.GONE);
        if (showStrongsToggle) {
	        boolean isShowstrongs = CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	        mStrongsToggle.setChecked(isShowstrongs);
        }
    }

	/** return true if Strongs numbers are shown */
	public boolean isStrongsShown() {
		return isStrongsRelevant() && 
			   CommonUtils.getSharedPreferences().getBoolean("show_strongs_pref", true);
	}
	
	/** return true if Strongs are relevant to this doc & screen */
	public boolean isStrongsRelevant() {
		return ControlFactory.getInstance().getDocumentControl().isStrongsInBook();
	}

	/** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setPageTitleVisible(boolean show) {
		mPageTitleLink.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	/** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setPageTitle(CharSequence title) {
		mPageTitleLink.setText(title);
	}
	
    /** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
     */
	public void setDocumentTitle(CharSequence title) {
		mDocumentTitleLink.setText(title);
	}

	public void setProgressBar(boolean on) {
		mProgressBarIndeterminate.setVisibility(on ? View.VISIBLE : View.GONE);
	}
}
