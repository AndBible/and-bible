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

import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakPhrases
import net.bible.android.control.speak.SpeakPlatform
import net.bible.android.control.speak.SpeakSetupProblem
import net.bible.service.device.speak.MediaButtonHandler
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.speak.AudioFocus
import net.bible.sharedcore.platform.speak.IosMediaSession
import net.bible.sharedcore.platform.speak.IosSpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import kotlin.time.Clock

/**
 * iOS [SpeakPlatform]: focus calls go to [audioFocus] (the `IosAudioFocus`, typed as the port so tests can fake it; phone calls and other interruptions arrive as audio-session
 * interruptions there, so [startPhoneCallMonitoring] is a no-op), the engine is [IosSpeakEngine], and the lock screen
 * and headset controls are [IosMediaSession] through the shared `MediaButtonHandler`.
 *
 * Deviation from the plan's constructor: the string table lives in `:sharedUi`, which depends on this module, so the
 * localized texts arrive as lambdas: [phrases] (the iOS binding passes `{ keyedSpeakPhrases(strings(it)) }`) and
 * [problemText] (the `tts_*` string of a problem). [settings] receives `speak-last-used`, as on Android.
 */
class IosSpeakPlatform(
    private val audioFocus: AudioFocus,
    private val phrases: (languageTag: String) -> SpeakPhrases,
    private val problemText: (SpeakSetupProblem) -> String,
    private val notifier: UserNotifier,
    private val settings: AppSettings,
    private val earconFiles: Map<String, String>,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SpeakPlatform, AudioFocus by audioFocus {
    override val maxSpeechInputLength: Int get() = MAX_SPEECH_INPUT_LENGTH
    override val preferredEnginePackage: String? get() = null

    override fun createEngine(listener: SpeakEngineListener): SpeakEngine = IosSpeakEngine(listener, earconFiles)

    override fun onPrepareForSpeaking() {
        settings.setLong("speak-last-used", now())
    }

    override fun attachMediaControls(control: SpeakControl) = MediaButtonHandler.initialize(control, IosMediaSession())

    override fun detachMediaControls() = MediaButtonHandler.release()

    /** C7 pending: phase 2 routes the reading screen to the front through the shared root. */
    override fun bringReadingToFront() {
        Log.w(PENDING_TAG, "IosSpeakPlatform.bringReadingToFront: no-op (phase 2: route through the shared root)")
    }

    override fun phrasesFor(languageCode: String): SpeakPhrases = phrases(languageCode)

    override fun reportProblem(problem: SpeakSetupProblem) = notifier.showError(problemText(problem))

    companion object {
        const val MAX_SPEECH_INPUT_LENGTH = 4000
        const val PENDING_TAG = "C7-pending"

        /**
         * The five earcons by their engine names, at the bundle paths `iosApp/project.yml` copies them to
         * (`earcons/<name>.wav` under [resourcePath]); the same pairing as `AndroidSpeakEngine`.
         */
        fun earconFiles(resourcePath: String): Map<String, String> = mapOf(
            TextToSpeechServiceManager.EARCON_PRE_FOOTNOTE to "short_pling",
            TextToSpeechServiceManager.EARCON_POST_FOOTNOTE to "short_pling_reverse",
            TextToSpeechServiceManager.EARCON_PRE_TITLE to "pageflip",
            TextToSpeechServiceManager.EARCON_PRE_CHAPTER_CHANGE to "medium_pling",
            TextToSpeechServiceManager.EARCON_PRE_BOOK_CHANGE to "long_pling",
        ).mapValues { "${resourcePath.trimEnd('/')}/earcons/${it.value}.wav" }
    }
}
