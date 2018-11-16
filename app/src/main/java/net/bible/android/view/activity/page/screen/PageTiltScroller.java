package net.bible.android.view.activity.page.screen;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import net.bible.android.control.page.PageTiltScrollControl;
import net.bible.android.control.page.PageTiltScrollControl.TiltScrollInfo;
import net.bible.android.view.activity.page.BibleView;

import java.lang.ref.WeakReference;

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

    private final PageTiltScrollControl pageTiltScrollControl;
    
    private Thread mScrollTriggerThread;
    private boolean mIsScrolling;
    private Handler mScrollMsgHandler = new ScrollMsgHandler(this);

    private static final String TAG = "PageTiltScroller";
    
    public PageTiltScroller(BibleView webView, PageTiltScrollControl pageTiltScrollControl) {
        this.mWebView = webView;
        this.pageTiltScrollControl = pageTiltScrollControl;
    }
    
    /** start or stop tilt to scroll functionality
     */
    public void enableTiltScroll(boolean enable) {
        if (pageTiltScrollControl.enableTiltScroll(enable)) {
            if (enable) {
                recalculateViewingPosition();
                kickOffScrollThread();
            } else {
                stopScrollThread();
            }
        }
    }

    /** called when user touches screen to reset home position
     */
    public void recalculateViewingPosition() {
        pageTiltScrollControl.recalculateViewingPosition();
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
    private class ScrollTrigger implements Runnable {
        private boolean isContinue = true;
        
        void enable() {
            isContinue = true;
        }
        void stop() {
            isContinue = false;
        }

        @Override
        public void run() {
            Log.d(TAG, "Tilt-Scroll loop starting");
            while (isContinue) {
                try {
                    TiltScrollInfo tiltScrollInfo = pageTiltScrollControl.getTiltScrollInfo();

                    if (tiltScrollInfo.scrollPixels!=0) {
                        Message msg = new Message();
                        Bundle b = new Bundle();
                        b.putInt(SCROLL_PIXELS_KEY, tiltScrollInfo.scrollPixels);
                        b.putBoolean(FORWARD_KEY, tiltScrollInfo.forward);
                        msg.setData(b);
                        mScrollMsgHandler.sendMessageAtFrontOfQueue(msg);
                    }

                    if (pageTiltScrollControl.isTiltScrollEnabled()) {
                        long delay = mIsScrolling ? tiltScrollInfo.delayToNextScroll : TiltScrollInfo.TIME_TO_POLL_WHEN_NOT_SCROLLING;
                        Thread.sleep(delay);
                    } else {
                        isContinue = false;
                    }
                 } catch (Exception e) {
                     Log.v("Error", e.toString());
                     isContinue = false;
                 }
            }
            Log.d(TAG, "Tilt-Scroll loop exiting");
        }
    }

    /** handle message requesting the bible view be scrolled up one pixel
     */
    private static class ScrollMsgHandler extends Handler {
        // avoid potential memory leak.  See http://stackoverflow.com/questions/11407943/this-handler-class-should-be-static-or-leaks-might-occur-incominghandler
        private final WeakReference<PageTiltScroller> pageTiltScrollerRef;
        ScrollMsgHandler(PageTiltScroller pageTiltScroller) {
            this.pageTiltScrollerRef = new WeakReference<>(pageTiltScroller);
        }
        
        /** scroll the window 1 pixel up
         */
        @Override
        public void handleMessage(Message msg) {
            Bundle b = msg.getData();
            int scrollPixels = b.getInt(SCROLL_PIXELS_KEY, 1);
            boolean forward = b.getBoolean(FORWARD_KEY, true);

            PageTiltScroller pageTiltScroller = pageTiltScrollerRef.get();
            if (pageTiltScroller!=null) {
                pageTiltScroller.mIsScrolling = pageTiltScroller.mWebView.scroll(forward, scrollPixels);
            }
        }
    }

}
