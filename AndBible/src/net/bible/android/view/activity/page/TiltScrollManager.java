package net.bible.android.view.activity.page;

import net.bible.android.BibleApplication;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebView;

public class TiltScrollManager {

	private WebView mWebView;
	
	private boolean mIsTiltScrollEnabled;

	private Handler mScrollHandler = new Handler();
	
	// the pitch at which a user views the text stationary
	// this changes dynamically when the screen is touched
	// both angles are degrees
	private int mNoScrollViewingPitch = -38;
	private boolean mNoScrollViewingPitchCalculated = false;
	
	private static final int NO_SCROLL_VIEWING_TOLERANCE = 4;
	private static final int NO_SPEED_INCREASE_VIEWING_TOLERANCE = 7;
	
	// this is decreased (subtracted from) to speed up scrolling
	private static int BASE_TIME_BETWEEN_SCROLLS = 40;
	
	// current pitch of phone - varies dynamically
	private int mPitch = mNoScrollViewingPitch;
	private int mTempPrevPitch;
	
	private static final String TAG = "TiltScrollManager";
	
	public TiltScrollManager(WebView webView) {
		this.mWebView = webView;
	}
	
	/** start or stop tilt to scroll functionality
	 */
	public void enableTiltScroll(boolean enable) {
		if (mIsTiltScrollEnabled != enable) {
			mIsTiltScrollEnabled = enable;
			if (enable) {
				connectListeners();
				kickOffScrollHandler();
			} else {
				disconnectListeners();
			}
		}
	}

	/** called when user touches screen to reset home position
	 */
	public void recalculateViewingPosition() {
		//TODO save to settings
		mNoScrollViewingPitchCalculated = false;
	}
	/** 
	 * Scroll screen at a certain speed
	 */

	/** start scrolling handler
	 */
	private void kickOffScrollHandler() {
       mScrollHandler.postDelayed(mScrollTask, BASE_TIME_BETWEEN_SCROLLS);
	}
	
	/** cause content of attached WebView to scroll
	 */
	private Runnable mScrollTask = new Runnable() {
		public void run() {
			if (mPitch!=mTempPrevPitch) {
				Log.d(TAG, "Pitch:" + Math.round(mPitch));
			}
			
			if (!mNoScrollViewingPitchCalculated) {
				// assume user's viewing pitch is the current one
				mNoScrollViewingPitch = mPitch;
				mNoScrollViewingPitchCalculated = true;
			}
			
			int speedUp = 0;
			int devianceFromViewingAngle = Math.abs(mPitch-mNoScrollViewingPitch);
			if (devianceFromViewingAngle > NO_SCROLL_VIEWING_TOLERANCE) {

				// speedUp if tilt screen beyond a certain amount
				speedUp = Math.max(0, devianceFromViewingAngle-NO_SCROLL_VIEWING_TOLERANCE-NO_SPEED_INCREASE_VIEWING_TOLERANCE);

				// speedup is initially done by decreasing time between scrolls and then by increasing scroll amount
				int scrollAmount = 1+speedUp;
				
				boolean isTiltedForward = mPitch<mNoScrollViewingPitch; 
				// TODO - do not allow scroll off end
				if (isTiltedForward) {
					// scroll down/forward
					mWebView.scrollBy(0, scrollAmount);
				} else if (mWebView.getScrollY() > 0) {
					// scroll up/back
					mWebView.scrollBy(0, -scrollAmount);
				}
				
			}

			if (mIsTiltScrollEnabled) {
				mScrollHandler.postDelayed(mScrollTask, BASE_TIME_BETWEEN_SCROLLS);
			}
			mTempPrevPitch = mPitch; 
		}
	};

	/**
	 * Orientation monitor (see Professional Android 2 App Dev Meier pg 469)
	 */
	
	private void connectListeners() {
		SensorManager sm = (SensorManager) BibleApplication.getApplication().getSystemService(Context.SENSOR_SERVICE);
		Sensor oSensor = sm.getDefaultSensor(Sensor.TYPE_ORIENTATION);

		sm.registerListener(myOrientationListener, oSensor, SensorManager.SENSOR_DELAY_UI);
	}
    private void disconnectListeners() {
		SensorManager sm = (SensorManager) BibleApplication.getApplication().getSystemService(Context.SENSOR_SERVICE);
    	sm.unregisterListener(myOrientationListener);
    }

	final SensorEventListener myOrientationListener = new SensorEventListener() {
		public void onSensorChanged(SensorEvent sensorEvent) {
			if (sensorEvent.sensor.getType() == Sensor.TYPE_ORIENTATION) {
				float[] orientationValues = sensorEvent.values;
				mPitch = Math.round(orientationValues[1]);
			}
		}

		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};
}
