package net.bible.android.view.activity.base;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.view.activity.base.toolbar.BibleToolbarButton;
import net.bible.android.view.activity.base.toolbar.CommentaryToolbarButton;
import net.bible.android.view.activity.base.toolbar.CurrentDocumentToolbarButton;
import net.bible.android.view.activity.base.toolbar.CurrentPageToolbarButton;
import net.bible.android.view.activity.base.toolbar.DictionaryToolbarButton;
import net.bible.android.view.activity.base.toolbar.StrongsToolbarButton;
import net.bible.android.view.activity.base.toolbar.ToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakFFToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakRewToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakStopToolbarButton;
import net.bible.android.view.activity.base.toolbar.speak.SpeakToolbarButton;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;

public abstract class CustomTitlebarActivityBase extends ActivityBase {
	
	public enum HeaderButton {DOCUMENT, PAGE, BIBLE, COMMENTARY, DICTIONARY, TOGGLE_STRONGS, SPEAK, SPEAK_STOP, SPEAK_FF, SPEAK_REW};

	private View mTitleBar;
	
	private ProgressBar mProgressBarIndeterminate;

	private List<ToolbarButton> mToolbarButtonList;
	
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
        mToolbarButtonList.add(new CurrentDocumentToolbarButton(mTitleBar));
        mToolbarButtonList.add(new CurrentPageToolbarButton(mTitleBar));
        mToolbarButtonList.add(new BibleToolbarButton(mTitleBar));
        mToolbarButtonList.add(new CommentaryToolbarButton(mTitleBar));
        mToolbarButtonList.add(new DictionaryToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakStopToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakRewToolbarButton(mTitleBar));
        mToolbarButtonList.add(new SpeakFFToolbarButton(mTitleBar));
        mToolbarButtonList.add(new StrongsToolbarButton(mTitleBar));
        
        mProgressBarIndeterminate = (ProgressBar)findViewById(R.id.progressCircular);
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
		
		updateToolbarButtonText();

//		// the title bar has different widths depending on the orientation
//		int titleBarTitleWidthPixels = getResources().getDimensionPixelSize(R.dimen.title_bar_title_width);
//		Log.d(TAG, "Title bar width:"+titleBarTitleWidthPixels);
//		mPageTitle.setWidth(titleBarTitleWidthPixels);
	}

	/** update the quick links in the title bar
     */
    public void updateToolbarButtonText() {
        for (ToolbarButton button : mToolbarButtonList) {
        	button.update();
        }
    }

    //TODO move this somewhere appropriate or call PageControl directly
	/** return true if Strongs numbers are shown */
	public boolean isStrongsShown() {
		return ControlFactory.getInstance().getPageControl().isStrongsShown();
	}

//TODO - do we need this
//	/** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
//     */
//	public void setPageTitleVisible(boolean show) {
//		mPageTitleLink.setVisibility(show ? View.VISIBLE : View.GONE);
//	}

//	/** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
//     */
//	public void setPageTitle(CharSequence title) {
//		mPageTitleLink.setText(title);
//	}
	
//    /** must wait until child has setContentView before setting custom title bar so intercept the method and then set the title bar
//     */
//	public void setDocumentTitle(CharSequence title) {
//		mDocumentTitleLink.setText(title);
//	}

	public void setProgressBar(boolean on) {
		mProgressBarIndeterminate.setVisibility(on ? View.VISIBLE : View.GONE);
	}
}
