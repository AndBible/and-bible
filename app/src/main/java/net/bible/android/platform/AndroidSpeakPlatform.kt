package net.bible.android.platform

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
import net.bible.android.control.event.phonecall.PhoneCallMonitor
import net.bible.android.control.speak.*
import net.bible.android.view.activity.base.CurrentActivityHolder
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
                resources.getString(R.string.speak_chapter_changed))
        }
}
