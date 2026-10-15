package net.bible.android.platform

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import net.bible.android.activity.R
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import net.bible.sharedcore.platform.speak.SpeakLanguageResult
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.sharedcore.log.Log
import java.util.Locale

/** Android TTS adapter. Initialization is forwarded on the original TTS callback thread. */
class AndroidSpeakEngine(context: Context, preferredPackage: String?, listener: SpeakEngineListener) : SpeakEngine {
    private val tts: TextToSpeech

    init {
        val onInit = TextToSpeech.OnInitListener { status ->
            val success = status == TextToSpeech.SUCCESS
            if (success) {
                tts.addEarcon(TextToSpeechServiceManager.EARCON_PRE_FOOTNOTE, context.packageName, R.raw.short_pling)
                tts.addEarcon(TextToSpeechServiceManager.EARCON_POST_FOOTNOTE, context.packageName, R.raw.short_pling_reverse)
                tts.addEarcon(TextToSpeechServiceManager.EARCON_PRE_TITLE, context.packageName, R.raw.pageflip)
                tts.addEarcon(TextToSpeechServiceManager.EARCON_PRE_CHAPTER_CHANGE, context.packageName, R.raw.medium_pling)
                tts.addEarcon(TextToSpeechServiceManager.EARCON_PRE_BOOK_CHANGE, context.packageName, R.raw.long_pling)
                if (tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) = listener.onUtteranceStart(utteranceId)
                    override fun onDone(utteranceId: String) = listener.onUtteranceDone(utteranceId)
                    override fun onError(utteranceId: String) = listener.onUtteranceError(utteranceId)
                }) == TextToSpeech.ERROR) {
                    Log.e("Speak", "Error registering utteranceProgressListener")
                    listener.onInit(false)
                    return@OnInitListener
                }
            }
            listener.onInit(success)
        }
        tts = if (preferredPackage != null) TextToSpeech(context, onInit, preferredPackage)
            else TextToSpeech(context, onInit)
    }

    override fun speakText(text: String, utteranceId: String) { tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId) }
    override fun playEarcon(earcon: String, volume: Float, utteranceId: String) {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts.playEarcon(earcon, TextToSpeech.QUEUE_ADD, params, utteranceId)
    }
    override fun playSilence(durationMs: Long, utteranceId: String) { tts.playSilentUtterance(durationMs, TextToSpeech.QUEUE_ADD, utteranceId) }
    override fun setLanguage(languageTag: String): SpeakLanguageResult {
        val locale = Locale.forLanguageTag(languageTag)
        return when (tts.setLanguage(locale)) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> SpeakLanguageResult.AVAILABLE
            TextToSpeech.LANG_MISSING_DATA -> SpeakLanguageResult.MISSING_DATA
            TextToSpeech.LANG_NOT_SUPPORTED -> SpeakLanguageResult.NOT_SUPPORTED
            else -> SpeakLanguageResult.OTHER
        }
    }
    override fun setSpeechRate(rate: Float) { tts.setSpeechRate(rate) }
    @Suppress("DEPRECATION")
    override fun flush() { tts.speak(" ", TextToSpeech.QUEUE_FLUSH, null) }
    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
    override val maxSpeechInputLength get() = TextToSpeech.getMaxSpeechInputLength()
}
