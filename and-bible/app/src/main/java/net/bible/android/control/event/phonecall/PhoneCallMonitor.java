package net.bible.android.control.event.phonecall;

import net.bible.android.BibleApplication;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import de.greenrobot.event.EventBus;

/**
 * Monitor phone calls to stop speech, etc 
 * 
 * @author Martin Denham [mjdenham at gmail dot com]
 * @see gnu.lgpl.License for license details.<br>
 *      The copyright to this program is held by it's author.
 */
public class PhoneCallMonitor {
	
	private static boolean isMonitoring = false;
	
	public static void ensureMonitoringStarted() {
		if (!isMonitoring) {
			isMonitoring = true;
			new PhoneCallMonitor().startMonitoring();
		}
	}
	
	/** If phone rings then notify all appToBackground listeners.
	 * This was attempted in CurrentActivityHolder but failed if device was on stand-by and speaking and Android 4.4 (I think it worked on earlier versions of Android)
	 */
	private void startMonitoring() {
		getTelephonyManager().listen(new PhoneStateListener() {
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				if (state==TelephonyManager.CALL_STATE_RINGING || state==TelephonyManager.CALL_STATE_OFFHOOK) {
					EventBus.getDefault().post(new PhoneCallStarted());
				}
			}
			
		}, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	private TelephonyManager getTelephonyManager() {
		return (TelephonyManager)BibleApplication.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
	}

}
