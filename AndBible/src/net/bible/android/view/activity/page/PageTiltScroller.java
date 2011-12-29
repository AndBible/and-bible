package net.bible.android.view.activity.page;

import net.bible.android.control.ControlFactory;
import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.PageTiltScrollControl.TiltScrollInfo;
import android.os.Handler;

/** The WebView component that shows teh main bible and commentary text
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PageTiltScroller {

	private BibleView mWebView;
	
	private Handler mScrollHandler = new Handler();
	
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
				kickOffScrollHandler();
			} else {
				stopScrollHandler();
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
	private void kickOffScrollHandler() {
		TiltScrollInfo tiltScrollInfo = mPageTiltScrollControl.getTiltScrollInfo();
		mScrollHandler.postDelayed(mScrollTask, tiltScrollInfo.delayToNextScroll);
	}
	
	/** start scrolling handler
	 */
	private void stopScrollHandler() {
		mScrollHandler.removeCallbacks(mScrollTask);
	}

	/** cause content of attached WebView to scroll
	 */
	private Runnable mScrollTask = new Runnable() {
		public void run() {
			TiltScrollInfo tiltScrollInfo = mPageTiltScrollControl.getTiltScrollInfo();

			boolean scrolledOK = mWebView.scroll(tiltScrollInfo.forward, tiltScrollInfo.scrollPixels);

			if (mPageTiltScrollControl.isTiltScrollEnabled()) {
				int delay = scrolledOK ? tiltScrollInfo.delayToNextScroll : TiltScrollInfo.TIME_TO_POLL_WHEN_NOT_SCROLLING;
				mScrollHandler.postDelayed(mScrollTask, delay);
			}
		}
	};
}
