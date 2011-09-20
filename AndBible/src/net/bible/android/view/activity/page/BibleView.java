package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.PageControl;
import net.bible.service.common.CommonUtils;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Picture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
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
public class BibleView extends WebView {
	
	private BibleJavascriptInterface javascriptInterface;
	
	private VerseCalculator verseCalculator;

	private int mJumpToVerse = 0;
	private float mJumpToYOffsetRatio = 0;
	
	private PageControl pageControl = ControlFactory.getInstance().getPageControl();
	
	private static final String TAG = "BibleView";
	
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
		verseCalculator = new VerseCalculator();
		javascriptInterface = new BibleJavascriptInterface(verseCalculator);
		
		addJavascriptInterface(javascriptInterface, "jsInterface");

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
	}

	/** apply settings set by the user using Preferences
	 */
	public void applyPreferenceSettings() {
		applyFontSize();
	}
	
	private void applyFontSize() {
		getSettings().setDefaultFontSize(pageControl.getDocumentFontSize());
	}
	
	/** show a page from bible commentary
	 * 
	 * @param html
	 */
	public void show(String html, int jumpToVerse, float jumpToYOffsetRatio) {
		Log.d(TAG, "Show(html,"+jumpToVerse+","+jumpToYOffsetRatio+")");
		applyFontSize();
		
		mJumpToVerse = jumpToVerse;
		mJumpToYOffsetRatio = jumpToYOffsetRatio;
		loadDataWithBaseURL("http://baseUrl", html, "text/html", "UTF-8", "http://historyUrl");
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
}
