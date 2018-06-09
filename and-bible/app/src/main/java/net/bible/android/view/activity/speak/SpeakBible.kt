package net.bible.android.view.activity.speak

import android.os.Bundle
import android.view.View
import android.widget.*
import de.greenrobot.event.EventBus
import kotlinx.android.synthetic.main.speak_bible.*
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.common.CommonUtils
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.device.speak.SpeakBibleTextProvider
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

val speakSpeedPref = "speak_speed_percent_pref"

@ActivityScope
class SpeakBible : CustomTitlebarActivityBase() {
    private lateinit var speakControl: SpeakControl
    private lateinit var bookmarkControl: BookmarkControl
    private lateinit var textProvider: SpeakBibleTextProvider
    private lateinit var bookmarkLabels: List<LabelDto>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        EventBus.getDefault().register(this)

        textProvider = speakControl.speakBibleTextProvider
        val initialSettings = textProvider.settings
        statusText.text = if (!speakControl.isSpeaking) "" else textProvider.getStatusText()
        synchronize.isChecked = initialSettings.synchronize
        speak_chapter_changes.isChecked = initialSettings.chapterChanges
        continue_sentences.isChecked = initialSettings.continueSentences

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

        bookmarkLabels = bookmarkControl.assignableLabels
        val adapter = ArrayAdapter<LabelDto>(this, android.R.layout.simple_spinner_dropdown_item, bookmarkLabels)
        bookmarkTag.adapter = adapter
        if(initialSettings.autoBookmarkLabel != null) {
            val labelDto = bookmarkLabels.find({ labelDto -> labelDto.name == initialSettings.autoBookmarkLabel })
            val itemId = bookmarkLabels.indexOf(labelDto)

            bookmarkTag.setSelection(itemId)
            auto_bookmark.isChecked = true
        }
        else {
            auto_bookmark.isChecked = false
            bookmarkTag.setEnabled(false)
        }

        bookmarkTag.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                updateSettings()
            }

        }
    }

    fun onEventMainThread(ev: SpeakProggressEvent) {
        statusText.text = textProvider.getStatusText()
    }

    fun onSettingsChange(widget: View) = updateSettings()

    private fun updateSettings() {
        bookmarkTag.setEnabled(auto_bookmark.isChecked)
        val label = bookmarkLabels.get(bookmarkTag.selectedItemPosition)

        textProvider.settings = SpeakSettings(synchronize.isChecked,
                speak_chapter_changes.isChecked, continue_sentences.isChecked,
                if (auto_bookmark.isChecked) label.name else null)
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
    internal fun setBookmarkControl(bookmarkControl: BookmarkControl) {
        this.bookmarkControl = bookmarkControl
    }

    @Inject
    internal fun setSpeakControl(speakControl: SpeakControl) {
        this.speakControl = speakControl
    }
}
