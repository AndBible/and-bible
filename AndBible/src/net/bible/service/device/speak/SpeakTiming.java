package net.bible.service.device.speak;

import android.util.Log;

public class SpeakTiming {
	
	private String lastUtteranceId;
	private int lastSpeakTextLength;
	private long lastSpeakStartTime;
	private static float cpms = 0.016F;
	
	private static final int SHORT_TEXT_LIMIT_MSEC = 20000;
	private static final String TAG = "SpeakTiming";
	
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
				cpms = ((float)lastSpeakTextLength)/milliSecsSinceStart();
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
		}
		return fractionCompleted;
	}

	private long milliSecsSinceStart(){
		long duration = System.currentTimeMillis() - lastSpeakStartTime;
		Log.d(TAG, "Duration:"+duration);
		return duration;
	}
}
