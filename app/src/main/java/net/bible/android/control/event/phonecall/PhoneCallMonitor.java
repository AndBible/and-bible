/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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
		return (TelephonyManager)BibleApplication.Companion.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
	}

}
