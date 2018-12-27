/*
 * Copyright (c) 2018 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package net.bible.android.control.page;

import java.util.ArrayList;
import java.util.List;

import net.bible.android.BibleApplication;
import net.bible.service.common.CommonUtils;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/** Manage the logic behind tilt-to-scroll
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 */
//Tilt-scroll is disabled on 2.1/ only enabled on 2.2+
@TargetApi(Build.VERSION_CODES.FROYO)
public class PageTiltScrollControl {

	// must be null initially
	private static Boolean mIsOrientationSensor = null;
	private boolean mIsTiltScrollEnabled = false;

	// the pitch at which a user views the text stationary
	// this changes dynamically when the screen is touched
	// both angles are degrees
	private int mNoScrollViewingPitch = -38;
	private boolean mNoScrollViewingPitchCalculated = false;
	private boolean mSensorsTriggered = false;
	private int mLastNormalisedPitchValue;
	
	private static final int NO_SCROLL_VIEWING_TOLERANCE = 2; //3;
	private static final int NO_SPEED_INCREASE_VIEWING_TOLERANCE = 2;
	private static final int INVALID_STATE = -9999;
	
	// this is decreased (subtracted from) to speed up scrolling
	private static final int BASE_TIME_BETWEEN_SCROLLS = 48; //70(jerky) 40((fast);

	private static final int MIN_TIME_BETWEEN_SCROLLS = 4;
	
	/**
	 * Time between scroll = Periodic time = 1/frequency
	 * Scroll speed = frequency*wavelength // wavelength = 1 pixel so can ignore wavelength
	 * => speed = 1/Time between each scroll event 
	 * If we use regular changes in periodic time then initial changes in tilt have little affect on speed 
	 * but when tilt is greater small changes in tilt cause large changes in speed
	 * Therefore the following mTimeBetweenScrollListEvery5Degrees is used to even out speed changes
	 * 
	 * This was my starting spreadsheet from which the below array was derived.  
	 * The spreadsheet starts with regular changes in speed and calculates the required Periodic time
	 * degrees	speed	Periodic time (ms)
		0		0.02	50
		5		0.04	25
		10		0.06	16.6666666666667
		15		0.08	12.5
		20		0.1		10
		25		0.12	8.33333333333333
		30		0.14	7.14285714285714
		35		0.16	6.25
		40		0.18	5.55555555555556
		45		0.2	5
	 */
	private static int MIN_DEGREES_OFFSET = 0;
	private static int MAX_DEGREES_OFFSET = 45;
	private static float MIN_SPEED = 0.02f;
	private static float MAX_SPEED = 0.2f;
	// calculated to ensure even speed up of scrolling
	private static Integer[] mTimeBetweenScrolls;

	// current pitch of phone - varies dynamically
	private float[] mOrientationValues;
	private int mRotation = Surface.ROTATION_0;

	// needed to find if screen switches to landscape and must different sensor value
	private Display mDisplay;
	
	public static final String TILT_TO_SCROLL_PREFERENCE_KEY = "tilt_to_scroll_pref";

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
	// should not need more than one because the request come in one at a time
	private TiltScrollInfo tiltScrollInfoSingleton = new TiltScrollInfo();
	
	public PageTiltScrollControl() {
		initialiseTiltSpeedPeriods();
	}
	
	public TiltScrollInfo getTiltScrollInfo() {
		TiltScrollInfo tiltScrollInfo = tiltScrollInfoSingleton.reset();
		int delayToNextScroll = BASE_TIME_BETWEEN_SCROLLS;
		if (mOrientationValues!=null) {
			int normalisedPitch = getNormalisedPitch(mRotation, mOrientationValues);
			if (normalisedPitch!=INVALID_STATE) {
				int devianceFromViewingAngle = getDevianceFromStaticViewingAngle(normalisedPitch);
				
				if (devianceFromViewingAngle > NO_SCROLL_VIEWING_TOLERANCE) {
					tiltScrollInfo.forward = normalisedPitch < mNoScrollViewingPitch;
	
					// speedUp if tilt screen beyond a certain amount
					if (tiltScrollInfo.forward) {
						delayToNextScroll = getDelayToNextScroll(devianceFromViewingAngle-NO_SCROLL_VIEWING_TOLERANCE-NO_SPEED_INCREASE_VIEWING_TOLERANCE-1);
	
						// speedup could be done by increasing scroll amount but that leads to a jumpy screen
						tiltScrollInfo.scrollPixels = 1;
					} else {
						// TURNED OFF UPSCROLL
						delayToNextScroll = BASE_TIME_BETWEEN_SCROLLS;
						tiltScrollInfo.scrollPixels = 0;
					}
				}
			}
		}
		if (mIsTiltScrollEnabled) {
			tiltScrollInfo.delayToNextScroll = Math.max(MIN_TIME_BETWEEN_SCROLLS, delayToNextScroll);
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
		mNoScrollViewingPitchCalculated = false;
		mSensorsTriggered = false;
	}

	/** if screen rotates must switch between different values returned by orientation sensor
	 */
	private int getNormalisedPitch(int rotation, float[] orientationValues) {
		float pitch = 0;
		
		// occasionally the viewing position was being unexpectedly reset to zero - avoid by checking for the problematic state  
		if (rotation==0 && 
			orientationValues[1]==0.0f && 
			orientationValues[2]==0.0f) {
			if (!mNoScrollViewingPitchCalculated) {
				return INVALID_STATE;
			} else {
				return mLastNormalisedPitchValue;
			}
		}
		
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
		default:
			Log.e(TAG, "Invalid Scroll rotation:"+rotation);
		}
		
		int normalisedPitch = Math.round(pitch); 
		mLastNormalisedPitchValue = normalisedPitch;
		return normalisedPitch;
	}
	
	/** find angle between no-scroll-angle and current pitch
	 */
	private int getDevianceFromStaticViewingAngle(int normalisedPitch) {
	
		if (!mNoScrollViewingPitchCalculated) {
//			Log.d(TAG, "Recalculating home/noscroll pitch "+normalisedPitch);

			// assume user's viewing pitch is the current one
			mNoScrollViewingPitch = normalisedPitch;
			// pitch can be 0 before the sensors have fired
			if (mSensorsTriggered) {
				mNoScrollViewingPitchCalculated = true;
			}
		}
		
		return Math.abs(normalisedPitch-mNoScrollViewingPitch);
	}

	/** Get delay between scrolls for specified tilt 
	 * 
	 *  negative tilts will return min delay
	 *  0-num elts in mTimeBetweenScrolls array will return associated period from array
	 *  larger tilts will return max period from array
	 *  
	 * @param tilt
	 * @return
	 */
	private int getDelayToNextScroll(int tilt) {
		// speed changes with every degree of tilt
		// ensure we have a positive number
		tilt = Math.max(tilt, 0);
		if (tilt < mTimeBetweenScrolls.length) {
			return mTimeBetweenScrolls[tilt];
		} else {
			return mTimeBetweenScrolls[mTimeBetweenScrolls.length-1];
		}
	}
	
	/**
	 * Orientation monitor (see Professional Android 2 App Dev Meier pg 469)
	 */
	
	private void connectListeners() {
		mDisplay = ((WindowManager) BibleApplication.Companion.getApplication().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		
		SensorManager sm = (SensorManager) BibleApplication.Companion.getApplication().getSystemService(Context.SENSOR_SERVICE);
		Sensor oSensor = sm.getDefaultSensor(Sensor.TYPE_ORIENTATION);

		sm.registerListener(myOrientationListener, oSensor, SensorManager.SENSOR_DELAY_UI);
	}
    private void disconnectListeners() {
    	try {
			SensorManager sm = (SensorManager) BibleApplication.Companion.getApplication().getSystemService(Context.SENSOR_SERVICE);
	    	sm.unregisterListener(myOrientationListener);
    	} catch (IllegalArgumentException e) {
    		// Prevent occasional: IllegalArgumentException: Receiver not registered: android.hardware.SystemSensorManager
    		// If not registered then there is no need to unregister
    		Log.w(TAG, "Error disconnecting sensor listener", e);
    		
    	}
    }

	final SensorEventListener myOrientationListener = new SensorEventListener() {
		public void onSensorChanged(SensorEvent sensorEvent) {
			if (sensorEvent.sensor.getType() == Sensor.TYPE_ORIENTATION) {
				mOrientationValues = sensorEvent.values;
				mRotation = mDisplay.getRotation();
				mSensorsTriggered = true;
			}
		}

		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
	};

	/** return true if both a sensor and android support are available to sense device tilt
	 */
	public static boolean isTiltSensingPossible() {
		return 	isOrientationSensor();
	}
	
    /**
     * Returns true if at least one Orientation sensor is available
     */
    public static boolean isOrientationSensor() {
        if (mIsOrientationSensor == null) {
       		SensorManager sm = (SensorManager) BibleApplication.Companion.getApplication().getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) {
                List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ORIENTATION);
                mIsOrientationSensor = Boolean.valueOf(sensors.size() > 0);
            } else {
                mIsOrientationSensor = Boolean.FALSE;
            }
        }
        return mIsOrientationSensor;
    }

	public boolean isTiltScrollEnabled() {
		return mIsTiltScrollEnabled;
	}
	
	/** map degrees tilt to time between 1 pixel scrolls to save time at runtime 
	 */
	private void initialiseTiltSpeedPeriods() {
		float degreeRange = MAX_DEGREES_OFFSET - MIN_DEGREES_OFFSET;
		float speedRange = MAX_SPEED - MIN_SPEED;
		
		List<Integer> delayPeriods = new ArrayList<Integer>();
		for (int deg=MIN_DEGREES_OFFSET; deg<=MAX_DEGREES_OFFSET; deg++) {
			float speed = MIN_SPEED+((deg/degreeRange)*speedRange);
			float period = 1/speed;
			delayPeriods.add(Math.round(period));
		}
		
		mTimeBetweenScrolls = delayPeriods.toArray(new Integer[delayPeriods.size()]);
	}
}
