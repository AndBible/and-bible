/*
 * Copyright (c) 2020-2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * Utility class for managing and querying TTS voices
 */
class VoiceManager {
    
    data class VoiceInfo(
        val name: String,
        val displayName: String,
        val locale: Locale,
        val quality: Int,
        val isNetworkConnectionRequired: Boolean
    )

    /**
     * Get all available voices for a specific language
     */
    fun getAvailableVoicesForLanguage(tts: TextToSpeech?, languageCode: String): List<VoiceInfo> {
        if (tts == null) return emptyList()
        
        return try {
            val voices = tts.voices ?: return emptyList()
            voices.filter { voice ->
                voice.locale.language.equals(languageCode, ignoreCase = true)
            }.map { voice ->
                VoiceInfo(
                    name = voice.name,
                    displayName = formatVoiceDisplayName(voice),
                    locale = voice.locale,
                    quality = voice.quality,
                    isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                )
            }.sortedWith(
                compareBy<VoiceInfo> { it.isNetworkConnectionRequired }  // Local voices first
                    .thenByDescending { it.quality }  // Higher quality first
                    .thenBy { it.displayName }  // Alphabetical by display name
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting voices for language $languageCode", e)
            emptyList()
        }
    }

    /**
     * Get all available voices grouped by language
     */
    fun getAllAvailableVoicesGroupedByLanguage(tts: TextToSpeech?): Map<String, List<VoiceInfo>> {
        if (tts == null) return emptyMap()
        
        return try {
            val voices = tts.voices ?: return emptyMap()
            voices.map { voice ->
                VoiceInfo(
                    name = voice.name,
                    displayName = formatVoiceDisplayName(voice),
                    locale = voice.locale,
                    quality = voice.quality,
                    isNetworkConnectionRequired = voice.isNetworkConnectionRequired
                )
            }.groupBy { it.locale.language }
            .mapValues { (_, voiceList) ->
                voiceList.sortedWith(
                    compareBy<VoiceInfo> { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.displayName }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all voices", e)
            emptyMap()
        }
    }

    /**
     * Set a specific voice by name
     */
    fun setVoiceByName(tts: TextToSpeech?, voiceName: String): Boolean {
        if (tts == null) return false
        
        return try {
            val voices = tts.voices ?: return false
            val voice = voices.find { it.name == voiceName }
            if (voice != null) {
                val result = tts.setVoice(voice)
                result == TextToSpeech.SUCCESS
            } else {
                Log.w(TAG, "Voice not found: $voiceName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting voice $voiceName", e)
            false
        }
    }

    /**
     * Format a voice name for display to users
     */
    private fun formatVoiceDisplayName(voice: Voice): String {
        val baseName = voice.name
            .replace("com.google.android.tts:", "")
            .replace("com.samsung.android.tts:", "")
            .replace("#neural", "")
            .replace("#high", "")
            .replace("#medium", "")
            .replace("#low", "")
            .replace("-#", " ")
            .replace("_", " ")
        
        val qualityIndicator = when (voice.quality) {
            Voice.QUALITY_VERY_HIGH -> " (Very High Quality)"
            Voice.QUALITY_HIGH -> " (High Quality)"
            Voice.QUALITY_NORMAL -> ""
            Voice.QUALITY_LOW -> " (Low Quality)"
            Voice.QUALITY_VERY_LOW -> " (Very Low Quality)"
            else -> ""
        }
        
        val networkIndicator = if (voice.isNetworkConnectionRequired) " (Online)" else ""
        
        return baseName + qualityIndicator + networkIndicator
    }

    companion object {
        private const val TAG = "VoiceManager"
    }
}