/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.sharedlogic.platform.ios

import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.control.speak.SpeakSetupProblem
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.speak.AudioFocus
import net.bible.sharedcore.platform.speak.IosSpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosSpeakPlatformTest {
    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = (m[key] as String?) ?: default
        override fun getLong(key: String, default: Long) = (m[key] as Long?) ?: default
        override fun getInt(key: String, default: Int) = (m[key] as Int?) ?: default
        override fun getBoolean(key: String, default: Boolean) = (m[key] as Boolean?) ?: default
        override fun getDouble(key: String, default: Double) = (m[key] as Double?) ?: default
        override fun getFloat(key: String, default: Float) = (m[key] as Float?) ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    private class RecordingNotifier : UserNotifier {
        val errors = mutableListOf<String>()
        override fun showInfo(message: String, long: Boolean) = Unit
        override fun showError(message: String, cause: Throwable?) { errors += message }
    }

    private class FakeFocus : AudioFocus {
        val calls = mutableListOf<String>()
        override fun requestAudioFocus(onFocusChange: (Boolean) -> Unit): Boolean { calls += "request"; return false }
        override fun abandonAudioFocus() { calls += "abandon" }
        override fun startPhoneCallMonitoring(onCallStateChanged: (Boolean) -> Unit) { calls += "calls" }
    }

    private val listener = object : SpeakEngineListener {
        override fun onInit(success: Boolean) = Unit
        override fun onUtteranceStart(utteranceId: String) = Unit
        override fun onUtteranceDone(utteranceId: String) = Unit
        override fun onUtteranceError(utteranceId: String) = Unit
    }
    private val settings = MapSettings()
    private val notifier = RecordingNotifier()
    private val focus = FakeFocus()
    private val requested = mutableListOf<String>()

    private fun platform(now: () -> Long = { 0L }) = IosSpeakPlatform(
        audioFocus = focus,
        phrases = { tag -> requested += tag; SpeakPhrases("first-$tag", "2", "3", "4", "5", "book", "chapter") },
        problemText = { "problem:$it" },
        notifier = notifier,
        settings = settings,
        earconFiles = emptyMap(),
        now = now,
    )

    @Test fun constantsOfTheIosEngine() {
        val p = platform()
        assertEquals(4000, p.maxSpeechInputLength)
        assertNull(p.preferredEnginePackage)
    }

    @Test fun createEngineReturnsAnIosSpeakEngine() {
        val engine = platform().createEngine(listener)
        try { assertTrue(engine is IosSpeakEngine) } finally { engine.shutdown() }
    }

    @Test fun phrasesAreLookedUpPerLanguageTag() {
        val p = platform()
        assertEquals("first-fi", p.phrasesFor("fi").first)
        assertEquals("first-en", p.phrasesFor("en").first)
        assertEquals(listOf("fi", "en"), requested)
    }

    @Test fun everyProblemReachesTheNotifierWithItsOwnText() {
        val p = platform()
        SpeakSetupProblem.entries.forEach(p::reportProblem)
        assertEquals(SpeakSetupProblem.entries.map { "problem:$it" }, notifier.errors)
    }

    @Test fun preparingToSpeakStampsSpeakLastUsed() {
        platform(now = { 1_234L }).onPrepareForSpeaking()
        assertEquals(1_234L, settings.getLong("speak-last-used", -1))
    }

    @Test fun focusCallsDelegateAndCallMonitoringIsDelegatedToo() {
        val p = platform()
        assertFalse(p.requestAudioFocus { })
        p.abandonAudioFocus()
        p.startPhoneCallMonitoring { }
        assertEquals(listOf("request", "abandon", "calls"), focus.calls)
    }

    @Test fun earconFilesPairTheFiveEngineNamesWithTheBundledWavs() {
        assertEquals(
            mapOf(
                "[pre-footnote]" to "/b/earcons/short_pling.wav",
                "[post-footnote]" to "/b/earcons/short_pling_reverse.wav",
                "[pre-title]" to "/b/earcons/pageflip.wav",
                "[pre-chapter-change]" to "/b/earcons/medium_pling.wav",
                "[pre-book-change]" to "/b/earcons/long_pling.wav",
            ),
            IosSpeakPlatform.earconFiles("/b/"),
        )
    }
}
