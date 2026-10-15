package net.bible.android.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.UserMessages
import net.bible.android.control.event.phonecall.PhoneCallMonitor
import net.bible.android.control.speak.*
import net.bible.sharedcore.platform.speak.SpeakEngine
import net.bible.sharedcore.platform.speak.SpeakEngineListener
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.MediaButtonHandler
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.nav.NavRoutes

class AndroidSpeakPlatform(private val context: Context) : SpeakPlatform {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    override val maxSpeechInputLength get() = android.speech.tts.TextToSpeech.getMaxSpeechInputLength()
    override val preferredEnginePackage: String?
        get() = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
                ?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w("Speak", "Unable to read preferred TTS engine from system settings", e)
            null
        }

    override fun createEngine(listener: SpeakEngineListener): SpeakEngine =
        AndroidSpeakEngine(context.applicationContext, preferredEnginePackage, listener)

    @RequiresApi(Build.VERSION_CODES.O)
    private fun newAudioFocusRequest(onFocusChange: (Boolean) -> Unit) =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { change ->
                Log.i("Speak", "Audio focus changed $change")
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> onFocusChange(true)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusChange(false)
                }
            }.build()

    override fun requestAudioFocus(onFocusChange: (Boolean) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest == null) {
            val request = newAudioFocusRequest(onFocusChange)
            if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            audioFocusRequest = request
        }
        return true
    }
    override fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.also { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        }
    }
    override fun startPhoneCallMonitoring(onCallStateChanged: (Boolean) -> Unit) =
        PhoneCallMonitor.ensureMonitoringStarted(onCallStateChanged)
    override fun onPrepareForSpeaking() {
        if (CommonUtils.isDiscrete) GlobalScope.launch { CommonUtils.requestNotificationPermission() }
        CommonUtils.settings.setLong("speak-last-used", System.currentTimeMillis())
        CurrentActivityHolder.currentActivity?.volumeControlStream = AudioManager.STREAM_MUSIC
    }
    override fun attachMediaControls(control: SpeakControl) = MediaButtonHandler.initialize(control)
    override fun detachMediaControls() = MediaButtonHandler.release()
    override fun bringReadingToFront() {
        context.startActivity(NavHostComposeActivity.intentFor(context, NavRoutes.READING).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
    override fun phrasesFor(languageCode: String): SpeakPhrases =
        BibleApplication.application.getLocalizedResources(languageCode).let { resources ->
            SpeakPhrases(resources.getString(R.string.speak_first), resources.getString(R.string.speak_second),
                resources.getString(R.string.speak_third), resources.getString(R.string.speak_fourth),
                resources.getString(R.string.speak_fifth), resources.getString(R.string.speak_book_changed),
                resources.getString(R.string.speak_chapter_changed),
                resources.getStringArray(R.array.speak_divinename_original).toList(),
                resources.getStringArray(R.array.speak_divinename_replace).toList())
        }

    override fun reportProblem(problem: SpeakSetupProblem) {
        when (problem) {
            SpeakSetupProblem.ENGINE_NOT_AVAILABLE -> showTtsSetupProblem(R.string.tts_engine_not_available)
            SpeakSetupProblem.LANGUAGE_NOT_AVAILABLE -> showTtsSetupProblem(R.string.tts_lang_not_available)
            SpeakSetupProblem.AUDIO_FOCUS_DENIED -> UserMessages.toast(R.string.tts_audio_focus_denied, long = true)
        }
    }

    private fun showTtsSetupProblem(msgId: Int) {
        Dialogs.showErrorMsgWithAction(msgId, R.string.open_settings) { openSystemTtsSettings() }
    }

    /** Opens the system text-to-speech settings, or the general settings where that screen is missing. */
    private fun openSystemTtsSettings() {
        val context: Context = CurrentActivityHolder.currentActivity ?: context
        val flags = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK
        // Exception, not just ActivityNotFoundException: some OEM settings screens are not exported.
        try {
            context.startActivity(Intent(ACTION_TTS_SETTINGS).addFlags(flags))
        } catch (e: Exception) {
            Log.w("Speak", "No TTS settings screen, opening general settings", e)
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags))
            } catch (e2: Exception) {
                Log.e("Speak", "No settings screen at all", e2)
            }
        }
    }

    private companion object {
        const val ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    }
}
