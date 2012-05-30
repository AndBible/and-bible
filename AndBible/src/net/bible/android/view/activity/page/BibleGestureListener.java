package net.bible.android.view.activity.page;

import net.bible.android.BibleApplication;
import net.bible.service.common.CommonUtils;
import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleGestureListener extends SimpleOnGestureListener {

	// measurements in dips for density independence
	private static final int DISTANCE_DIP = 40;
	private int scaledDistance;
	
	private int minScaledVelocity;
	private MainBibleActivity mainBibleActivity;
	
	private boolean sensePageDownTap;
	
	private static final String TAG = "BibleGestureListener";
	
	public BibleGestureListener(MainBibleActivity mainBibleActivity) {
		super();
		this.mainBibleActivity = mainBibleActivity;
		scaledDistance = CommonUtils.convertDipsToPx(DISTANCE_DIP);
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
		
    	// The MyNote triggers it's own context menu which causes 2 to be displayed
    	// I have also seen 2 displayed in normal view 
    	// Avoid 2 by preventing display twice within 1.5 seconds
		if (!mainBibleActivity.isContextMenuRecentlyCreated()) {
			// This seems to be required for Android 2.1 because the context menu of a WebView is not automatically displayed for 2.1
			mainBibleActivity.openContextMenu();
		}
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		// get distance between points of the fling
		double vertical = Math.abs( e1.getY() - e2.getY() );
		double horizontal = Math.abs( e1.getX() - e2.getX() );

		Log.d(TAG, "onFling vertical:"+vertical+" horizontal:"+horizontal+" VelocityX"+velocityX);
		
		// test vertical distance, make sure it's a swipe
		if ( vertical > scaledDistance ) {
			 return false;
		}
		// test horizontal distance and velocity
		else if ( horizontal > scaledDistance && Math.abs(velocityX) > minScaledVelocity ) {
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
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		Log.d(TAG, "onDoubleTap");
		mainBibleActivity.toggleFullScreen();
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		boolean handled=false;
		if (sensePageDownTap) {
			Log.d(TAG, "onSingleTapConfirmed ");
		    WindowManager window = (WindowManager)BibleApplication.getApplication().getSystemService(Context.WINDOW_SERVICE); 
		    Display display = window.getDefaultDisplay();
		    int height = display.getHeight();
		    
		    if (e.getY()>height*0.93) {
				Log.d(TAG, "scrolling down");
				mainBibleActivity.scrollScreenDown();
				handled = true;
		    }
			Log.d(TAG, "finished onSingleTapConfirmed ");
		}
		if (!handled) {
	    	handled = super.onSingleTapConfirmed(e);

		}
	    return handled;
	}

	public void setSensePageDownTap(boolean sensePageDownTap) {
		this.sensePageDownTap = sensePageDownTap;
	}
}
