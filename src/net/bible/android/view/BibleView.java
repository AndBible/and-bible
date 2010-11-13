package net.bible.android.view;

import net.bible.android.BibleApplication;
import net.bible.android.activity.R;
import net.bible.android.control.page.CurrentPageManager;
import net.bible.android.view.util.UiUtils;
import net.bible.service.common.Constants;

import org.crosswire.jsword.book.Book;
import org.crosswire.jsword.book.Defaults;
import org.crosswire.jsword.passage.Key;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
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
	
	private long lastHandledTrackballEventTime = 0;


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
		SharedPreferences preferences = UiUtils.getSharedPreferences();
		// see this bug (http://code.google.com/p/android/issues/detail?id=2096) for the reason we can't just use an integer-array in values.xml
		int fontSize = Integer.valueOf(preferences.getString("text_size_pref", "16"));
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
	public void flingScroll(int vx, int vy) {
		// TODO Auto-generated method stub
		super.flingScroll(vx, vy);
		
		Log.d(TAG, "flingScroll vx:"+vx+" vy:"+vy);
	}
	
	/** handle right/left trackpad movement by going next/prev page
	 */
	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		Log.d(TAG, "onTrackBallEvent:"+event+" down:"+event.getDownTime()+" time:"+event.getEventTime());
		boolean isHandled = false;
		try {
			if (event.getAction() == MotionEvent.ACTION_MOVE) {
				// The relative movement of the trackball since the last event can be retrieve with MotionEvent.getX() and MotionEvent.getY(). These are normalized so that a movement of 1 corresponds to the user pressing one DPAD key (so they will often be fractional values, representing the more fine-grained movement information available from a trackball).
				float xChangeSinceLastEvent = event.getX();
				float yChangeSinceLastEvent = event.getY();
				boolean xMovement = Math.abs(xChangeSinceLastEvent) > Math.abs(yChangeSinceLastEvent);
				Log.d(TAG, "XMov:"+xMovement+" xch:"+xChangeSinceLastEvent+" ych:"+yChangeSinceLastEvent);
				if (xMovement) {
					Log.d(TAG, "is xmov");
					if (Math.abs(xChangeSinceLastEvent)>0.4) {
						Log.d(TAG, "large enough movement for page change:"+xChangeSinceLastEvent);
						if (event.getEventTime()-lastHandledTrackballEventTime>1000) {
							Log.d(TAG, "Changing page");
							if (xChangeSinceLastEvent>0) {
								CurrentPageManager.getInstance().getCurrentPage().next();
							} else {
								CurrentPageManager.getInstance().getCurrentPage().previous();
							}
							lastHandledTrackballEventTime = event.getEventTime();
						} else {
							Log.d(TAG, "Trackball scroll too soon - ignoring");
						}
						isHandled = true;
					}
				}
			}
			if (!isHandled) {
				isHandled = super.onTrackballEvent(event);
			}
		} catch (Exception e) {
			Log.e(TAG, "Error changing page", e);
		}
		return isHandled;
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
	        	BibleApplication.getApplication().showErrorMessage(R.string.strongs_not_installed);
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
