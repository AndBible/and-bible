package net.bible.android.view.activity.page;

import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.activity.base.Dialogs;
import net.bible.service.common.CommonUtils;
import net.bible.service.common.Constants;
import net.bible.service.history.HistoryManager;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
	
	// prevent too may scroll events causing multi-page changes
	private long lastHandledDpadEventTime = 0;

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
		verseCalculator = new VerseCalculator(this);
		javascriptInterface = new BibleJavascriptInterface(verseCalculator);
		
		addJavascriptInterface(javascriptInterface, "jsInterface");
		
		/* WebViewClient must be set BEFORE calling loadUrl! */  
		setWebViewClient(new WebViewClient() {  
		    @Override  
		    public void onPageFinished(WebView view, String url)  
		    {
		    	super.onPageFinished(view, url);
		    	
		    	if (mJumpToVerse > 0) { 
		    		Log.d(TAG, "Jumping to verse "+mJumpToVerse);
		    		if (mJumpToVerse==1) {
		    			// use scroll to becasue difficult to place a tag exactly at the top
		    			view.scrollTo(0,0);
		    		} else {
		    			view.loadUrl("javascript:location.href='#"+mJumpToVerse+"'");
		    		}
		    	    // must zero mJumpToVerse because setting location causes another onPageFinished
		    	    mJumpToVerse = -1; 
		    	 } 
		    }

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				// load Strongs refs when a user clicks on a link
				if (loadApplicationUrl(url)) {
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
		SharedPreferences preferences = CommonUtils.getSharedPreferences();
		// see this bug (http://code.google.com/p/android/issues/detail?id=2096) for the reason we can't just use an integer-array in values.xml
		int fontSize = preferences.getInt("text_size_pref", 16);
		getSettings().setDefaultFontSize(fontSize);
	}
	
	/** show a page from bible commentary
	 * 
	 * @param html
	 */
	public void show(String html, int jumpToVerse) {
		Log.d(TAG, "Show(html,"+jumpToVerse+")");
		mJumpToVerse = jumpToVerse;
		loadDataWithBaseURL("http://baseUrl", html, "text/html", "UTF-8", "http://historyUrl");
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, "Keycode:"+keyCode);
		//TODO allow DPAD_LEFT to always change page and navigation between links using dpad
		// placing BibleKeyHandler second means that DPAD left is unable to move to prev page if strongs refs are shown
		// vice-versa (webview second) means right & left can not be used to navigate between Strongs links

		// common key handling i.e. KEYCODE_DPAD_RIGHT & KEYCODE_DPAD_LEFT to change chapter
		if (BibleKeyHandler.getInstance().onKeyDown(keyCode, event)) {
			return true;
		}
		
		// allow movement from link to link in current page
		return super.onKeyDown(keyCode, event);
	}
    
	/** Currently the only uris handled are for Strongs refs
	 * see OSISToHtmlSaxHandler.getStrongsUrl for format of uri
	 * 
	 * @param url
	 * @return true if successfully changed to Strongs ref
	 */
	private boolean loadApplicationUrl(String uri) {
		try {
			Log.d(TAG, "Loading: "+uri);
			// check for urls like gdef:01234 
			if (!uri.contains(":")) {
				return false;
			}
			String[] uriTokens = uri.split(":");
	        String protocol = uriTokens[0];
	        String ref = uriTokens[1];
	
	        // hebrew or greek
	        Book book = null;
	        if (Constants.GREEK_DEF_PROTOCOL.equals(protocol)) {
	        	book = Defaults.getGreekDefinitions();
	        } else if (Constants.HEBREW_DEF_PROTOCOL.equals(protocol)) {
	        	book = Defaults.getHebrewDefinitions();
	        } else {
	        	// not a valid Strongs Uri
	        	return false;
	        }
		        
	        // valid Strongs uri but Strongs refs not installed
	        if (book==null) {
	        	Dialogs.getInstance().showErrorMsg(R.string.strongs_not_installed);
	        	// this uri request was handled by showing an error message so return true
	        	return true;
	        }
	
	        Key strongsNumberKey = book.getKey(ref); 
	   		CurrentPageManager.getInstance().setCurrentDocumentAndKey(book, strongsNumberKey);
	
			return true;
		} catch (Exception e) {
			Log.e(TAG, "Error going to Strongs", e);
			return false;
		}
	}
}
