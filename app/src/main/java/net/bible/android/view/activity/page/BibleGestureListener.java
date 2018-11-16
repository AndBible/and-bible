package net.bible.android.view.activity.page;

import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import net.bible.android.view.util.TouchOwner;
import net.bible.service.common.CommonUtils;

/** Listen for side swipes to change chapter.  This listener class seems to work better that subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class BibleGestureListener extends SimpleOnGestureListener {

	// measurements in dips for density independence
	// TODO: final int swipeMinDistance = vc.getScaledTouchSlop();
	// TODO: and other suggestions in http://stackoverflow.com/questions/937313/android-basic-gesture-detection
	private static final int DISTANCE_DIP = 40;
	private int scaledMinimumDistance;
	
	private int minScaledVelocity;
	private MainBibleActivity mainBibleActivity;
	
	private static final String TAG = "BibleGestureListener";
	
	public BibleGestureListener(MainBibleActivity mainBibleActivity) {
		super();
		this.mainBibleActivity = mainBibleActivity;
		scaledMinimumDistance = CommonUtils.convertDipsToPx(DISTANCE_DIP);
		minScaledVelocity = ViewConfiguration.get(mainBibleActivity).getScaledMinimumFlingVelocity();
		// make it easier to swipe
		minScaledVelocity = (int)(minScaledVelocity*0.66);
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		// prevent interference with window separator drag - fast drags were causing a fling
		if (!TouchOwner.getInstance().isTouchOwned()) {
			// avoid NPE on Samsung devices
			if (e1!=null && e2!=null) {
				// get distance between points of the fling
				double vertical = Math.abs(e1.getY() - e2.getY());
				double horizontal = Math.abs(e1.getX() - e2.getX());

				Log.d(TAG, "onFling vertical:" + vertical + " horizontal:" + horizontal + " VelocityX" + velocityX);

				// test vertical distance, make sure it's a swipe
				if (vertical > scaledMinimumDistance) {
					return false;
				}
				// test horizontal distance and velocity
				else if (horizontal > scaledMinimumDistance && Math.abs(velocityX) > minScaledVelocity) {
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
		}
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		Log.d(TAG, "onDoubleTap");
		
		mainBibleActivity.toggleFullScreen();
		return true;
	}
}
