package net.bible.android.view.activity.page;

import java.lang.reflect.Method;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.PageControl;
import net.bible.android.view.activity.base.DocumentView;
import net.bible.service.device.ScreenSettings;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Picture;
import android.util.AttributeSet;
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
public class BibleView extends WebView implements DocumentView {
	
	private BibleJavascriptInterface mJavascriptInterface;
	
	private VerseCalculator mVerseCalculator;

	private int mJumpToVerse = 0;
	private float mJumpToYOffsetRatio = 0;
	
	private PageControl mPageControl = ControlFactory.getInstance().getPageControl();
	
	private PageTiltScroller mPageTiltScroller;

	private static final String TAG = "BibleView";
	
	// remember current background colour so we know when it changes
	// -123 is not equal to WHITE or BLACK forcing first setting to be actioned
	private int mCurrentBackgroundColour = -123;
	
	/**
     * Constructor.  This version is only needed if you will be instantiating
     * the object manually (not from a layout XML file).
     * @param context
     */
	public BibleView(Context context) {
		super(context);
		initialise();
	}

    /**
     * Construct object, initializing with any attributes we understand from a
     * layout file. These attributes are defined in
     * SDK/assets/res/any/classes.xml.
     * 
     * @see android.view.View#View(android.content.Context, android.util.AttributeSet)
     */
	public BibleView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialise();
	}
	public BibleView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialise();		
	}
	
	private void initialise() {
		mVerseCalculator = new VerseCalculator();
		mJavascriptInterface = new BibleJavascriptInterface(mVerseCalculator);
		
		addJavascriptInterface(mJavascriptInterface, "jsInterface");

		setPictureListener(new PictureListener() {
			/** this is called after the WebView page has finished loading and a new "picture" is on the webview.
			 */
			@Override
		    public void onNewPicture(WebView view, Picture arg1) {
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
				// TODO Auto-generated method stub
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
	}

	/** apply settings set by the user using Preferences
	 */
	@Override
	public void applyPreferenceSettings() {
		applyFontSize();
		
		changeBackgroundColour();
	}

	private void applyFontSize() {
		getSettings().setDefaultFontSize(mPageControl.getDocumentFontSize());
	}
	
	/** may need updating depending on environmental brightness
	 */
	@Override
	public boolean changeBackgroundColour() {
		// if night mode then set scrollbar colour
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
		Log.d(TAG, "Show(html,"+jumpToVerse+","+jumpToYOffsetRatio+")");
		// call this from here because some documents may require an adjusted font size e.g. those using Greek font
		applyFontSize();
		
		mJumpToVerse = jumpToVerse;
		mJumpToYOffsetRatio = jumpToYOffsetRatio;
		loadDataWithBaseURL("http://baseUrl", html, "text/html", "UTF-8", "http://historyUrl");
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		// TODO Auto-generated method stub
		super.onWindowFocusChanged(hasWindowFocus);

		if (hasWindowFocus) {
			resumeTiltScroll();
		} else {
			pauseTiltScroll();
		}
	}
	
    private void pauseTiltScroll() {
		Log.d(TAG, "Pausing tilt to scroll");
        mPageTiltScroller.enableTiltScroll(false);
    }
    
    private void resumeTiltScroll() {
		Log.d(TAG, "Resuming tilt to scroll");
        mPageTiltScroller.enableTiltScroll(true);
    }
    
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		boolean handled = super.onTouchEvent(ev);
		
		// Allow user to redefine viewing angle by touching screen
		mPageTiltScroller.recalculateViewingPosition();
		
		return handled;
	}

	/** enter text selection mode
	 */
	@Override
	public void selectAndCopyText() {
	    try {
	        Method m = WebView.class.getMethod("emulateShiftHeld", (Class[])null);
	        m.invoke(this, (Object[])null);
	    } catch (Exception e) {
	        e.printStackTrace();
	        // fallback
	        KeyEvent shiftPressEvent = new KeyEvent(0,0,
	             KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SHIFT_LEFT,0,0);
	        shiftPressEvent.dispatch(this);
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

	private boolean hideScrollBar;
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
    	
    	// get these once, they probably won't change 
        return computeVerticalScrollRange()-computeVerticalScrollExtent();
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
}
