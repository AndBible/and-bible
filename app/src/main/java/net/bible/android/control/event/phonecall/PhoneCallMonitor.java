package net.bible.android.control.event.phonecall;

import android.util.Log;
import net.bible.android.BibleApplication;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import net.bible.android.control.event.ABEventBus;

/**
 * Monitor phone calls to stop speech, etc 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *	  The copyright to this program is held by it's author.
 */
public class PhoneCallMonitor {
	
	private static boolean isMonitoring = false;
	// We need to keep reference to phoneStateListener. See
	// https://stackoverflow.com/questions/42213250/android-nougat-phonestatelistener-is-not-triggered
	private static PhoneStateListener phoneStateListener;
	
	public static void ensureMonitoringStarted() {
		if (!isMonitoring) {
			isMonitoring = true;
			new PhoneCallMonitor().startMonitoring();
		}
	}
	
	/** If phone rings then notify all PhoneCallEvent listeners.
	 * This was attempted in CurrentActivityHolder but failed if device was on
	 * stand-by and speaking and Android 4.4 (I think it worked on earlier versions of Android)
	 */
	private void startMonitoring() {
		Log.d("PhoneCallMonitor", "Starting monitoring");
		phoneStateListener = new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				Log.d("PhoneCallMonitor", "State changed " + state);
				if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
					ABEventBus.getDefault().post(new PhoneCallEvent(true));
				}
				else if(state == TelephonyManager.CALL_STATE_IDLE) {
					ABEventBus.getDefault().post(new PhoneCallEvent(false));
				}
			}
		};
		getTelephonyManager().listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	private TelephonyManager getTelephonyManager() {
		return (TelephonyManager)BibleApplication.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
	}

}
