package net.bible.android.view.activity.page.screen;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.PopupMenu.OnMenuItemClickListener;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.Window.WindowOperation;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.MainBibleActivityScope;
import net.bible.android.view.activity.page.BibleView;
import net.bible.android.view.activity.page.BibleViewFactory;
import net.bible.android.view.activity.page.MainBibleActivity;
import net.bible.service.common.CommonUtils;

import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

/**
 * TEMP NOTE: http://stackoverflow.com/questions/961944/overlapping-views-in-android
 * FOR OVERLAY IMAGE
 */
/**
 * Build the main WebView component for displaying most document types
 * 
 * Structure of the layout:
 * 	parent
 * 		windowFrameLayout
 * 			bibleView
 * 			separatorExtension (touch delegate for next separator)
 * 		separator
 * 		windowFrameLayout
 * 			bibleView
 * 			separatorExtension (touch delegate for previous separator)
 * 			minimiseButton
 * 
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's authors.
 * @author Martin Denham [mjdenham at gmail dot com]
 */
@MainBibleActivityScope
public class DocumentWebViewBuilder {

	private boolean isWindowConfigurationChanged = true;

	private final WindowControl windowControl;

	private boolean isLaidOutWithHorizontalSplit;
	private final MainBibleActivity mainBibleActivity;

	private final BibleViewFactory bibleViewFactory;

	private final WindowMenuCommandHandler windowMenuCommandHandler;

	final private int WINDOW_SEPARATOR_WIDTH_PX;
	final private int WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX;
	final private int WINDOW_BUTTON_TEXT_COLOUR;
	final private int WINDOW_BUTTON_BACKGROUND_COLOUR;
	final private int BUTTON_SIZE_PX;

	final static private String SPLIT_MODE_PREF = "split_mode_pref";
	final static private String SPLIT_MODE_AUTOMATIC = "automatic";
	final static private String SPLIT_MODE_VERTICAL = "vertical";
	final static private String SPLIT_MODE_HORIZONTAL = "horizontal";

	private LinearLayout previousParent;

	private static final String TAG="DocumentWebViewBuilder";

	@Inject
	public DocumentWebViewBuilder(WindowControl windowControl, MainBibleActivity mainBibleActivity, BibleViewFactory bibleViewFactory, WindowMenuCommandHandler windowMenuCommandHandler) {
		this.windowControl = windowControl;
		this.mainBibleActivity = mainBibleActivity;
		this.bibleViewFactory = bibleViewFactory;
		this.windowMenuCommandHandler = windowMenuCommandHandler;

        Resources res = BibleApplication.getApplication().getResources();
        WINDOW_SEPARATOR_WIDTH_PX = res.getDimensionPixelSize(R.dimen.window_separator_width);
        WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX = res.getDimensionPixelSize(R.dimen.window_separator_touch_expansion_width);
        WINDOW_BUTTON_TEXT_COLOUR = res.getColor(R.color.window_button_text_colour);
        WINDOW_BUTTON_BACKGROUND_COLOUR = res.getColor(R.color.window_button_background_colour);
        
        BUTTON_SIZE_PX = res.getDimensionPixelSize(R.dimen.minimise_restore_button_size);
        
		// Be notified of any changes to window config
		EventBus.getDefault().register(this);
	}
	
	/**
	 * Record changes to scplit screen config so can redraw screen from scratch.
	 */
	public void onEvent(NumberOfWindowsChangedEvent event) {
		isWindowConfigurationChanged = true;
	}

	/**
	 * Enable switch from Bible WebView to MyNote view
	 */
	public void removeWebView(ViewGroup parent) {
    	boolean isWebView = isWebViewShowing(parent);
    	
    	if (isWebView) {
        	parent.setTag("");
    		removeChildViews(parent);
    	}
	}
	
	@SuppressLint("RtlHardcoded")
	public void addWebView(LinearLayout parent) {

		String pref = CommonUtils.getSharedPreference(SPLIT_MODE_PREF, SPLIT_MODE_AUTOMATIC);

		boolean splitHorizontally;
		switch(pref) {
			case SPLIT_MODE_AUTOMATIC:
				splitHorizontally = CommonUtils.isPortrait();
				break;
			case SPLIT_MODE_VERTICAL:
				splitHorizontally = false;
				break;
			case SPLIT_MODE_HORIZONTAL:
				splitHorizontally = true;
				break;
			default:
				throw new RuntimeException("Illegal preference");
		}

    	boolean isWebView = isWebViewShowing(parent);
    	parent.setTag(TAG);

    	if (!isWebView || 
    			isWindowConfigurationChanged ||
    			splitHorizontally!= isLaidOutWithHorizontalSplit) {
    		Log.d(TAG, "Layout web view");
    		
    		List<Window> windows = windowControl.getWindowRepository().getVisibleWindows();
    		
    		// ensure we have a known starting point - could be none, 1, or 2 webviews present
    		removeChildViews(previousParent);
    		
    		parent.setOrientation(splitHorizontally? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    		ViewGroup currentWindowFrameLayout = null;
    		Separator previousSeparator = null;
    		
    		int windowNo = 0;
    		
    		for (Window window : windows) {
    			Log.d(TAG, "Layout screen "+window.getScreenNo() + " of "+windows.size());
    			
    			currentWindowFrameLayout = new FrameLayout(this.mainBibleActivity);
    			
    			BibleView bibleView = getCleanView(window);

        		// trigger recalc of verse positions in case width changes e.g. minimize/restore web view
        		bibleView.setVersePositionRecalcRequired(true);

    			float windowWeight = window.getWindowLayout().getWeight();
    			LinearLayout.LayoutParams lp = splitHorizontally?	new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, windowWeight) :
    														new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, windowWeight);

	    		parent.addView(currentWindowFrameLayout, lp);

				// add bible to framelayout
				LayoutParams frameLayoutParamsBibleWebView = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				currentWindowFrameLayout.addView(bibleView, frameLayoutParamsBibleWebView);

				if (windowNo>0) {
					Separator separator = previousSeparator;
					
					// extend touch area of separator
					addTopOrLeftSeparatorExtension(splitHorizontally, currentWindowFrameLayout, lp, separator);
				}

				// Add screen separator
				if (windowNo<windows.size()-1) {
					Window nextWindow = windows.get(windowNo+1);
					Separator separator = createSeparator(parent, window, nextWindow, splitHorizontally, windows.size());
					
					// extend touch area of separator
					addBottomOrRightSeparatorExtension(splitHorizontally, currentWindowFrameLayout, lp, separator);

					// Add actual separator line dividing two windows
					parent.addView(separator, splitHorizontally ? 	new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_WIDTH_PX, 0) :
															new LinearLayout.LayoutParams(WINDOW_SEPARATOR_WIDTH_PX, LayoutParams.MATCH_PARENT, 0));
					// allow extension to be added in next screen
					previousSeparator = separator;
				}

				// leave main window clear of distracting minimise button, but simplify unmaximise
				if (windowNo!=0 || window.isMaximised()) {
					// create default action button for top right of each window
					View defaultWindowActionButton = createDefaultWindowActionButton(window);
	    			currentWindowFrameLayout.addView(defaultWindowActionButton, new FrameLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX, Gravity.TOP|Gravity.RIGHT));
				}

    			windowNo++;
    		}
    		
    		// Display minimised screens
    		ViewGroup minimisedWindowsFrameContainer = new LinearLayout(mainBibleActivity);
    		currentWindowFrameLayout.addView(minimisedWindowsFrameContainer, new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, BUTTON_SIZE_PX, Gravity.BOTTOM|Gravity.RIGHT));
    		List<Window> minimisedScreens = windowControl.getWindowRepository().getMinimisedScreens();
    		for (int i=0; i<minimisedScreens.size(); i++) {
    			Log.d(TAG,  "Show restore button");
    			Button restoreButton = createRestoreButton(minimisedScreens.get(i));
    			minimisedWindowsFrameContainer.addView(restoreButton, new LinearLayout.LayoutParams(BUTTON_SIZE_PX, BUTTON_SIZE_PX));
    		}    		
    		
    		previousParent = parent;
    		isLaidOutWithHorizontalSplit = splitHorizontally;
    		isWindowConfigurationChanged = false;
    	}
	}

	private View createDefaultWindowActionButton(Window window) {
		View defaultWindowActionButton;
		if (window.getDefaultOperation().equals(WindowOperation.CLOSE)) {
		    // close button for the links window
		    defaultWindowActionButton = createCloseButton(window);
		} else if (window.getDefaultOperation().equals(WindowOperation.MAXIMISE)) {
		    // normalise button for maximised window
		    defaultWindowActionButton = createMaximiseToggleButton(window);
		} else {
		    // minimise button for normal window
		    defaultWindowActionButton = createMinimiseButton(window);
		}
		return defaultWindowActionButton;
	}

	/**
	 *	Add extension preceding separator 
	 */
	@SuppressLint("RtlHardcoded")
	private void addBottomOrRightSeparatorExtension(boolean isPortrait,
			ViewGroup previousWindowLayout,
			LinearLayout.LayoutParams previousLp, 
			Separator separator) {
		// add first touch delegate to framelayout which extends the touch area, otherwise it is difficult to select the separator to move it
		FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait? 	new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.BOTTOM) :
																					new FrameLayout.LayoutParams(WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.RIGHT);
		previousWindowLayout.addView(separator.getTouchDelegateView1(), frameLayoutParamsSeparatorDelegate);
		// separator will adjust layouts when dragged
		separator.setView1LayoutParams(previousLp);
	}

	/**
	 *	Add extension after separator 
	 */
	@SuppressLint("RtlHardcoded")
	private void addTopOrLeftSeparatorExtension(boolean isPortrait,
			ViewGroup currentWindowLayout, 
			LinearLayout.LayoutParams lp,
			Separator separator) {
		// add separator handle touch delegate to framelayout
		FrameLayout.LayoutParams frameLayoutParamsSeparatorDelegate = isPortrait ?	new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, Gravity.TOP) :
																					new FrameLayout.LayoutParams(WINDOW_SEPARATOR_TOUCH_EXPANSION_WIDTH_PX, LayoutParams.MATCH_PARENT, Gravity.LEFT);
		currentWindowLayout.addView(separator.getTouchDelegateView2(), frameLayoutParamsSeparatorDelegate);

		// separator will adjust layouts when dragged
		separator.setView2LayoutParams(lp);
	}

	protected Separator createSeparator(LinearLayout parent, Window window, Window nextScreen, boolean isPortrait, int numWindows) {
		return new Separator(this.mainBibleActivity, WINDOW_SEPARATOR_WIDTH_PX, parent, window, nextScreen, numWindows, isPortrait, windowControl);
	}

	/**
	 * parent contains Frame, seperator, Frame.
	 * Frame contains BibleView
	 * @param parent
	 */
	private void removeChildViews(ViewGroup parent) {
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

	/**
	 * Attempt to fix occasional error: "The specified child already has a parent. You must call removeView() on the child's parent first."
	 */
	private BibleView getCleanView(Window window) {
		BibleView bibleView = getView(window);
		ViewParent parent = bibleView.getParent();
		if (parent!=null && parent instanceof ViewGroup) {
			ViewGroup viewGroupParent = (ViewGroup)parent;
			viewGroupParent.removeView(bibleView);
		}
		return bibleView;
	}

	public BibleView getView(Window window) {
		return bibleViewFactory.createBibleView(window);
	}

	private Button createCloseButton(final Window window) {
		return createTextButton("X", new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.closeWindow(window);				
			}
			
		},
		new WindowButtonLongClickListener(window));
	}

	private Button createMaximiseToggleButton(final Window window) {
		return createImageButton(R.drawable.ic_menu_unmaximise, new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.unmaximiseWindow(window);				
			}
		},
		new WindowButtonLongClickListener(window));
	}

	private Button createMinimiseButton(final Window window) {
		return createTextButton("━━", new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.minimiseWindow(window);				
			}
		},
		new WindowButtonLongClickListener(window));
	}

	private Button createRestoreButton(final Window window) {
        // restore button
        return createTextButton(getDocumentInitial(window), new OnClickListener() {
			@Override
			public void onClick(View v) {
				windowControl.restoreWindow(window);				
			}
		},
		null);
	}

	/** 
	 * Get the first initial of the doc in the window to show in the minimise restore button
	 */
	protected String getDocumentInitial(final Window window) {
		try {
			return window.getPageManager().getCurrentPage().getCurrentDocument().getAbbreviation().substring(0,  1);
		} catch (Exception e) {
			return " ";
		}
	}
	
	private Button createTextButton(String text, OnClickListener onClickListener, OnLongClickListener onLongClickListener) {
		Button button = new Button(this.mainBibleActivity);
		button.setText(text);
		button.setBackgroundColor(WINDOW_BUTTON_BACKGROUND_COLOUR);
        button.setWidth(BUTTON_SIZE_PX);
        button.setHeight(BUTTON_SIZE_PX);
        button.setTextColor(WINDOW_BUTTON_TEXT_COLOUR);
        button.setTypeface(null, Typeface.BOLD);
        button.setSingleLine(true);
        button.setOnClickListener(onClickListener);
        button.setOnLongClickListener(onLongClickListener);
        return button;
	}

	private Button createImageButton(int drawableId, OnClickListener onClickListener, OnLongClickListener onLongClickListener) {
		Button button = new Button(this.mainBibleActivity);
		button.setBackgroundColor(WINDOW_BUTTON_BACKGROUND_COLOUR);
		button.setBackgroundResource(drawableId);
        button.setWidth(BUTTON_SIZE_PX);
        button.setHeight(BUTTON_SIZE_PX);
        button.setOnClickListener(onClickListener);
        button.setOnLongClickListener(onLongClickListener);
        return button;
	}

	private class WindowButtonLongClickListener implements OnLongClickListener {
		private Window window;
		
		public WindowButtonLongClickListener(Window window) {
			this.window = window;
		}

		@Override
		public boolean onLongClick(View v) {
			// Android 2.3 has various errors around popup menus so just support this shortcut for the primary user base which is on Android 4.0+ 
			if (CommonUtils.isIceCreamSandwichPlus()) {
				// ensure actions affect the right window
				windowControl.setActiveWindow(window);
				
			    PopupMenu popup = new PopupMenu(mainBibleActivity, v);
			    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

					@Override
					public boolean onMenuItemClick(MenuItem menuItem) {
						return windowMenuCommandHandler.handleMenuRequest(menuItem);
					}
				});
			    
			    MenuInflater inflater = popup.getMenuInflater();
			    inflater.inflate(R.menu.window_popup_menu, popup.getMenu());
			    
			    // enable/disable and set synchronised checkbox
			    windowControl.updateOptionsMenu(popup.getMenu());
			    
			    CommonUtils.forcePopupMenuToShowIcons(popup);
			    
			    popup.show();
				return true;
			} else {
				return false;
			}
		}
	}

	private boolean isWebViewShowing(ViewGroup parent) {
		Object tag = parent.getTag();
		return tag!=null && tag.equals(TAG);
	}
}
