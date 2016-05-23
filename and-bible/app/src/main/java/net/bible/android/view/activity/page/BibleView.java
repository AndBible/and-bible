package net.bible.android.view.activity.page;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import net.bible.android.SharedConstants;
import net.bible.android.control.ControlFactory;
import net.bible.android.control.event.window.CurrentWindowChangedEvent;
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent;
import net.bible.android.control.event.window.ScrollSecondaryWindowEvent;
import net.bible.android.control.event.window.UpdateSecondaryWindowEvent;
import net.bible.android.control.event.window.WindowSizeChangedEvent;
import net.bible.android.control.page.PageControl;
import net.bible.android.control.page.window.Window;
import net.bible.android.control.page.window.WindowControl;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.android.view.activity.page.screen.PageTiltScroller;
import net.bible.service.common.CommonUtils;
import net.bible.service.device.ScreenSettings;

import java.lang.reflect.Method;

import de.greenrobot.event.EventBus;

/** The WebView component that shows the main bible and commentary text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleView extends WebView implements DocumentView {
	
	private Window window;
	
	private BibleJavascriptInterface mJavascriptInterface;
	
	private VerseCalculator mVerseCalculator;

	private int mJumpToVerse = SharedConstants.NO_VALUE;
	private float mJumpToYOffsetRatio = SharedConstants.NO_VALUE;

	private boolean mIsVersePositionRecalcRequired = true;
	
	private PageTiltScroller mPageTiltScroller;
	private boolean hideScrollBar;
	
	private boolean wasAtRightEdge;
	private boolean wasAtLeftEdge;

	private PageControl mPageControl = ControlFactory.getInstance().getPageControl();
	
	private int maintainMovingVerse = -1;
	private static WindowControl windowControl = ControlFactory.getInstance().getWindowControl();
	
	private boolean kitKatPlus = CommonUtils.isKitKatPlus();
	
	// never go to 0 because a bug in Android prevents invalidate after loadDataWithBaseURL so no scrollOrJumpToVerse will occur 
	private static final int TOP_OF_SCREEN = 1;

	private static final String TAG = "BibleView";
	
	// remember current background colour so we know when it changes
	// -123 is not equal to WHITE or BLACK forcing first setting to be actioned
	private int mCurrentBackgroundColour = -123;
	
	/**
     * Constructor.  This version is only needed if you will be instantiating
     * the object manually (not from a layout XML file).
     * @param context
     */
	public BibleView(Context context, Window window) {
		super(context);
		this.window = window;
		initialise();
	}

	@SuppressLint("SetJavaScriptEnabled")
	private void initialise() {
		mVerseCalculator = new VerseCalculator(window);
		mJavascriptInterface = new BibleJavascriptInterface(mVerseCalculator);
		
		addJavascriptInterface(mJavascriptInterface, "jsInterface");

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
		EventBus.getDefault().register(this);
		
		// initialise split state related code - always screen1 is selected first
		onEvent(new CurrentWindowChangedEvent(windowControl.getActiveWindow()));
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
		int fontSize = mPageControl.getDocumentFontSize(window);
		getSettings().setDefaultFontSize(fontSize);

		// 1.6 is taken from css - line-height: 1.6em;
		ScreenSettings.setLineHeightDips((int) (1.6 * fontSize));
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
		Log.d(TAG, "Show(html," + jumpToVerse + "," + jumpToYOffsetRatio + ") Window:" + window);
		// set background colour if necessary
		changeBackgroundColour();
		
		// call this from here because some documents may require an adjusted font size e.g. those using Greek font
		applyFontSize();

		// scrollTo is used on kitkatplus but sometimes the later scrollTo was not working
		if (kitKatPlus && jumpToVerse>0) {
			html += "<script>scrollTo('" + jumpToVerse + "');</script>";
		} else {
			setJumpToVerse(jumpToVerse);
		}
		mJumpToYOffsetRatio = jumpToYOffsetRatio;

		// allow zooming if map
		enableZoomForMap(ControlFactory.getInstance().getCurrentPageControl().isMapShown());

		loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", "http://historyUrl");

		// ensure jumpToOffset is eventually called during initialisation.  It will normally be called automatically but sometimes is not i.e. after jump to verse 1 at top of screen then press back.
		// don't set this value too low or it may trigger before a proper upcoming computeVerticalScrollEvent
		// 100 was good for my Nexus 4 but 500 for my G1 - it would be good to get a reflection of processor speed and adjust appropriately
		invokeJumpToOffsetIfRequired(CommonUtils.isSlowDevice() ? 500 : 250);
	}

	/**
	 * Enable or disable zoom controls depending on whether map is currently shown
	 */
	@SuppressLint("NewApi")
	protected void enableZoomForMap(boolean isMap) {
		getSettings().setBuiltInZoomControls(true);
		getSettings().setSupportZoom(isMap);
		if (CommonUtils.isHoneycombPlus()) {
			// Could not totally remove the zoom controls after returning to a Bible view so never display them
			getSettings().setDisplayZoomControls(false);
		}
		// http://stackoverflow.com/questions/3808532/how-to-set-the-initial-zoom-width-for-a-webview
		getSettings().setLoadWithOverviewMode(isMap);
		getSettings().setUseWideViewPort(isMap);
	}

	/**
	 * This is called fairly late in initialisation so override to invoke jump to offset position
	 */
	@Override
	protected int computeVerticalScrollExtent() {
	    int result = super.computeVerticalScrollExtent();

	    // trigger jump to appropriate verse or offset into a book or commentary page...
		invokeJumpToOffsetIfRequired(0);

		return result;
	}

	/** 
	 * Trigger jump to correct offset
	 */
	private void invokeJumpToOffsetIfRequired(long delay) {
		if (mJumpToVerse!=SharedConstants.NO_VALUE || mJumpToYOffsetRatio!=SharedConstants.NO_VALUE) {
			postDelayed(new Runnable() {
				@Override
				public void run() {
				    jumpToOffset();
				}
			}, delay);
		}
	}
	
	private void jumpToOffset() {
		if (getContentHeight() > 0) {
			if (mIsVersePositionRecalcRequired) {
				mIsVersePositionRecalcRequired = false;
				executeJavascript("registerVersePositions()");
			}
			
			mJavascriptInterface.setNotificationsEnabled(windowControl.isActiveWindow(window));

			// screen is changing shape/size so constantly maintain the current verse position
			// main difference from jumpToVerse is that this is not cleared after jump
			if (maintainMovingVerse>0) {
				scrollOrJumpToVerse(maintainMovingVerse);
			}

			// go to any specified verse or offset
			if (mJumpToVerse!=SharedConstants.NO_VALUE) {
			    // must clear mJumpToVerse because setting location causes another onPageFinished
				int jumpToVerse = mJumpToVerse;
			    mJumpToVerse = SharedConstants.NO_VALUE;
			    
				scrollOrJumpToVerse(jumpToVerse);
				
			} else if (mJumpToYOffsetRatio!=SharedConstants.NO_VALUE) {
	            int contentHeight = getContentHeight(); 
	            int y = (int) ((float)contentHeight*mJumpToYOffsetRatio);
	            
	            // must zero mJumpToVerse because setting location causes another onPageFinished
				mJumpToYOffsetRatio = SharedConstants.NO_VALUE;
				
		        scrollTo(0, Math.max(y, TOP_OF_SCREEN));
			}
	    }
	}

	/** prevent swipe right if the user is scrolling the page right */
	public boolean isPageNextOkay() {
		boolean isOkay = true;
		if (window.getPageManager().isMapShown()) {
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
		if (window.getPageManager().isMapShown()) {
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
		Log.d(TAG, "Pausing tilt to scroll " + window);
        mPageTiltScroller.enableTiltScroll(false);
    }
    
    private void resumeTiltScroll() {
    	// but if multiple windows then only if the current active window
    	if (windowControl.isActiveWindow(window)) {
			Log.d(TAG, "Resuming tilt to scroll "+window);
	        mPageTiltScroller.enableTiltScroll(true);
    	}
    }
    
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		boolean handled = super.onTouchEvent(ev);

		windowControl.setActiveWindow(window);

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
			if (forward) {
				// scroll down/forward if not at bottom
				if (getScrollY()+1 < getMaxVerticalScroll()) {
					scrollBy(0, 1);
					ok = true;
				}
			} else {
				// scroll up/backward if not at top
				if (getScrollY() > TOP_OF_SCREEN) {
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

	public void onEvent(CurrentWindowChangedEvent event) {
		if (window.equals(event.getActiveWindow())) {
			mJavascriptInterface.setNotificationsEnabled(true);
			resumeTiltScroll();
		} else {
			mJavascriptInterface.setNotificationsEnabled(false);
			pauseTiltScroll();
		}
	}

	public void onEvent(UpdateSecondaryWindowEvent event) {
		if (window.equals(event.getUpdateScreen())) {
			changeBackgroundColour();
			show(event.getHtml(), event.getVerseNo(), SharedConstants.NO_VALUE);
		}		
	}

	public void onEvent(ScrollSecondaryWindowEvent event) {
		if (window.equals(event.getWindow()) && getHandler()!=null) {
			scrollOrJumpToVerseOnUIThread(event.getVerseNo());
		}
	}
	
	public void onEvent(WindowSizeChangedEvent event) {
		Log.d(TAG, "window size changed");
		boolean isScreenVerse = event.isVerseNoSet(window);
		if (isScreenVerse) {
			this.maintainMovingVerse = event.getVerseNo(window);
		}

		// when move finished the verse positions will have changed if in Landscape so recalc positions
		boolean isMoveFinished = event.isFinished();
		if (isMoveFinished && isScreenVerse) {
			final int verse = event.getVerseNo(window);
			setJumpToVerse(verse);
			
			if (getHandler()!=null) {
				getHandler().postDelayed(new Runnable() {
					@Override
					public void run() {
						// clear jump value if still set
						BibleView.this.maintainMovingVerse = SharedConstants.NO_VALUE;
						
						// ensure we are in the correct place after screen settles
						scrollOrJumpToVerse(verse);
						executeJavascript("registerVersePositions()");
					}
				} , WindowControl.SCREEN_SETTLE_TIME_MILLIS/2);
			}
		}		
	}
	
	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		Log.d(TAG, "Detached from window");
		// prevent random verse changes while layout is being rebuild because of window changes
		mJavascriptInterface.setNotificationsEnabled(false);
		pauseTiltScroll();
	}
	
	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		Log.d(TAG, "Attached to window");
		if (windowControl.isActiveWindow(window)) {
			mJavascriptInterface.setNotificationsEnabled(true);

			// may have returned from MyNote view
			resumeTiltScroll();
		}
	}

	public void onEvent(NumberOfWindowsChangedEvent event) {
		if (getVisibility()==View.VISIBLE && event.isVerseNoSet(window)) {
			setJumpToVerse(event.getVerseNo(window));
		}
	}

	public Window getWindowNo() {
		return window;
	}

	public void setVersePositionRecalcRequired(boolean mIsVersePositionRecalcRequired) {
		this.mIsVersePositionRecalcRequired = mIsVersePositionRecalcRequired;
	}
	
	public void setJumpToVerse(int verseNo) {
		this.mJumpToVerse = verseNo;
	}

	/** move the view so the selected verse is at the top or at least visible
	 */
	private void scrollOrJumpToVerseOnUIThread(final int verse) {
		getHandler().post(new Runnable() {
			
			@Override
			public void run() {
				scrollOrJumpToVerse(verse);
			}
		});
	}
	/** move the view so the selected verse is at the top or at least visible
	 */
	private void scrollOrJumpToVerse(final int verse) {
		Log.d(TAG, "Scroll or jump to:" + verse);
		if (verse==SharedConstants.NO_VALUE) {
			// NOOP
		} else if (verse<=1) {
			// use scroll to because difficult to place a tag exactly at the top
			scrollTo(0, TOP_OF_SCREEN);
		} else {
			// jump to correct verse
			// but scrollTop does not work on Android 3.0-4.0 and changing document location does not work on latest WebView  
			if (kitKatPlus) {
				// required format changed in 4.2 http://stackoverflow.com/questions/14771970/how-to-call-javascript-in-android-4-2
				executeJavascript("scrollTo('" + verse + "')");
			} else {
				executeJavascript("(function() { document.location = '#" + verse+"' })()");
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void executeJavascript(String javascript) {
		Log.d(TAG, "Executing JS:"+javascript);
		if (kitKatPlus) {
			evaluateJavascript(javascript+";", null);
		} else {
			loadUrl("javascript:"+javascript+";");
		}
	}
}
