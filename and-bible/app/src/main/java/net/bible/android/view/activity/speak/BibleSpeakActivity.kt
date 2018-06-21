package net.bible.android.view.activity.speak

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.*
import android.widget.AdapterView.INVALID_POSITION
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
import net.bible.service.device.speak.BibleSpeakTextProvider
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

val speakSpeedPref = "speak_speed_percent_pref"

@ActivityScope
class BibleSpeakActivity : CustomTitlebarActivityBase() {
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl

    private lateinit var textProvider: BibleSpeakTextProvider
    private lateinit var bookmarkLabels: List<LabelDto>

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        EventBus.getDefault().register(this)

        textProvider = speakControl.bibleSpeakTextProvider
        bookmarkLabels = bookmarkControl.assignableLabels

        resetView(textProvider.settings)

        val initialSpeed = CommonUtils.getSharedPreferences().getInt(speakSpeedPref, 100)
        speakSpeed.progress = initialSpeed
        speedStatus.text = "$initialSpeed %"

        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = "$progress %"
                speakControl.setRate(progress/100F)
                speakControl.updateSettings()
            }
        })

        val adapter = ArrayAdapter<LabelDto>(this, android.R.layout.simple_spinner_dropdown_item, bookmarkLabels)
        bookmarkTag.adapter = adapter

        bookmarkTag.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSettings(restart = false)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                updateSettings(restart = false)
            }

        }
    }

    private fun resetView(initialSettings: SpeakSettings) {
        statusText.text = textProvider.getStatusText()
        synchronize.isChecked = initialSettings.synchronize
        speakBookChanges.isChecked = initialSettings.speakBookChanges
        speakChapterChanges.isChecked = initialSettings.speakChapterChanges
        speakTitles.isChecked = initialSettings.speakTitles

        playEarconBook.isChecked = initialSettings.playEarconBook
        playEarconChapter.isChecked = initialSettings.playEarconChapter
        playEarconTitles.isChecked = initialSettings.playEarconTitles

        continueSentences.isChecked = initialSettings.continueSentences
        replaceDivineName.isChecked = initialSettings.replaceDivineName
        delayOnParagraphChanges.isChecked = initialSettings.delayOnParagraphChanges

        when(initialSettings.rewindAmount) {
            SpeakSettings.RewindAmount.ONE_VERSE -> rewindOneVerse.isChecked = true
            SpeakSettings.RewindAmount.TEN_VERSES -> rewindTenVerses.isChecked = true
            SpeakSettings.RewindAmount.FULL_CHAPTER -> rewindFullChapter.isChecked = true
            SpeakSettings.RewindAmount.NONE -> {}
        }

        when(initialSettings.autoRewindAmount) {
            SpeakSettings.RewindAmount.NONE -> autoRewindNone.isChecked = true
            SpeakSettings.RewindAmount.ONE_VERSE -> autoRewindOneVerse.isChecked = true
            SpeakSettings.RewindAmount.TEN_VERSES -> autoRewindTenVerses.isChecked = true
            SpeakSettings.RewindAmount.FULL_CHAPTER -> autoRewindFullChapter.isChecked = true
        }

        if(initialSettings.autoBookmarkLabelId != null) {
            val labelDto = bookmarkLabels.find { labelDto -> labelDto.id == initialSettings.autoBookmarkLabelId }
            val itemId = bookmarkLabels.indexOf(labelDto)

            bookmarkTag.setSelection(itemId)
            autoBookmark.isChecked = true
        }
        else {
            autoBookmark.isChecked = false
            bookmarkTag.setEnabled(false)
        }
    }

    fun onEventMainThread(ev: SpeakProggressEvent) {
        statusText.text = textProvider.getStatusText()
    }

    fun onEventMainThread(ev: SpeakSettings) {
        resetView(ev)
    }

    fun onSettingsChange(widget: View) = updateSettings()

    private fun updateSettings(restart: Boolean = true) {
        bookmarkTag.setEnabled(autoBookmark.isChecked)

        val labelId = if (bookmarkTag.selectedItemPosition != INVALID_POSITION) {
            bookmarkLabels.get(bookmarkTag.selectedItemPosition).id
        } else SpeakSettings.INVALID_LABEL_ID

        textProvider.settings = SpeakSettings(
                synchronize = synchronize.isChecked,

                speakBookChanges = speakBookChanges.isChecked,
                speakChapterChanges = speakChapterChanges.isChecked,
                speakTitles = speakTitles.isChecked,

                playEarconBook = playEarconBook.isChecked,
                playEarconChapter = playEarconChapter.isChecked,
                playEarconTitles = playEarconTitles.isChecked,

                continueSentences = continueSentences.isChecked,
                autoBookmarkLabelId = if (autoBookmark.isChecked) labelId else null,
                replaceDivineName = replaceDivineName.isChecked,
                delayOnParagraphChanges = delayOnParagraphChanges.isChecked,

                rewindAmount =  if ( rewindOneVerse.isChecked ) {
                    SpeakSettings.RewindAmount.ONE_VERSE }
                else if (rewindTenVerses.isChecked ) {
                    SpeakSettings.RewindAmount.TEN_VERSES }
                else {
                    SpeakSettings.RewindAmount.FULL_CHAPTER},

                autoRewindAmount =  if ( autoRewindOneVerse.isChecked ) {
                    SpeakSettings.RewindAmount.ONE_VERSE }
                else if (autoRewindNone.isChecked ) {
                    SpeakSettings.RewindAmount.NONE }
                else if (autoRewindTenVerses.isChecked ) {
                    SpeakSettings.RewindAmount.TEN_VERSES }
                else {
                    SpeakSettings.RewindAmount.FULL_CHAPTER}
        )
        if(restart) {
            speakControl.updateSettings()
        }
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
                        speakControl.speakBible()
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
        statusText.text = textProvider.getStatusText()
    }
}
