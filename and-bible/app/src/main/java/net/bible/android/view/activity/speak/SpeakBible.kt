package net.bible.android.view.activity.speak

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import de.greenrobot.event.EventBus
import kotlinx.android.synthetic.main.speak_bible.*
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.device.speak.SpeakBibleTextProvider
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

val speakSpeedPref = "speak_speed_percent_pref"

@ActivityScope
class SpeakBible : CustomTitlebarActivityBase() {
    private lateinit var speakControl: SpeakControl
    private lateinit var textProvider: SpeakBibleTextProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        EventBus.getDefault().register(this)
        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = progress.toString()
                speakControl.setRate(progress/100F)
            }
        })
        val initialSpeed = CommonUtils.getSharedPreferences().getInt(speakSpeedPref, 100)
        speakSpeed.progress = initialSpeed
        speedStatus.text = initialSpeed.toString()
    }

    fun onEventMainThread(ev: SpeakProggressEvent) {
        statusText.text = textProvider.getStatusText()
    }

    fun onSettingsChange(widget: View) = updateSettings()

    private fun updateSettings() {
        textProvider.settings = SpeakSettings(synchronize.isChecked, speak_chapter_changes.isChecked, continue_sentences.isChecked)
    }

    fun onButtonClick(button: View) {
        try {
            when (button) {
                rewindButton -> speakControl.rewind()
                stopButton -> speakControl.stop()
                pauseButton -> speakControl.pause()
                speakButton ->
                    if (speakControl.isPaused) {
                        speakControl.continueAfterPause()
                    } else {
                        updateSettings()
                        speakControl.speakBible()
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
    }

    @Inject
    internal fun setSpeakControl(speakControl: SpeakControl) {
        this.speakControl = speakControl
        textProvider = speakControl.speakBibleTextProvider
        statusText.text = if (!speakControl.isSpeaking) "" else textProvider.getStatusText()
        synchronize.isChecked = textProvider.settings.synchronize
        speak_chapter_changes.isChecked = textProvider.settings.chapterChanges
        continue_sentences.isChecked = textProvider.settings.continueSentences
    }
}
