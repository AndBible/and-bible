package net.bible.service.device.speak;

import net.bible.service.common.CommonUtils;
import android.util.Log;

public class SpeakTiming {
	
	private String lastUtteranceId;
	private int lastSpeakTextLength;
	private long lastSpeakStartTime;
	
	private float cpms = DEFAULT_CPMS;
	private static final float DEFAULT_CPMS = 0.016f;
	private static final String SPEAK_CPMS_KEY = "SpeakCPMS";
	
	private static final int SHORT_TEXT_LIMIT_MSEC = 20000;
	private static final String TAG = "Speak";
	
	public SpeakTiming() {
		loadCpms();
		Log.d(TAG, "Average Speak CPMS:"+cpms);
	}

	public void started(String utteranceId, int speakTextLength) {
		Log.d(TAG, "Speak timer started");
		lastUtteranceId = utteranceId;
		lastSpeakTextLength = speakTextLength;
		lastSpeakStartTime = System.currentTimeMillis();
	}
	
	/** a block of text is finished being read so update char/msec if necessary
	 */
	public void finished(String utteranceId) {
		Log.d(TAG, "Speak timer stopped");
		if (utteranceId.equals(lastUtteranceId)) {
			long timeTaken = milliSecsSinceStart();
			// ignore short text strings as they can be way off in cps e.g. passage header (e.g. Job 39 v7)  has lots of 1 char numbers that take a long time to say
			if (timeTaken>SHORT_TEXT_LIMIT_MSEC) {
				// calculate characters per millisecond
				float latestCpms = ((float)lastSpeakTextLength)/milliSecsSinceStart();
				updateAverageCpms(latestCpms);
				Log.d(TAG, "CPmS:"+cpms+" CPS:"+cpms*1000.0);
			}
			lastUtteranceId = null;
			lastSpeakStartTime = 0;
			lastSpeakTextLength = 0;
		}
	}
	
	/** estimate how much of the last string sent to TTS has been spoken 
	 */
	public float getFractionCompleted() {
		float fractionCompleted = 1;
		if (cpms >0 && lastSpeakTextLength>0) {
			fractionCompleted = (float)milliSecsSinceStart()/((float)lastSpeakTextLength/cpms);
			Log.d(TAG, "Fraction completed:"+fractionCompleted);
		} else {
			Log.e(TAG, "SpeakTiming- Cpms:"+cpms+" lastSpeakTextLength:"+lastSpeakTextLength);
		}
		return fractionCompleted;
	}

	/** estimate how much of the last string sent to TTS has been spoken 
	 */
	public long getCharsInSecs(int secs) {
		return (long)(cpms*(1000.0*secs));
	}
	/** estimate how long it will take to speak so many chars 
	 */
	public long getSecsForChars(long chars) {
		return Math.round(((1.0*chars)/cpms)/1000.0);
	}

	private long milliSecsSinceStart(){
		long duration = System.currentTimeMillis() - lastSpeakStartTime;
		Log.d(TAG, "Duration:"+duration);
		return duration;
	}
	
	private void updateAverageCpms(float lastCpms) {
		// take the average of historical figures and the new figure to attempt to lessen the affect of weird text but aadjust for different types of text 
		cpms = (cpms+lastCpms)/2.0f;
		saveCpms();
	}
	
	private void loadCpms() {
		cpms = CommonUtils.getSharedPreferences().getFloat(SPEAK_CPMS_KEY, DEFAULT_CPMS);
	}
	private void saveCpms() {
		CommonUtils.getSharedPreferences().edit().putFloat(SPEAK_CPMS_KEY, cpms).commit();
	}
}
