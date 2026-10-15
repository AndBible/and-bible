package net.bible.android.control.speak

import net.bible.sharedcore.platform.speak.AudioFocus
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener

const val SPEAK_KEY = "speak"

/** Raw JSON storage kept separate from the settings database for early widget startup. */
interface SpeakSettingsStore {
    fun load(): String?
    fun save(json: String)
}

/** A device speech setup problem (not an AndBible bug), told to the user by the platform. */
enum class SpeakSetupProblem { ENGINE_NOT_AVAILABLE, LANGUAGE_NOT_AVAILABLE, AUDIO_FOCUS_DENIED }

data class SpeakPhrases(
    val first: String, val second: String, val third: String, val fourth: String,
    val fifth: String, val bookChanged: String, val chapterChanged: String,
    val divineNameOriginal: List<String> = emptyList(),
    val divineNameReplace: List<String> = emptyList(),
)

interface SpeakPlatform : AudioFocus {
    val maxSpeechInputLength: Int
    /** Read on every access so playback notices changes in the system engine preference. */
    val preferredEnginePackage: String?
    /** Registers earcons before reporting successful initialization on the engine callback thread. */
    fun createEngine(listener: SpeakEngineListener): SpeakEngine
    fun onPrepareForSpeaking()
    fun attachMediaControls(control: SpeakControl)
    fun detachMediaControls()
    fun bringReadingToFront()
    fun phrasesFor(languageCode: String): SpeakPhrases
    /** Explains [problem]; engine and voice data problems also offer the system speech settings. */
    fun reportProblem(problem: SpeakSetupProblem)
}
