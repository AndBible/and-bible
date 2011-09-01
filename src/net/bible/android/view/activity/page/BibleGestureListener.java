package net.bible.android.view.activity.page;

import net.bible.android.control.page.CurrentPageManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.GestureDetector.SimpleOnGestureListener;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class BibleGestureListener extends SimpleOnGestureListener {

	// measurements in dips for density independence
	private static final float DISTANCE_DIP = 40.0f;
	private int scaledDistance;
	
	private int minScaledVelocity;
	private MainBibleActivity mainBibleActivity;
	
	private static final String TAG = "BibleGestureListener";
	
	public BibleGestureListener(MainBibleActivity mainBibleActivity) {
		super();
		this.mainBibleActivity = mainBibleActivity;
		// convert dip measurements to pixels
		final float scale = mainBibleActivity.getResources().getDisplayMetrics().density;
		scaledDistance = (int) ( DISTANCE_DIP * scale + 0.5f );
    	minScaledVelocity = ViewConfiguration.get(mainBibleActivity).getScaledMinimumFlingVelocity();
    	// make it easier to swipe
    	minScaledVelocity = (int)(minScaledVelocity*0.66);
	}

	/** WebView does not handle long presses automatically via onCreateContextMenu so do it here
	 */
	@Override
	public void onLongPress(MotionEvent e) {
		super.onLongPress(e);
		Log.d(TAG, "onLongPress");
		mainBibleActivity.openContextMenu();
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		
		// get distance between points of the fling
		double vertical = Math.abs( e1.getY() - e2.getY() );
		double horizontal = Math.abs( e1.getX() - e2.getX() );
		
		// test vertical distance, make sure it's a swipe
		if ( vertical > scaledDistance ) {
			 return false;
		}
		// test horizontal distance and velocity
		else if ( horizontal > scaledDistance && Math.abs(velocityX) > minScaledVelocity ) {
			// right to left swipe
			if (velocityX < 0 ) {
				CurrentPageManager.getInstance().getCurrentPage().next();
			}
			// left to right swipe
			else {
				CurrentPageManager.getInstance().getCurrentPage().previous();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		Log.d(TAG, "*** onDoubleTap");
		mainBibleActivity.toggleFullScreen();
		return super.onDoubleTap(e);
	}
}
