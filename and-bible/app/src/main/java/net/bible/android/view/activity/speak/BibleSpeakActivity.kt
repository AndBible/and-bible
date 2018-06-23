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
import net.bible.android.control.speak.INVALID_LABEL_ID
import net.bible.android.control.speak.PlaybackSettings
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.db.bookmark.LabelDto
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

@ActivityScope
class BibleSpeakActivity : CustomTitlebarActivityBase() {
    @Inject lateinit var speakControl: SpeakControl
    @Inject lateinit var bookmarkControl: BookmarkControl
    private lateinit var bookmarkLabels: List<LabelDto>

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        EventBus.getDefault().register(this)

        bookmarkLabels = bookmarkControl.assignableLabels

        if(bookmarkLabels.isEmpty()) {
            autoBookmark.isEnabled = false
            if(autoBookmark.isChecked) {
                autoBookmark.isChecked = false
                updateSettings()
            }
        }

        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = "$progress %"
                updateSettings()
            }
        })

        val adapter = ArrayAdapter<LabelDto>(this, android.R.layout.simple_spinner_dropdown_item, bookmarkLabels)
        bookmarkTag.adapter = adapter

        bookmarkTag.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateSettings()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                updateSettings()
            }
        }
        resetView(SpeakSettings.fromSharedPreferences())
    }

    private fun resetView(settings: SpeakSettings) {
        statusText.text = speakControl.getStatusText()
        synchronize.isChecked = settings.synchronize
        speakBookChanges.isChecked = settings.playbackSettings.speakBookChanges
        speakChapterChanges.isChecked = settings.playbackSettings.speakChapterChanges
        speakTitles.isChecked = settings.playbackSettings.speakTitles

        playEarconBook.isChecked = settings.playbackSettings.playEarconBook
        playEarconChapter.isChecked = settings.playbackSettings.playEarconChapter
        playEarconTitles.isChecked = settings.playbackSettings.playEarconTitles

        replaceDivineName.isChecked = settings.replaceDivineName
        restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

        when(settings.rewindAmount) {
            SpeakSettings.RewindAmount.ONE_VERSE -> rewindOneVerse.isChecked = true
            SpeakSettings.RewindAmount.TEN_VERSES -> rewindTenVerses.isChecked = true
            SpeakSettings.RewindAmount.SMART -> rewindSmart.isChecked = true
            SpeakSettings.RewindAmount.NONE -> {}
        }

        when(settings.autoRewindAmount) {
            SpeakSettings.RewindAmount.NONE -> autoRewindNone.isChecked = true
            SpeakSettings.RewindAmount.ONE_VERSE -> autoRewindOneVerse.isChecked = true
            SpeakSettings.RewindAmount.TEN_VERSES -> autoRewindTenVerses.isChecked = true
            SpeakSettings.RewindAmount.SMART -> autoRewindSmart.isChecked = true
        }

        if(settings.autoBookmarkLabelId != null) {
            val labelDto = bookmarkLabels.find { labelDto -> labelDto.id == settings.autoBookmarkLabelId }
            val itemId = bookmarkLabels.indexOf(labelDto)

            bookmarkTag.setSelection(itemId)
            autoBookmark.isChecked = true
        }
        else {
            autoBookmark.isChecked = false
            bookmarkTag.isEnabled = false
        }
        speakSpeed.progress = settings.playbackSettings.speed
        speedStatus.text = "${settings.playbackSettings.speed} %"

    }

    fun onEventMainThread(ev: SpeakProggressEvent) {
        statusText.text = speakControl.getStatusText()
    }

    fun onEventMainThread(ev: SpeakSettings) {
        resetView(ev)
    }

    fun onSettingsChange(widget: View) = updateSettings()

    private fun updateSettings() {
        bookmarkTag.isEnabled = autoBookmark.isChecked

        val labelId = if (bookmarkTag.selectedItemPosition != INVALID_POSITION) {
            bookmarkLabels.get(bookmarkTag.selectedItemPosition).id
        } else INVALID_LABEL_ID

        val settings = SpeakSettings(
                synchronize = synchronize.isChecked,
                playbackSettings = PlaybackSettings(
                        speakBookChanges = speakBookChanges.isChecked,
                        speakChapterChanges = speakChapterChanges.isChecked,
                        speakTitles = speakTitles.isChecked,
                        playEarconBook = playEarconBook.isChecked,
                        playEarconChapter = playEarconChapter.isChecked,
                        playEarconTitles = playEarconTitles.isChecked,
                        speed = speakSpeed.progress
                        ),
                autoBookmarkLabelId = if (autoBookmark.isChecked) labelId else null,
                replaceDivineName = replaceDivineName.isChecked,

                rewindAmount =  if ( rewindOneVerse.isChecked ) {
                    SpeakSettings.RewindAmount.ONE_VERSE }
                else if (rewindTenVerses.isChecked ) {
                    SpeakSettings.RewindAmount.TEN_VERSES }
                else {
                    SpeakSettings.RewindAmount.SMART},

                autoRewindAmount =  if ( autoRewindOneVerse.isChecked ) {
                    SpeakSettings.RewindAmount.ONE_VERSE }
                else if (autoRewindNone.isChecked ) {
                    SpeakSettings.RewindAmount.NONE }
                else if (autoRewindTenVerses.isChecked ) {
                    SpeakSettings.RewindAmount.TEN_VERSES }
                else {
                    SpeakSettings.RewindAmount.SMART},
                restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked
        )
        settings.saveSharedPreferences()
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
        statusText.text = speakControl.getStatusText()
    }
}
