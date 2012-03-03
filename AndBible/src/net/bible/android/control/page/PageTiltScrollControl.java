package net.bible.android.control.page;

import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.service.common.CommonUtils;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/** Manage the logic behind tilt-to-scroll
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PageTiltScrollControl {

	// must be null initially
	private Boolean mIsOrientationSensor = null;
	private boolean mIsTiltScrollEnabled = false;

	// the pitch at which a user views the text stationary
	// this changes dynamically when the screen is touched
	// both angles are degrees
	private int mNoScrollViewingPitch = -38;
	private boolean mNoScrollViewingPitchCalculated = false;
	
	private static final int NO_SCROLL_VIEWING_TOLERANCE = 2; //3;
	private static final int NO_SPEED_INCREASE_VIEWING_TOLERANCE = 0; //6;
	
	// this is decreased (subtracted from) to speed up scrolling
	private static final int BASE_TIME_BETWEEN_SCROLLS = 60; //70(jerky) 40((fast);

	private static final int MIN_TIME_BETWEEN_SCROLLS = 4;
	
	private static final int SPEEDUP_MULTIPLIER = 4; 
	
	// current pitch of phone - varies dynamically
	private float[] mOrientationValues;
	private int mRotation = Surface.ROTATION_0;

	// needed to find if screen switches to landscape and must different sensor value
	private Display mDisplay;
	
	public static final String TILT_TO_SCROLL_PREFERENCE_KEY = "tilt_to_scroll_pref";

	@SuppressWarnings("unused")
	private static final String TAG = "TiltScrollControl";
	
	public static class TiltScrollInfo {
		public int scrollPixels;
		public boolean forward;
		public int delayToNextScroll;

		public static int TIME_TO_POLL_WHEN_NOT_SCROLLING = 500;
		
		private TiltScrollInfo reset() {
			scrollPixels = 0;
			forward = true;
			delayToNextScroll = TIME_TO_POLL_WHEN_NOT_SCROLLING;
			return this;
		}
	}
	// should not need more than one because teh request come in one at a time
	private TiltScrollInfo tiltScrollInfoSingleton = new TiltScrollInfo();
	
	public TiltScrollInfo getTiltScrollInfo() {
		TiltScrollInfo tiltScrollInfo = tiltScrollInfoSingleton.reset();
		int speedUp = 0;
		if (mOrientationValues!=null) {
			int normalisedPitch = getPitch(mRotation, mOrientationValues);
			int devianceFromViewingAngle = getDevianceFromStaticViewingAngle(normalisedPitch);
			
			if (devianceFromViewingAngle > NO_SCROLL_VIEWING_TOLERANCE) {
				tiltScrollInfo.forward = normalisedPitch < mNoScrollViewingPitch;

				// speedUp if tilt screen beyond a certain amount
				if (tiltScrollInfo.forward) {
					speedUp = Math.max(0, devianceFromViewingAngle-NO_SCROLL_VIEWING_TOLERANCE-NO_SPEED_INCREASE_VIEWING_TOLERANCE);

					// speedup could be done by increasing scroll amount but that leads to a jumpy screen
					tiltScrollInfo.scrollPixels = 1;
				} else {
					// TURNED OFF UPSCROLL
					speedUp = 0;
					tiltScrollInfo.scrollPixels = 0;
				}
			}
		}
		if (mIsTiltScrollEnabled) {
			tiltScrollInfo.delayToNextScroll = Math.max(MIN_TIME_BETWEEN_SCROLLS, BASE_TIME_BETWEEN_SCROLLS-(SPEEDUP_MULTIPLIER*speedUp));
		}
		return tiltScrollInfo;
	}
	
	/** start or stop tilt to scroll functionality
	 */
	public boolean enableTiltScroll(boolean enable) {
		// Android 2.1 does not have Display.getRotation so disable tilt-scroll for 2.1 
		if (!CommonUtils.getSharedPreferences().getBoolean(TILT_TO_SCROLL_PREFERENCE_KEY, false) || 
			!isTiltSensingPossible()) {
			return false;
		} else if (mIsTiltScrollEnabled != enable) {
			mIsTiltScrollEnabled = enable;
			if (enable) {
				connectListeners();
			} else {
				disconnectListeners();
			}
			return true;
		} else {
			return false;
		}
	}

	/** called when user touches screen to reset home position
	 */
	public void recalculateViewingPosition() {
		//TODO save to settings
		mNoScrollViewingPitchCalculated = false;
	}

	/** if screen rotates must switch between different values returned by orientation sensor
	 */
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
	
	/** find angle between no-scroll-angle and current pitch
	 */
	private int getDevianceFromStaticViewingAngle(int normalisedPitch) {
	
		if (!mNoScrollViewingPitchCalculated) {
			// assume user's viewing pitch is the current one
			mNoScrollViewingPitch = normalisedPitch;
			mNoScrollViewingPitchCalculated = true;
		}
		
		return Math.abs(normalisedPitch-mNoScrollViewingPitch);
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

	/** return true if both a sensor and android support are available to sense device tilt
	 */
	public boolean isTiltSensingPossible() {
		return 	isOrientationSensor() &&
				CommonUtils.isFroyoPlus();
	}
	
    /**
     * Returns true if at least one Orientation sensor is available
     */
    public boolean isOrientationSensor() {
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

	public boolean isTiltScrollEnabled() {
		return mIsTiltScrollEnabled;
	}
}
