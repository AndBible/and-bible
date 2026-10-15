package net.bible.sharedcore.platform.speak

enum class SpeakLanguageResult { AVAILABLE, MISSING_DATA, NOT_SUPPORTED, OTHER }

/** The system TTS engine, narrowed to the operations speech commands and playback use. */
interface SpeakEngine {
    fun speakText(text: String, utteranceId: String)
    fun playEarcon(earcon: String, volume: Float, utteranceId: String)
    fun playSilence(durationMs: Long, utteranceId: String)
    /** [languageTag] is BCP-47 (`Locale.toLanguageTag()` on the JVM). */
    fun setLanguage(languageTag: String): SpeakLanguageResult
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
