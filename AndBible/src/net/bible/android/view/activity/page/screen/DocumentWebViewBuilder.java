package net.bible.android.view.activity.page.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.splitscreen.NumberOfScreensChangedEvent;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.splitscreen.Screen;
import net.bible.android.control.page.splitscreen.Separator;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
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

	private Map<Screen, BibleView> screenBibleViewMap;
	private static final int BIBLE_WEB_VIEW_ID_BASE = 990;

	private static SplitScreenControl splitScreenControl;

	private ViewGroup parentLayout;

	private boolean isLaidOutForPortrait;
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
	public void onEvent(NumberOfScreensChangedEvent event) {
		isSplitScreenConfigurationChanged = true;
	}
	
	/** return true if the current page should show a NyNote
	 */
	public boolean isWebViewType() {
		return !CurrentPageManager.getInstance().isMyNoteShown();
	}
	
	public void addWebView(LinearLayout parent) {
		this.parentLayout = parent;
		// TODO dodgy
    	boolean isWebView = parent.findViewById(BIBLE_WEB_VIEW_ID_BASE+1)!=null;
    	boolean isPortrait = CommonUtils.isPortrait();

    	if (!isWebView || 
    			isSplitScreenConfigurationChanged ||
    			isPortrait!=isLaidOutForPortrait) {
    		Log.d(TAG, "Layout web view");
    		
    		List<Screen> screens = splitScreenControl.getScreenManager().getVisibleScreens();
    		
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeWebView(parent);
    		
    		parent.setOrientation(isPortrait? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    		ViewGroup currentSplitScreenLayout = null;
    		Separator previousSeparator = null;
    		
    		for (int i=0; i<screens.size(); i++) {
    			final Screen screen = screens.get(i);
    			
    			currentSplitScreenLayout = new FrameLayout(this.mainActivity);
    			
    			BibleView bibleView = getView(screen);
        		// trigger recalc of verse positions in case width changes e.g. minimize/restore web view
        		bibleView.setVersePositionRecalcRequired(true);

    			float screenWeight = screen.getWeight();
    			LinearLayout.LayoutParams lp = isPortrait?	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 0, screenWeight) :
    														new LinearLayout.LayoutParams(0, LayoutParams.FILL_PARENT, screenWeight);

	    		parent.addView(currentSplitScreenLayout, lp);

				// add bible to framelayout
				LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				currentSplitScreenLayout.addView(bibleView, frameLayoutParamsBibleWebView);

				if (i>0) {
					Separator separator = previousSeparator;
					
					// extend touch area of separator
					addTopOrLeftSeparatorExtension(isPortrait, currentSplitScreenLayout, lp, separator);
				}

				// Add screen separator
				Separator separator = null;
				if (i<screens.size()-1) {
					separator = createSeparator(parent, isPortrait);
					
					// extend touch area of separator
					addBottomOrRightSeparatorExtension(isPortrait, currentSplitScreenLayout, lp, separator);

					// Add actual separator line dividing the split screens
					parent.addView(separator, isPortrait ? 	new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_WIDTH_PX, 0) :
															new LinearLayout.LayoutParams(SPLIT_SEPARATOR_WIDTH_PX, LayoutParams.FILL_PARENT, 0));
					// allow extension to be added in next screen
					previousSeparator = separator;
				}

				if (i>0) {
			        // minimise button
			        Button minimiseScreenButton = createMinimiseButton(screen);
	    			currentSplitScreenLayout.addView(minimiseScreenButton, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP|Gravity.RIGHT));
				} else {
	    			// new Screen button
			        Button newScreenButton = createNewScreenButton();
	    			currentSplitScreenLayout.addView(newScreenButton, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP|Gravity.RIGHT));
				}

    			mainActivity.registerForContextMenu(bibleView);
    		}
    		
    		// Display minimised screens
    		ViewGroup minimisedWindowsFrameContainer = currentSplitScreenLayout;
    		List<Screen> minimisedScreens = splitScreenControl.getScreenManager().getMinimisedScreens();
    		for (int i=0; i<minimisedScreens.size(); i++) {
    			Log.d(TAG,  "Show restore button");
    			Button restoreButton = createRestoreButton(minimisedScreens.get(i));
    			minimisedWindowsFrameContainer.addView(restoreButton, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.BOTTOM|Gravity.RIGHT));
    		}    		
    		
//    		} else if (splitScreenControl.isScreen2Minimized()) {
//    			Log.d(TAG,  "Show restore button");
//    			splitFrameLayout1.addView(restoreScreen2Button, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.BOTTOM|Gravity.RIGHT));
//    		}
    		
//    		mainActivity.registerForContextMenu(bibleWebView);
    		isLaidOutForPortrait = isPortrait;
    		isSplitScreenConfigurationChanged = false;
    	}
	}

	protected void addTopOrLeftSeparatorExtension(boolean isPortrait,
			ViewGroup currentSplitScreenLayout, LinearLayout.LayoutParams lp,
			Separator separator) {
		// add separator handle touch delegate to framelayout
		FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate2 = isPortrait ?	new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP) :
																					new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.FILL_PARENT, Gravity.LEFT);
		currentSplitScreenLayout.addView(separator.getTouchDelegateView2(), frameLayoutParamsSeparatorDelegate2);

		// separator will adjust layouts when dragged
		separator.setView2LayoutParams(lp);
	}

	protected void addBottomOrRightSeparatorExtension(boolean isPortrait,
			ViewGroup previousSplitScreenLayout,
			LinearLayout.LayoutParams previousLp, Separator separator) {
		// add first touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
		FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait? 	new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM) :
																					new FrameLayout.LayoutParams(SPLIT_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.FILL_PARENT, Gravity.RIGHT);
		previousSplitScreenLayout.addView(separator.getTouchDelegateView1(), frameLayoutParamsSeparatorDelegate);
		// separator will adjust layouts when dragged
		separator.setView1LayoutParams(previousLp);
	}

	protected Separator createSeparator(LinearLayout parent, boolean isPortrait) {
		Separator separator = new Separator(this.mainActivity, SPLIT_SEPARATOR_WIDTH_PX);
		separator.setParentLayout(parent);
		separator.setPortrait(isPortrait);
		return separator;
	}

	/**
	 * parent contains Frame, seperator, Frame.
	 * Frame contains BibleView
	 * @param parent
	 */
	public void removeWebView(ViewGroup parent) {
		for (int i=0; i<parent.getChildCount(); i++) {
			View view = parent.getChildAt(i);
			if (view instanceof ViewGroup) {
				// this detaches the BibleView from it's containing Frame
				((ViewGroup) view).removeAllViews();
			}
		}

		if (parent!=null) {
			parent.removeAllViews();
		}
	}
	
	public BibleView getView(Screen screen) {
		BibleView bibleView = screenBibleViewMap.get(screen);
		if (bibleView==null) {
			bibleView = new BibleView(this.mainActivity, screen);
	        bibleView.setId(BIBLE_WEB_VIEW_ID_BASE+screen.getScreenNo());
	        
	        screenBibleViewMap.put(screen, bibleView);
		}
		return bibleView;
	}

	private Button createNewScreenButton() {
		return createTextButton("+", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.addNewScreen();				
			}
		});
	}

	private Button createMinimiseButton(final Screen screen) {
		return createTextButton("━━", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.minimiseScreen(screen);				
			}
		});
	}

	private Button createRestoreButton(final Screen screen) {
        // restore button
        return createTextButton("\u2588"+screen.getScreenNo()+"\u2588", new OnClickListener() {
			@Override
			public void onClick(View v) {
				splitScreenControl.restoreScreen(screen);				
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
