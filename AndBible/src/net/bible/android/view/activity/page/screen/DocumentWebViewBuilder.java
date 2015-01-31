package net.bible.android.view.activity.page.screen;

import java.util.HashMap;
import java.util.Map;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.Screen;
import net.bible.android.control.page.splitscreen.Separator;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.BibleView;
import net.bible.service.common.CommonUtils;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * TEMP NOTE: http://stackoverflow.com/questions/961944/overlapping-views-in-android
 * FOR OVERLAY IMAGE
 */
/**
 * Build the main WebView component for displaying most document types
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
public class DocumentWebViewBuilder {

	private BibleView bibleWebView;
	private BibleView bibleWebView2;
	private static final int BIBLE_WEB_VIEW_ID = 991;
	private static final int BIBLE_WEB_VIEW2_ID = 992;
	private Separator separator;
	
	private static SplitScreenControl splitScreenControl;

	private ViewGroup parentLayout;
	private ViewGroup splitFrameLayout1;
	private ViewGroup splitFrameLayout2;
	private boolean isLaidOutForPortrait;
	private Button minimiseScreen2Button;
	private Button restoreScreen2Button;
	private Activity mainActivity;
	
	private int SPLIT_SEPARATOR_WIDTH_PX;
	private int SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX;
	private int SPLIT_BUTTON_TEXT_COLOUR;
	private int SPLIT_BUTTON_BACKGROUND_COLOUR;
	private int BUTTON_SIZE_PX;

	private static final String TAG="DocumentWebViewBuilder";

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
		splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();

        bibleWebView = new BibleView(this.mainActivity, splitScreenControl.getScreen(1));
        bibleWebView.setId(BIBLE_WEB_VIEW_ID);

        bibleWebView2 = new BibleView(this.mainActivity, splitScreenControl.getScreen(2));
        bibleWebView2.setId(BIBLE_WEB_VIEW2_ID);
        
        Resources res = BibleApplication.getApplication().getResources();
        SPLIT_SEPARATOR_WIDTH_PX = res.getDimensionPixelSize(R.dimen.split_screen_separator_width);
        SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX = res.getDimensionPixelSize(R.dimen.split_screen_separator_touch_expansion_width);
        SPLIT_BUTTON_TEXT_COLOUR = res.getColor(R.color.split_button_text_colour);
        SPLIT_BUTTON_BACKGROUND_COLOUR = res.getColor(R.color.split_button_background_colour);
        
        BUTTON_SIZE_PX = res.getDimensionPixelSize(R.dimen.minimise_restore_button_size);
        
        separator = new Separator(this.mainActivity, SPLIT_SEPARATOR_WIDTH_PX);
        
        splitFrameLayout1 = new FrameLayout(this.mainActivity);
        splitFrameLayout2 = new FrameLayout(this.mainActivity);

        // minimise button
        minimiseScreen2Button = createTextButton("━━", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.minimiseScreen(splitScreenControl.getScreen(2));				
			}
		});

        // restore button
        restoreScreen2Button = createTextButton("\u2588\u2588", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.restoreScreen(splitScreenControl.getScreen(2));				
			}
		});
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !CurrentPageManager.getInstance().isMyNoteShown();
	}
	
	public void addWebView(LinearLayout parent) {
		this.parentLayout = parent;
		separator.setParentLayout(parentLayout);
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID)!=null;
    	boolean isAlreadySplitWebView = isWebView && parent.findViewById(BIBLE_WEB_VIEW2_ID)!=null;
    	boolean isAlreadyScreen2Minimized = isWebView && restoreScreen2Button.getParent()!=null;
    	boolean isPortrait = CommonUtils.isPortrait();

    	if (!isWebView || 
    			isAlreadySplitWebView!=splitScreenControl.isSplit() || 
    			isAlreadyScreen2Minimized!=splitScreenControl.isScreen2Minimized() ||
    			isPortrait!=isLaidOutForPortrait) {
    		Log.d(TAG, "Layout web view");
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeWebView(parent);
    		
    		// trigger recalc of verse positions in case width changes e.g. minimize/restore web view
    		bibleWebView.setVersePositionRecalcRequired(true);
    		bibleWebView2.setVersePositionRecalcRequired(true);
    		
    		parent.setOrientation(isPortrait? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    		separator.setPortrait(isPortrait);

    		float screen1Weight = splitScreenControl.getScreen1Weight();
    		LinearLayout.LayoutParams lp = isPortrait?	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, screen1Weight) :
    													new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, screen1Weight);

			// add top view whether split or not
			//AddTop FrameLayout, then webview, [then separatorTouchExtender(beside separator)]
			// add a FrameLayout
    		parent.addView(splitFrameLayout1, lp);

			// add bible to framelayout
			LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
			splitFrameLayout1.addView(bibleWebView, frameLayoutParamsBibleWebView);

    		if (splitScreenControl.isSplit()) {

    			// add separator touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait? 	new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM) :
    																						new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.FILL_PARENT, Gravity.RIGHT);
    			splitFrameLayout1.addView(separator.getTouchDelegateView1(), frameLayoutParamsSeparatorDelegate);
    			
    			// line dividing the split screens
    			parent.addView(separator, isPortrait ? 	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_WIDTH_PX, 0) :
    													new LinearLayout.LayoutParams(SPLIT_SEPARATOR_WIDTH_PX, LayoutParams.FILL_PARENT, 0));
    			
    			// add a FrameLayout to the lower part of the LinearLayout to contain both a webView and separator extension
        		LinearLayout.LayoutParams lp2 = isPortrait?	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, 1-screen1Weight) :
        													new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, 1-screen1Weight);
        		parent.addView(splitFrameLayout2, lp2);
        		
    			// add bible to framelayout
//    			LayoutParams frameLayoutParamsBibleWebView2 = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
    			splitFrameLayout2.addView(bibleWebView2, frameLayoutParamsBibleWebView);
    			
    			// add separator handle touch delegate to framelayout
    			FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate2 = isPortrait ?	new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP) :
    																						new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.FILL_PARENT, Gravity.LEFT);
    			splitFrameLayout2.addView(separator.getTouchDelegateView2(), frameLayoutParamsSeparatorDelegate2);
    			splitFrameLayout2.addView(minimiseScreen2Button, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP|Gravity.RIGHT));

    			// separator will adjust layouts when dragged
    			separator.setView1LayoutParams(lp);
    			separator.setView2LayoutParams(lp2);

    			mainActivity.registerForContextMenu(bibleWebView2);
    		} else if (splitScreenControl.isScreen2Minimized()) {
    			Log.d(TAG,  "Show restore button");
    			splitFrameLayout1.addView(restoreScreen2Button, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.BOTTOM|Gravity.RIGHT));
    		}
    		
    		mainActivity.registerForContextMenu(bibleWebView);
    		isLaidOutForPortrait = isPortrait;
    	}
	}

	public void removeWebView(ViewGroup parent) {
		//TODO use multiple splits
		if (splitFrameLayout1!=null) {
			splitFrameLayout1.removeAllViews();
		}
		if (splitFrameLayout2!=null) {
			splitFrameLayout2.removeAllViews();
		}
		if (parent!=null) {
			parent.removeAllViews();
		}
	}
	
	public DocumentView getView(Screen screen) {
		//TODO use map
		if (1 == screen.getScreenNo()) {
			return bibleWebView;
		} else {
			return bibleWebView2;
		}
	}
	
	private Button createTextButton(String text, OnClickListener onClickListener) {
		Button button = new Button(this.mainActivity);
        button.setText(text);
        button.setWidth(BUTTON_SIZE_PX);
        button.setHeight(BUTTON_SIZE_PX);
        button.setBackgroundColor(SPLIT_BUTTON_BACKGROUND_COLOUR);
        button.setTextColor(SPLIT_BUTTON_TEXT_COLOUR);
        button.setTypeface(null, Typeface.BOLD);
        button.setSingleLine(true);
        button.setOnClickListener(onClickListener);
        return button;
	}
	
}
