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
package net.bible.service.device.speak

import android.util.Log
import net.bible.service.common.CommonUtils.settings

/**
 * The speed and progress of TTS Speech
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SpeakTiming {
    private var lastUtteranceId: String? = null
    private var lastSpeakTextLength = 0
    private var lastSpeakStartTime: Long = 0
    private var cpms = DEFAULT_CPMS.toDouble()

    init {
        loadCpms()
        Log.i(TAG, "Average Speak CPMS:$cpms")
    }

    fun started(utteranceId: String, speakTextLength: Int) {
        Log.i(TAG, "Speak timer started")
        lastUtteranceId = utteranceId
        lastSpeakTextLength = speakTextLength
        lastSpeakStartTime = System.currentTimeMillis()
    }

    /** a block of text is finished being read so update char/msec if necessary
     */
    fun finished(utteranceId: String) {
        Log.i(TAG, "Speak timer stopped")
        if (utteranceId == lastUtteranceId) {
            val timeTaken = milliSecsSinceStart()
            // ignore short text strings as they can be way off in cps e.g. passage header (e.g. Job 39 v7)  has lots of 1 char numbers that take a long time to say
            if (timeTaken > SHORT_TEXT_LIMIT_MSEC) {
                // calculate characters per millisecond
                val latestCpms = lastSpeakTextLength.toFloat() / milliSecsSinceStart()
                updateAverageCpms(latestCpms)
                Log.i(TAG, "CPmS:" + cpms + " CPS:" + cpms * 1000.0)
            }
            lastUtteranceId = null
            lastSpeakStartTime = 0
            lastSpeakTextLength = 0
        }
    }

    /** estimate how much of the last string sent to TTS has been spoken
     */
    val fractionCompleted: Double
        get() {
            var fractionCompleted = 1.0
            if (cpms > 0 && lastSpeakTextLength > 0) {
                fractionCompleted =
                    milliSecsSinceStart().toFloat() / (lastSpeakTextLength.toFloat() / cpms)
                Log.i(TAG, "Fraction completed:$fractionCompleted")
            } else {
                Log.e(TAG, "SpeakTiming- Cpms:$cpms lastSpeakTextLength:$lastSpeakTextLength")
            }
            return fractionCompleted
        }

    /** estimate how much of the last string sent to TTS has been spoken
     */
    fun getCharsInSecs(secs: Int): Long {
        return (cpms * (1000.0 * secs)).toLong()
    }

    /** estimate how long it will take to speak so many chars
     */
    fun getSecsForChars(chars: Long): Long {
        return Math.round(1.0 * chars / cpms / 1000.0)
    }

    private fun milliSecsSinceStart(): Long {
        val duration = System.currentTimeMillis() - lastSpeakStartTime
        Log.i(TAG, "Duration:$duration")
        return duration
    }

    private fun updateAverageCpms(lastCpms: Float) {
        // take the average of historical figures and the new figure to attempt to lessen the affect of weird text but aadjust for different types of text 
        cpms = (cpms + lastCpms) / 2.0f
        saveCpms()
    }

    private fun loadCpms() {
        cpms = settings.getDouble(SPEAK_CPMS_KEY, DEFAULT_CPMS.toDouble())
    }

    private fun saveCpms() {
        settings.setDouble(SPEAK_CPMS_KEY, cpms)
    }

    companion object {
        private const val DEFAULT_CPMS = 0.016f
        private const val SPEAK_CPMS_KEY = "SpeakCPMS"
        private const val SHORT_TEXT_LIMIT_MSEC = 20000
        private const val TAG = "Speak"
    }
}
