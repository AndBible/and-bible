package net.bible.android.view.util.swipe;

import net.bible.android.BibleApplication;
import net.bible.service.common.CommonUtils;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Listen for side swipes to change chapter.  This listener class seems to work better than subclassing WebView.
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class SwipeGestureListener extends SimpleOnGestureListener {

    // measurements in dips for density independence
    private static final int DISTANCE_DIP = 40;
    private int scaledDistance;
    
    private int minScaledVelocity;
    private SwipeGestureEventHandler eventHandler;
    
    private static final String TAG = "SimpleGestureListener";
    
    public SwipeGestureListener(SwipeGestureEventHandler compareTranslationActivity) {
        super();
        this.eventHandler = compareTranslationActivity;
        scaledDistance = CommonUtils.convertDipsToPx(DISTANCE_DIP);
        minScaledVelocity = ViewConfiguration.get(BibleApplication.getApplication()).getScaledMinimumFlingVelocity();
        // make it easier to swipe
        minScaledVelocity = (int)(minScaledVelocity*0.66);
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
            // right to left swipe
            if (velocityX < 0 ) {
                eventHandler.onNext();
            }
            // left to right swipe
            else {
                eventHandler.onPrevious();
            }
            return true;
        }
        return false;
    }
}
