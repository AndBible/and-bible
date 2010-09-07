package net.bible.android.view;

import net.bible.android.CurrentPassage;
import android.content.Context;
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
		    	
		    	if (mJumpToVerse != -1) { 
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
			public void onLoadResource(WebView view, String url) {
				System.out.println("**Onloadresource:"+url);
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
					if (xChangeSinceLastEvent < -0.3) {
						Log.d(TAG, "<.3");
						if (event.getEventTime()-lastHandledTrackballEventTime>1000) {
							Log.i(TAG, "Move Back");
							CurrentPassage.getInstance().previous();
							lastHandledTrackballEventTime = event.getEventTime();
						} else {
							Log.i(TAG, "Already handled");
						}
						isHandled = true;
					} else if (xChangeSinceLastEvent > 0.3) {
						Log.d(TAG, ">.3");
						if (event.getEventTime()-lastHandledTrackballEventTime>1000) {
							Log.i(TAG, "Move Forward");
							CurrentPassage.getInstance().next();
							lastHandledTrackballEventTime = event.getEventTime();
						} else {
							Log.i(TAG, "Already handled");
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
}
