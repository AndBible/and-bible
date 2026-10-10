package net.bible.android.control.speak

import java.util.Locale

/** Raw JSON storage kept separate from the settings database for early widget startup. */
interface SpeakSettingsStore {
    fun load(): String?
    fun save(json: String)
}

enum class SpeakLanguageResult { AVAILABLE, MISSING_DATA, NOT_SUPPORTED, OTHER }

/** The TTS engine, narrowed to the operations used by speech commands and playback. */
interface SpeakEngine {
    fun speakText(text: String, utteranceId: String)
    fun playEarcon(earcon: String, volume: Float, utteranceId: String)
    fun playSilence(durationMs: Long, utteranceId: String)
    fun setLanguage(locale: Locale): SpeakLanguageResult
    fun setSpeechRate(rate: Float)
    fun flush()
    fun stop()
    fun shutdown()
    val maxSpeechInputLength: Int
}

interface SpeakEngineListener {
    fun onInit(success: Boolean)
    fun onUtteranceStart(utteranceId: String)
    fun onUtteranceDone(utteranceId: String)
    fun onUtteranceError(utteranceId: String)
}

data class SpeakPhrases(
    val first: String, val second: String, val third: String, val fourth: String,
    val fifth: String, val bookChanged: String, val chapterChanged: String,
)

interface SpeakPlatform {
    val maxSpeechInputLength: Int
    /** Read on every access so playback notices changes in the system engine preference. */
    val preferredEnginePackage: String?
    /** Registers earcons before reporting successful initialization on the engine callback thread. */
    fun createEngine(listener: SpeakEngineListener): SpeakEngine
    fun requestAudioFocus(onFocusChange: (gained: Boolean) -> Unit): Boolean
    fun abandonAudioFocus()
    fun startPhoneCallMonitoring(onCallStateChanged: (active: Boolean) -> Unit)
    fun onPrepareForSpeaking()
    fun attachMediaControls(control: SpeakControl)
    fun detachMediaControls()
    fun bringReadingToFront()
    fun phrasesFor(languageCode: String): SpeakPhrases
}
