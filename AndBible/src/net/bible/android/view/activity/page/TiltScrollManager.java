package net.bible.android.view.activity.page;

import java.util.List;

import net.bible.android.BibleApplication;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.webkit.WebView;

public class TiltScrollManager {

	private WebView mWebView;
	
	private Boolean mIsOrientationSensor;
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
	private float[] mOrientationValues;
	private int mTempPrevPitch;
	private int mRotation = Surface.ROTATION_0;

	// needed to find if screen switches to landscape and must different sensor value
	private Display mDisplay;
	
	private static final String TAG = "TiltScrollManager";
	
	public TiltScrollManager(WebView webView) {
		this.mWebView = webView;
	}
	
	/** start or stop tilt to scroll functionality
	 */
	public void enableTiltScroll(boolean enable) {
		if (mIsTiltScrollEnabled != enable && isOrientationSupported()) {
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
	
			if (mOrientationValues!=null) {
				int normalisedPitch = getPitch(mRotation, mOrientationValues);
				if (normalisedPitch!=mTempPrevPitch) {
					Log.d(TAG, "Pitch:" + Math.round(normalisedPitch));
				}
				
				if (!mNoScrollViewingPitchCalculated) {
					// assume user's viewing pitch is the current one
					mNoScrollViewingPitch = normalisedPitch;
					mNoScrollViewingPitchCalculated = true;
				}
				
				int speedUp = 0;
				int devianceFromViewingAngle = Math.abs(normalisedPitch-mNoScrollViewingPitch);
				if (devianceFromViewingAngle > NO_SCROLL_VIEWING_TOLERANCE) {
	
					// speedUp if tilt screen beyond a certain amount
					speedUp = Math.max(0, devianceFromViewingAngle-NO_SCROLL_VIEWING_TOLERANCE-NO_SPEED_INCREASE_VIEWING_TOLERANCE);
	
					// speedup is initially done by decreasing time between scrolls and then by increasing scroll amount
					int scrollAmount = 1+speedUp;
					
					boolean isTiltedForward = normalisedPitch<mNoScrollViewingPitch; 
					// TODO - do not allow scroll off end
					if (isTiltedForward) {
						// scroll down/forward
						mWebView.scrollBy(0, scrollAmount);
					} else if (mWebView.getScrollY() > 0) {
						// scroll up/back
						mWebView.scrollBy(0, -scrollAmount);
					}
					
				}
				mTempPrevPitch = normalisedPitch; 
			}
			if (mIsTiltScrollEnabled) {
				mScrollHandler.postDelayed(mScrollTask, BASE_TIME_BETWEEN_SCROLLS);
			}
		}
	};

	private int getPitch(int rotation, float[] orientationValues) {
		float pitch = 0;
		switch (rotation) {
		//Portrait for Nexus
		case Surface.ROTATION_0:
			pitch = orientationValues[1];
			break;
		//Landscape for Nexus
		case Surface.ROTATION_90:
			pitch = -orientationValues[2];
			break;
		case Surface.ROTATION_270:
			pitch = orientationValues[2];
			break;
		case Surface.ROTATION_180:
			pitch = -orientationValues[1];
			break;
		}
		return Math.round(pitch);
	}
	/**
	 * Orientation monitor (see Professional Android 2 App Dev Meier pg 469)
	 */
	
	private void connectListeners() {
		mDisplay = ((WindowManager) BibleApplication.getApplication().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		
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
				mOrientationValues = sensorEvent.values;
				mRotation = mDisplay.getRotation();
			}
		}

		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};
	
    /**
     * Returns true if at least one Orientation sensor is available
     */
    public boolean isOrientationSupported() {
        if (mIsOrientationSensor == null) {
       		SensorManager sm = (SensorManager) BibleApplication.getApplication().getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) {
                List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ORIENTATION);
                mIsOrientationSensor = new Boolean(sensors.size() > 0);
            } else {
                mIsOrientationSensor = Boolean.FALSE;
            }
        }
        return mIsOrientationSensor;
    }
}
