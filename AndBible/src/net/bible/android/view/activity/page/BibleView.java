package net.bible.android.view.activity.page;

import java.lang.reflect.Method;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.splitscreen.SplitScreenEventListener;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl;
import net.bible.android.control.page.splitscreen.SplitScreenControl.Screen;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.screen.PageTiltScroller;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Picture;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** The WebView component that shows teh main bible and commentary text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleView extends WebView implements DocumentView, SplitScreenEventListener {
	
	private Screen splitScreenNo;
	
	private BibleJavascriptInterface mJavascriptInterface;
	
	private VerseCalculator mVerseCalculator;

	private int mJumpToVerse = 0;
	private float mJumpToYOffsetRatio = 0;
	
	private PageTiltScroller mPageTiltScroller;
	private boolean hideScrollBar;
	
	private boolean wasAtRightEdge;
	private boolean wasAtLeftEdge;

	private PageControl mPageControl = ControlFactory.getInstance().getPageControl();
	
	private static SplitScreenControl splitScreenControl = ControlFactory.getInstance().getSplitScreenControl();
	
	private static final String TAG = "BibleView";
	
	// remember current background colour so we know when it changes
	// -123 is not equal to WHITE or BLACK forcing first setting to be actioned
	private int mCurrentBackgroundColour = -123;
	
	/**
     * Constructor.  This version is only needed if you will be instantiating
     * the object manually (not from a layout XML file).
     * @param context
     */
	public BibleView(Context context, Screen splitScreenNo) {
		super(context);
		this.splitScreenNo = splitScreenNo;
		initialise();
	}

//    /**
//     * Construct object, initializing with any attributes we understand from a
//     * layout file. These attributes are defined in
//     * SDK/assets/res/any/classes.xml.
//     * 
//     * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
//     */
//	public BibleView(Context context, AttributeSet attrs) {
//		super(context, attrs);
//		initialise();
//	}
//	public BibleView(Context context, AttributeSet attrs, int defStyle) {
//		super(context, attrs, defStyle);
//		initialise();		
//	}
	
	@SuppressLint("SetJavaScriptEnabled")
	private void initialise() {
		mVerseCalculator = new VerseCalculator();
		mJavascriptInterface = new BibleJavascriptInterface(mVerseCalculator);
		
		addJavascriptInterface(mJavascriptInterface, "jsInterface");

		setPictureListener(new PictureListener() {
			/** this is called after the WebView page has finished loading and a new "picture" is on the webview.
			 */
			@Override
		    public void onNewPicture(WebView view, Picture arg1) {
				// go to any specified verse or offset
				if (mJumpToVerse > 0) { 
		    		if (mJumpToVerse==1) {
		    			// use scroll to becasue difficult to place a tag exactly at the top
		    			view.scrollTo(0,0);
		    		} else {
		    			view.loadUrl("javascript:location.href='#"+mJumpToVerse+"'");
		    		}
	    		} else if (mJumpToYOffsetRatio>0) {
		            int contentHeight = view.getContentHeight(); 
		            int y = (int) ((float)contentHeight*mJumpToYOffsetRatio);
		    		view.scrollTo(0, y);
	    		}
	    	    // must zero mJumpToVerse because setting location causes another onPageFinished
	    	    mJumpToVerse = -1; 
	    		mJumpToYOffsetRatio = -1;
		    }    
		});
		
		/* WebViewClient must be set BEFORE calling loadUrl! */  
		setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				// load Strongs refs when a user clicks on a link
				if (ControlFactory.getInstance().getLinkControl().loadApplicationUrl(url)) {
					return true;
				} else {
					return super.shouldOverrideUrlLoading(view, url);
				}
			}

			@Override
			public void onLoadResource(WebView view, String url) {
				Log.d(TAG, "onLoadResource:"+url);
				super.onLoadResource(view, url);
			}

			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				super.onReceivedError(view, errorCode, description, failingUrl);
				Log.e(TAG, description);
			}
		});

		// handle alerts
		setWebChromeClient(new WebChromeClient() {
		    @Override
	        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
	            Log.d(TAG, message);
	            result.confirm();
	            return true;
	        }
		});

		// need javascript to enable jump to anchors/verses
		getSettings().setJavaScriptEnabled(true);
		
		applyPreferenceSettings();
		
		mPageTiltScroller = new PageTiltScroller(this);
		mPageTiltScroller.enableTiltScroll(true);

		// if this webview becomes (in)active then must start/stop auto-scroll
		splitScreenControl.addSplitScreenEventListener(this);
		// initialise split state related code - always screen1 is selected first
		currentSplitScreenChanged(splitScreenControl.getCurrentActiveScreen());
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		// update the height in ScreenSettings
		ScreenSettings.setContentViewHeightPx(getMeasuredHeight());
	}

	/** apply settings set by the user using Preferences
	 */
	@Override
	public void applyPreferenceSettings() {
		applyFontSize();
		
		changeBackgroundColour();
		
		ScreenSettings.setContentViewHeightPx(getHeight());
	}

	private void applyFontSize() {
		int fontSize = mPageControl.getDocumentFontSize();
		getSettings().setDefaultFontSize(fontSize);

		// 1.6 is taken from css - line-height: 1.6em;
		ScreenSettings.setLineHeightDips((int)(1.6*fontSize));
	}
	
	/** may need updating depending on environmental brightness
	 */
	@Override
	public boolean changeBackgroundColour() {
		// if night mode then set dark background colour
		int newBackgroundColour = ScreenSettings.isNightMode() ? Color.BLACK : Color.WHITE;
		boolean changed = mCurrentBackgroundColour != newBackgroundColour;
		
		if (changed) {
			setBackgroundColor(newBackgroundColour);
			mCurrentBackgroundColour = newBackgroundColour;
		}
		return changed;
	}
	
	/** show a page from bible commentary
	 * 
	 * @param html
	 */
	@Override
	public void show(String html, int jumpToVerse, float jumpToYOffsetRatio) {
		Log.d(TAG, "Show(html,"+jumpToVerse+","+jumpToYOffsetRatio+") screen:"+splitScreenNo);
		// set background colour if necessary
		changeBackgroundColour();
		
		// call this from here because some documents may require an adjusted font size e.g. those using Greek font
		applyFontSize();
		
		mJumpToVerse = jumpToVerse;
		mJumpToYOffsetRatio = jumpToYOffsetRatio;
		
		// allow zooming if map
		boolean isMap = CurrentPageManager.getInstance().isMapShown();
		getSettings().setBuiltInZoomControls(isMap);
		// http://stackoverflow.com/questions/3808532/how-to-set-the-initial-zoom-width-for-a-webview
		getSettings().setLoadWithOverviewMode(isMap);
		getSettings().setUseWideViewPort(isMap);
		
		loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", "http://historyUrl");
	}

	/** prevent swipe right if the user is scrolling the page right */
	public boolean isPageNextOkay() {
		boolean isOkay = true;
		if (CurrentPageManager.getInstance(splitScreenNo).isMapShown()) {
			// allow swipe right if at right side of map
			boolean isAtRightEdge = (getScrollX() >= getMaxHorizontalScroll());

			// the first side swipe takes us to the edge and second takes us to next page
			isOkay = isAtRightEdge && wasAtRightEdge;
			wasAtRightEdge = isAtRightEdge;
			wasAtLeftEdge = false;
		}
		return isOkay;
	}
	
	/** prevent swipe left if the user is scrolling the page left */
	public boolean isPagePreviousOkay() {
		boolean isOkay = true;
		if (CurrentPageManager.getInstance(splitScreenNo).isMapShown()) {
			// allow swipe left if at left edge of map
			boolean isAtLeftEdge = (getScrollX() == 0);

			// the first side swipe takes us to the edge and second takes us to next page
			isOkay = isAtLeftEdge && wasAtLeftEdge;
			wasAtLeftEdge = isAtLeftEdge;
			wasAtRightEdge = false;
		}
		return isOkay;
	}

	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
		Log.d(TAG, "Focus changed so start/stop scroll");
		if (hasWindowFocus) {
			resumeTiltScroll();
		} else {
			pauseTiltScroll();
		}
	}
	
    private void pauseTiltScroll() {
		Log.d(TAG, "Pausing tilt to scroll "+splitScreenNo);
        mPageTiltScroller.enableTiltScroll(false);
    }
    
    private void resumeTiltScroll() {
    	// but if split screen then only if the current active split
    	if (splitScreenControl.isCurrentActiveScreen(splitScreenNo)) {
			Log.d(TAG, "Resuming tilt to scroll "+splitScreenNo);
	        mPageTiltScroller.enableTiltScroll(true);
    	}
    }
    
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		boolean handled = super.onTouchEvent(ev);
		
		splitScreenControl.setCurrentActiveScreen(splitScreenNo);
		
		// Allow user to redefine viewing angle by touching screen
		mPageTiltScroller.recalculateViewingPosition();
		
		return handled;
	}

	/** ensure auto-scroll does not continue when screen is powered off
	 */
	@Override
	public void onScreenTurnedOn() {
		resumeTiltScroll();
	}
	@Override
	public void onScreenTurnedOff() {
		pauseTiltScroll();
	}

	/** enter text selection mode
	 */
	@Override
	public void selectAndCopyText(LongPressControl longPressControl) {
		Log.d(TAG, "enter text selection mode");
		
		// JellyBean
		if (CommonUtils.isJellyBeanPlus()) {	
			Log.d(TAG, "keycode Enter for JB+");
			// retrigger a long-press but allow it to be handled by WebView
	        KeyEvent enterEvent = new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0,0);
	        longPressControl.ignoreNextLongPress();
	        enterEvent.dispatch(this);
		} else {
			    
			try {
				Log.d(TAG, "selectText for ICS");
				// ICS
	            WebView.class.getMethod("selectText").invoke(this);
	        } catch (Exception e1) {
			    try {
					Log.d(TAG, "emulateShiftHeld");
			        Method m = WebView.class.getMethod("emulateShiftHeld", (Class[])null);
			        m.invoke(this, (Object[])null);
			    } catch (Exception e2) {
					Log.d(TAG, "shiftPressEvent");
			        // fallback
			        KeyEvent shiftPressEvent = new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT,0,0);
			        shiftPressEvent.dispatch(this);
			    }
	        }
		}
	}
	
	@Override
    public float getCurrentPosition() {
    	// see http://stackoverflow.com/questions/1086283/getting-document-position-in-a-webview
        int contentHeight = getContentHeight();
        int scrollY = getScrollY();
        float ratio = ((float) scrollY / ((float) contentHeight));

        return ratio;
    }

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		//TODO allow DPAD_LEFT to always change page and navigation between links using dpad
		// placing BibleKeyHandler second means that DPAD left is unable to move to prev page if strongs refs are shown
		// vice-versa (webview second) means right & left can not be used to navigate between Strongs links

		// common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT to change chapter
		if (BibleKeyHandler.getInstance().onKeyUp(keyCode, event)) {
			return true;
		}
		
		// allow movement from link to link in current page
		return super.onKeyUp(keyCode, event);
	}

	public boolean scroll(boolean forward, int scrollAmount) {
		boolean ok = false;
		hideScrollBar = true;
		for (int i=0; i<scrollAmount; i++) {
			//TODO calculate lineHeight properly
			if (forward) {
				// scroll down/forward if not at bottom
				if (getScrollY()+1 < getMaxVerticalScroll()) {
					scrollBy(0, 1);
					ok = true;
				}
			} else {
				// scroll up/backward if not at top
				if (getScrollY() > 0) {
					// scroll up/back
					scrollBy(0, -1);
					ok = true;
				}
			}
		}
		hideScrollBar = false;
		return ok;
	}

	/** Used to prevent scroll off bottom using auto-scroll
	 * see http://stackoverflow.com/questions/5069765/android-webview-how-to-autoscroll-a-page
	 * @return
	 */
    private int getMaxVerticalScroll() {
    	//TODO get these once, they probably won't change 
        return computeVerticalScrollRange()-computeVerticalScrollExtent();
    }

    private int getMaxHorizontalScroll() {
        return computeHorizontalScrollRange()-computeHorizontalScrollExtent();
    }

    /** allow vertical scroll bar to be hidden during auto-scroll
     */
    protected boolean awakenScrollBars(int startDelay, boolean invalidate) {
    	if (!hideScrollBar) {
    		return super.awakenScrollBars(startDelay, invalidate);
    	} else {
    		return false;
    	}
    }
	@Override
	public View asView() {
		return this;
	}

	@Override
	public void save() {
		//NOOP
	}

	@Override
	public void currentSplitScreenChanged(Screen activeScreen) {
		if (splitScreenNo == activeScreen) {
			mJavascriptInterface.setNotificationsEnabled(true);
			resumeTiltScroll();
		} else {
			mJavascriptInterface.setNotificationsEnabled(false);
			pauseTiltScroll();
		}
	}

	@Override
	public void updateSecondaryScreen(Screen updateScreen, String html, int verseNo) {
		if (splitScreenNo == updateScreen) {
			changeBackgroundColour();
			show(html, verseNo, 0);
		}		
	}


	@Override
	public void scrollSecondaryScreen(Screen updateScreen, final int verseNo) {
		if (splitScreenNo == updateScreen && getHandler()!=null) {
			getHandler().post(new Runnable() {
				
				@Override
				public void run() {
//					loadUrl("javascript:location.href='#"+verseNo+"'");
					loadUrl("javascript:scrollTo('"+verseNo+"')");
				}
			});
		}
	}

	@Override
	public void numberOfScreensChanged() {
		// Noop
	}

	public Screen getSplitScreenNo() {
		return splitScreenNo;
	}

}
