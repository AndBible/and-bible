package net.bible.android.view.activity.page.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.splitscreen.NumberOfWindowsChangedEvent;
import net.bible.android.control.page.splitscreen.Separator;
import net.bible.android.control.page.splitscreen.Window;
import net.bible.android.control.page.splitscreen.Window.WindowOperation;
import net.bible.android.control.page.splitscreen.WindowControl;
import net.bible.android.view.activity.page.BibleView;
import net.bible.service.common.CommonUtils;
import android.annotation.SuppressLint;
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
import de.greenrobot.event.EventBus;

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

	private boolean isSplitScreenConfigurationChanged = true;

	private Map<Window, BibleView> screenBibleViewMap;
	private static final int BIBLE_WEB_VIEW_ID_BASE = 990;

	private static WindowControl windowControl;

	private boolean isLaidOutForPortrait;
	private Activity mainActivity;
	
	private int SPLIT_SEPARATOR_WIDTH_PX;
	private int SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX;
	private int SPLIT_BUTTON_TEXT_COLOUR;
	private int SPLIT_BUTTON_BACKGROUND_COLOUR;
	private int BUTTON_SIZE_PX;

	private LinearLayout previousParent;
	
	private static final String TAG="DocumentWebViewBuilder";

	public DocumentWebViewBuilder(Activity mainActivity) {
		this.mainActivity = mainActivity;
		
		windowControl = ControlFactory.getInstance().getWindowControl();
		
		screenBibleViewMap = new HashMap<>();

        Resources res = BibleApplication.getApplication().getResources();
        SPLIT_SEPARATOR_WIDTH_PX = res.getDimensionPixelSize(R.dimen.split_screen_separator_width);
        SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX = res.getDimensionPixelSize(R.dimen.split_screen_separator_touch_expansion_width);
        SPLIT_BUTTON_TEXT_COLOUR = res.getColor(R.color.split_button_text_colour);
        SPLIT_BUTTON_BACKGROUND_COLOUR = res.getColor(R.color.split_button_background_colour);
        
        BUTTON_SIZE_PX = res.getDimensionPixelSize(R.dimen.minimise_restore_button_size);
        
		// Be notified of any changes to split-screen config
		EventBus.getDefault().register(this);
	}
	
	/**
	 * Record changes to scplit screen config so can redraw screen from scratch.
	 */
	public void onEvent(NumberOfWindowsChangedEvent event) {
		isSplitScreenConfigurationChanged = true;
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !ControlFactory.getInstance().getCurrentPageControl().isMyNoteShown();
	}
	
	public void addWebView(LinearLayout parent) {
		// TODO dodgy
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID_BASE+1)!=null;
    	boolean isPortrait = CommonUtils.isPortrait();

    	if (!isWebView || 
    			isSplitScreenConfigurationChanged ||
    			isPortrait!=isLaidOutForPortrait) {
    		Log.d(TAG, "Layout web view");
    		
    		List<Window> windows = windowControl.getWindowRepository().getVisibleWindows();
    		
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeChildViews(previousParent);
    		
    		parent.setOrientation(isPortrait? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    		ViewGroup currentSplitScreenLayout = null;
    		Separator previousSeparator = null;
    		
    		int windowNo = 0;
    		
    		for (Window window : windows) {
    			Log.d(TAG, "Layout screen "+window.getScreenNo() + " of "+windows.size());
    			
    			currentSplitScreenLayout = new FrameLayout(this.mainActivity);
    			
    			BibleView bibleView = getView(window);

        		// trigger recalc of verse positions in case width changes e.g. minimize/restore web view
        		bibleView.setVersePositionRecalcRequired(true);

    			float screenWeight = window.getWindowLayout().getWeight();
    			LinearLayout.LayoutParams lp = isPortrait?	new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, screenWeight) :
    														new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, screenWeight);

	    		parent.addView(currentSplitScreenLayout, lp);

				// add bible to framelayout
				LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				currentSplitScreenLayout.addView(bibleView, frameLayoutParamsBibleWebView);

				if (windowNo>0) {
					Separator separator = previousSeparator;
					
					// extend touch area of separator
					addTopOrLeftSeparatorExtension(isPortrait, currentSplitScreenLayout, lp, separator);
				}

				// Add screen separator
				Separator separator = null;
				if (windowNo<windows.size()-1) {
					Window nextScreen = windows.get(windowNo+1);
					separator = createSeparator(parent, window, nextScreen, isPortrait, windows.size());
					
					// extend touch area of separator
					addBottomOrRightSeparatorExtension(isPortrait, currentSplitScreenLayout, lp, separator);

					// Add actual separator line dividing the split screens
					parent.addView(separator, isPortrait ? 	new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, SPLIT_SEPARATOR_WIDTH_PX, 0) :
															new LinearLayout.LayoutParams(SPLIT_SEPARATOR_WIDTH_PX, LayoutParams.MATCH_PARENT, 0));
					// allow extension to be added in next screen
					previousSeparator = separator;
				}

				if (windowNo>0) {
					Button defaultWindowActionButton;
					if (window.getDefaultOperation().equals(WindowOperation.DELETE)) {
				        // minimise button
				        defaultWindowActionButton = createRemoveButton(window);
					} else {
				        // minimise button
				        defaultWindowActionButton = createMinimiseButton(window);
					}
	    			currentSplitScreenLayout.addView(defaultWindowActionButton, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP|Gravity.RIGHT));
				}

    			mainActivity.registerForContextMenu(bibleView);
    			
    			windowNo++;
    		}
    		
    		// Display minimised screens
    		ViewGroup minimisedWindowsFrameContainer = currentSplitScreenLayout;
    		List<Window> minimisedScreens = windowControl.getWindowRepository().getMinimisedScreens();
    		for (int i=0; i<minimisedScreens.size(); i++) {
    			Log.d(TAG,  "Show restore button");
    			Button restoreButton = createRestoreButton(minimisedScreens.get(i));
    			minimisedWindowsFrameContainer.addView(restoreButton, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.BOTTOM|Gravity.RIGHT));
    		}    		
    		
    		previousParent = parent;
    		isLaidOutForPortrait = isPortrait;
    		isSplitScreenConfigurationChanged = false;
    	}
	}

	@SuppressLint("RtlHardcoded")
	protected void addTopOrLeftSeparatorExtension(boolean isPortrait,
			ViewGroup currentSplitScreenLayout, 
			LinearLayout.LayoutParams lp,
			Separator separator) {
		// add separator handle touch delegate to framelayout
		FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait ?	new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP) :
																					new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.LEFT);
		currentSplitScreenLayout.addView(separator.getTouchDelegateView2(), frameLayoutParamsSeparatorDelegate);

		// separator will adjust layouts when dragged
		separator.setView2LayoutParams(lp);
	}

	protected void addBottomOrRightSeparatorExtension(boolean isPortrait,
			ViewGroup previousSplitScreenLayout,
			LinearLayout.LayoutParams previousLp, 
			Separator separator) {
		// add first touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
		FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait? 	new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM) :
																					new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.RIGHT);
		previousSplitScreenLayout.addView(separator.getTouchDelegateView1(), frameLayoutParamsSeparatorDelegate);
		// separator will adjust layouts when dragged
		separator.setView1LayoutParams(previousLp);
	}

	protected Separator createSeparator(LinearLayout parent, Window window, Window nextScreen, boolean isPortrait, int numSplitScreens) {
		Separator separator = new Separator(this.mainActivity, SPLIT_SEPARATOR_WIDTH_PX, parent, window, nextScreen, numSplitScreens, isPortrait);
		return separator;
	}

	/**
	 * parent contains Frame, seperator, Frame.
	 * Frame contains BibleView
	 * @param parent
	 */
	public void removeChildViews(ViewGroup parent) {
		if (parent!=null) {
			for (int i=0; i<parent.getChildCount(); i++) {
				View view = parent.getChildAt(i);
				if (view instanceof ViewGroup) {
					// this detaches the BibleView from it's containing Frame
					removeChildViews((ViewGroup)view);
				}
			}

			parent.removeAllViews();
		}
	}
	
	public BibleView getView(Window window) {
		BibleView bibleView = screenBibleViewMap.get(window);
		if (bibleView==null) {
			bibleView = new BibleView(this.mainActivity, window);
	        bibleView.setId(BIBLE_WEB_VIEW_ID_BASE+window.getScreenNo());
	        
	        screenBibleViewMap.put(window, bibleView);
		}
		return bibleView;
	}

	private Button createNewScreenButton() {
		return createTextButton("+", new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.addNewWindow();				
			}
		});
	}

	private Button createRemoveButton(final Window window) {
		return createTextButton("X", new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.removeWindow(window);				
			}
		});
	}

	private Button createMinimiseButton(final Window window) {
		return createTextButton("━━", new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.minimiseWindow(window);				
			}
		});
	}

	private Button createRestoreButton(final Window window) {
        // restore button
        return createTextButton("\u2588"+"\u2588", new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.restoreWindow(window);				
			}
		});

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
