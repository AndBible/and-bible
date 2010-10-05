package net.bible.android.view;

import net.bible.android.CurrentPassage;
import android.content.Context;
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
public class BibleSwipeListener extends SimpleOnGestureListener {

	// measurements in dips for density independence
	private static final float DISTANCE_DIP = 40.0f;
	private int scaledDistance;
	
	private int minScaledVelocity;
	private Context context;
	
	private static final String TAG = "BibleSwipeListener";
	
	public BibleSwipeListener(Context context) {
		super();
		this.context = context;
		// convert dip measurements to pixels
		final float scale = context.getResources().getDisplayMetrics().density;
		scaledDistance = (int) ( DISTANCE_DIP * scale + 0.5f );
    	minScaledVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
    	// make it easier to swipe
    	minScaledVelocity = (int)(minScaledVelocity*0.66);
	}

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
				CurrentPassage.getInstance().next();
			}
			// left to right swipe
			else {
				CurrentPassage.getInstance().previous();
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		Log.d(TAG, "On swipeListener scroll");
		// TODO Auto-generated method stub
		return super.onScroll(e1, e2, distanceX, distanceY);
	}
	
	
}
