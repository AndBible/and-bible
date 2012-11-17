package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.PageTiltScrollControl.TiltScrollInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/** The WebView component that shows teh main bible and commentary text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PageTiltScroller {

	private static final String FORWARD_KEY = "Forward";

	private static final String SCROLL_PIXELS_KEY = "ScrollPixels";

	private BibleView mWebView;
	
	private Thread mScrollTriggerThread;
	private boolean mIsScrolling;
	private Handler mScrollHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			Bundle b = msg.getData();
			int scrollPixels = b.getInt(SCROLL_PIXELS_KEY, 1);
			boolean forward = b.getBoolean(FORWARD_KEY, true);

			mIsScrolling = mWebView.scroll(forward, scrollPixels);
		}
	};	
	private PageTiltScrollControl mPageTiltScrollControl = ControlFactory.getInstance().getPageTiltScrollControl();
	
	@SuppressWarnings("unused")
	private static final String TAG = "PageTiltScroller";
	
	public PageTiltScroller(BibleView webView) {
		this.mWebView = webView;
	}
	
	/** start or stop tilt to scroll functionality
	 */
	public void enableTiltScroll(boolean enable) {
		if (mPageTiltScrollControl.enableTiltScroll(enable)) {
			if (enable) {
				kickOffScrollThread();
			} else {
				stopScrollThread();
			}
		}
	}

	/** called when user touches screen to reset home position
	 */
	public void recalculateViewingPosition() {
		mPageTiltScrollControl.recalculateViewingPosition();
	}
	/** 
	 * Scroll screen at a certain speed
	 */

	/** start scrolling handler
	 */
	private void kickOffScrollThread() {
		if (mScrollTriggerThread==null) {
			mScrollTrigger.enable();
			mScrollTriggerThread = new Thread(mScrollTrigger);
			mScrollTriggerThread.start();
		}
	}
	
	/** start scrolling handler
	 */
	private void stopScrollThread() {
		if (mScrollTriggerThread!=null) {
			mScrollTrigger.stop();
			mScrollTriggerThread = null;
		}
	}

	private ScrollTrigger mScrollTrigger = new ScrollTrigger();
	class ScrollTrigger implements Runnable {
		private boolean isContinue = true;
		
		void enable() {
			isContinue = true;
		}
		void stop() {
			isContinue = false;
		}

		@Override
		public void run() {
			while (isContinue) {
				try {
					TiltScrollInfo tiltScrollInfo = mPageTiltScrollControl.getTiltScrollInfo();

					if (tiltScrollInfo.scrollPixels!=0) {
						Message msg = new Message();
						Bundle b = new Bundle();
						b.putInt(SCROLL_PIXELS_KEY, tiltScrollInfo.scrollPixels);
						b.putBoolean(FORWARD_KEY, tiltScrollInfo.forward);
						msg.setData(b);
						mScrollHandler.sendMessageAtFrontOfQueue(msg);
					}

					if (mPageTiltScrollControl.isTiltScrollEnabled()) {
						long delay = mIsScrolling ? tiltScrollInfo.delayToNextScroll : TiltScrollInfo.TIME_TO_POLL_WHEN_NOT_SCROLLING;
					    Thread.sleep(delay);        
					} else {
						isContinue = false;
					}
			     } catch (Exception e) {
			      Log.v("Error", e.toString());
			     }
				
			}
		}
	}
}
