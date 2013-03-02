package net.bible.android.view.activity.page;

import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;

import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleGestureListener extends SimpleOnGestureListener {

	// measurements in dips for density independence
	// TODO: final int swipeMinDistance = vc.getScaledTouchSlop();
	// TODO: and other suggestions in http://stackoverflow.com/questions/937313/android-basic-gesture-detection
	private static final int DISTANCE_DIP = 40;
	private int scaledMinimumDistance;
	
	private int minScaledVelocity;
	private MainBibleActivity mainBibleActivity;
	
	private boolean sensePageDownTap;
	
	private static final String TAG = "BibleGestureListener";
	
	public BibleGestureListener(MainBibleActivity mainBibleActivity) {
		super();
		this.mainBibleActivity = mainBibleActivity;
		scaledMinimumDistance = CommonUtils.convertDipsToPx(DISTANCE_DIP);
    	minScaledVelocity = ViewConfiguration.get(mainBibleActivity).getScaledMinimumFlingVelocity();
    	// make it easier to swipe
    	minScaledVelocity = (int)(minScaledVelocity*0.66);
	}

	/** WebView does not handle long presses automatically via onCreateContextMenu so do it here
	 */
	@Override
	public void onLongPress(MotionEvent e) {
		Log.d(TAG, "onLongPress");
		super.onLongPress(e);
		
		// This seems to be required for Android 2.1 because the context menu of a WebView is not automatically displayed for 2.1
		// also do for 2.2 but not for 2.3+ as I can test that version
		// A user on 2.3.6 complained the context menu was no longer shown.  However, a user on '2.3.4 on an HTC EVO Shift' says it works great now
		// so it does not seem that we can simply use android version to determine if this forwarding is required
		if (!CommonUtils.isGingerBreadPlus()) {
			mainBibleActivity.openContextMenu();
		}
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		// prevent interference with splitter drag - fast drags were causing a fling
		if (!TouchOwner.getInstance().isTouchOwned()) {

			// get distance between points of the fling
			double vertical = Math.abs( e1.getY() - e2.getY() );
			double horizontal = Math.abs( e1.getX() - e2.getX() );
	
			Log.d(TAG, "onFling vertical:"+vertical+" horizontal:"+horizontal+" VelocityX"+velocityX);
			
			// test vertical distance, make sure it's a swipe
			if ( vertical > scaledMinimumDistance ) {
				 return false;
			}
			// test horizontal distance and velocity
			else if ( horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity ) {
				// right to left swipe - sometimes velocity seems to have wrong sign so use raw positions to determine direction  
				if (e1.getX() > e2.getX()) {
					mainBibleActivity.next();
				}
				// left to right swipe
				else {
					mainBibleActivity.previous();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		Log.d(TAG, "onDoubleTap");
		mainBibleActivity.toggleFullScreen();
		return true;
	}

// conflicts with maximize button in split screens - could add && !split.isSplit && !split.isMinimized 
//	but I don't think many people use this so remove in next release if no grumbles
//	@Override
//	public boolean onSingleTapConfirmed(MotionEvent e) {
//		boolean handled=false;
//		if (sensePageDownTap) {
//			Log.d(TAG, "onSingleTapConfirmed ");
//		    WindowManager window = (WindowManager)BibleApplication.getApplication().getSystemService(Context.WINDOW_SERVICE); 
//		    Display display = window.getDefaultDisplay();
//		    int height = display.getHeight();
//		    
//		    if (e.getY()>height*0.93) {
//				Log.d(TAG, "scrolling down");
//				mainBibleActivity.scrollScreenDown();
//				handled = true;
//		    }
//			Log.d(TAG, "finished onSingleTapConfirmed ");
//		}
//		if (!handled) {
//	    	handled = super.onSingleTapConfirmed(e);
//
//		}
//	    return handled;
//	}

	public void setSensePageDownTap(boolean sensePageDownTap) {
		this.sensePageDownTap = sensePageDownTap;
	}
}
