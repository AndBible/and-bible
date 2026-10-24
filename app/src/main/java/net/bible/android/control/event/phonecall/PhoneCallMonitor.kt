/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.control.event.phonecall

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import net.bible.sharedcore.log.Log
import net.bible.android.BibleApplication.Companion.application

const val TAG = "PhoneCallMonitor"

/**
 * Monitor phone calls to stop speech, etc
 */

object PhoneCallMonitor {
    private var isMonitoring = false
    private var onCallStateChanged: ((Boolean) -> Unit)? = null

    /** If phone rings then tell the TTS manager.
     * This was attempted in CurrentActivityHolder but failed if device was on
     * stand-by and speaking and Android 4.4 (I think it worked on earlier versions of Android)
     */
    private fun startMonitoringLegacy() {
        Log.i("PhoneCallMonitor", "Starting monitoring")
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                Log.i("PhoneCallMonitor", "State changed $state")
                callActivating(state)?.let { onCallStateChanged?.invoke(it) }
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private val telephonyManager: TelephonyManager
        get() = application.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // We need to keep reference to phoneStateListener. See
    // https://stackoverflow.com/questions/42213250/android-nougat-phonestatelistener-is-not-triggered
    private var phoneStateListener: PhoneStateListener? = null

    fun ensureMonitoringStarted(onCallStateChanged: (Boolean) -> Unit) {
        this.onCallStateChanged = onCallStateChanged
        Log.i(TAG, "ensureMonitoringStarted ${Build.VERSION.SDK_INT}")
        if (!isMonitoring) {
            // From API 26 onwards, we use audio focus change listening (see startSpeaking)
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isMonitoring = true
                startMonitoringLegacy()
            }
        }
    }

    /** Ringing or off-hook → true, idle → false, anything else → null (ignored). */
    internal fun callActivating(state: Int): Boolean? = when (state) {
        TelephonyManager.CALL_STATE_RINGING, TelephonyManager.CALL_STATE_OFFHOOK -> true
        TelephonyManager.CALL_STATE_IDLE -> false
        else -> null
    }
}
