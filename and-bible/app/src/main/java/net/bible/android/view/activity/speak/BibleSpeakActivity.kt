package net.bible.android.view.activity.speak

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.speak_bible.*
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakProgressEvent
import javax.inject.Inject

@ActivityScope
class BibleSpeakActivity : AbstractSpeakActivity() {
    @Inject lateinit var speakControl: SpeakControl

    companion object {
        const val TAG = "BibleSpeakActivity"
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        ABEventBus.getDefault().register(this)

        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = "$progress %"
                if(fromUser) {
                    updateSettings()
                }
            }
        })

        resetView(SpeakSettings.load())
    }

    override fun onDestroy() {
        ABEventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
        synchronize.isChecked = settings.synchronize
        speakChapterChanges.isChecked = settings.playbackSettings.speakChapterChanges
        speakTitles.isChecked = settings.playbackSettings.speakTitles

        playEarconChapter.isChecked = settings.playbackSettings.playEarconChapter
        playEarconTitles.isChecked = settings.playbackSettings.playEarconTitles

        replaceDivineName.isChecked = settings.replaceDivineName
        restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

        autoBookmark.isChecked = settings.autoBookmark
        speakSpeed.progress = settings.playbackSettings.speed
        speedStatus.text = "${settings.playbackSettings.speed} %"
        sleepTimer.isChecked = settings.sleepTimer > 0
        sleepTimer.text = if(settings.sleepTimer>0) getString(R.string.sleep_timer_timer_set, settings.sleepTimer) else getString(R.string.conf_sleep_timer)

    }

    fun onEventMainThread(ev: SpeakProgressEvent) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }


    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings;
        resetView(ev.speakSettings)
    }

    fun onSettingsChange(widget: View) = updateSettings()

    fun updateSettings() {
        val settings = SpeakSettings(
                synchronize = synchronize.isChecked,
                playbackSettings = PlaybackSettings(
                        speakChapterChanges = speakChapterChanges.isChecked,
                        speakTitles = speakTitles.isChecked,
                        playEarconChapter = playEarconChapter.isChecked,
                        playEarconTitles = playEarconTitles.isChecked,
                        speed = speakSpeed.progress
                        ),
                autoBookmark = autoBookmark.isChecked,
                replaceDivineName = replaceDivineName.isChecked,
                restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked,
                sleepTimer = currentSettings.sleepTimer,
                lastSleepTimer = currentSettings.lastSleepTimer
        )
        settings.save(updateBookmark = true)
    }

    fun onButtonClick(button: View) {
        try {
            when (button) {
                prevButton -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                nextButton -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                rewindButton -> speakControl.rewind()
                stopButton -> speakControl.stop()
                pauseButton -> speakControl.pause()
                speakButton ->
                    if (speakControl.isPaused) {
                        speakControl.continueAfterPause()
                    } else {
                        speakControl.speakBible()
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
            Log.e(TAG, "Error: ", e)
        }
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }
}
