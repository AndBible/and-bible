package net.bible.android.view.activity.speak

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.SeekBar
import kotlinx.android.synthetic.main.speak_general.*
import net.bible.android.activity.R
import net.bible.android.control.speak.NumPagesToSpeakDefinition
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.base.Dialogs
import javax.inject.Inject

/** Allow user to listen to text via TTS

 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class GeneralSpeakActivity : AbstractSpeakActivity() {
   private lateinit var numPagesToSpeakDefinitions: Array<NumPagesToSpeakDefinition>

    private lateinit var speakControl: SpeakControl

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Speak view")

        setContentView(R.layout.speak_general)

        super.buildActivityComponent().inject(this)

        // set title of chapter/verse/page selection
        numPagesToSpeakDefinitions = speakControl.calculateNumPagesToSpeakDefinitions()

        // set a suitable prompt for the different numbers of chapters
        numPagesToSpeakDefinitions.forEach {
            val numChaptersCheckBox = findViewById(it.radioButtonId) as RadioButton
            numChaptersCheckBox.text = it.getPrompt()
        }

        // set defaults for Queue and Repeat
        queue.isChecked = true
        repeat.isChecked = false
        speakSpeed.progress = currentSettings.playbackSettings.speed
        speedStatus.text = "${currentSettings.playbackSettings.speed} %"
        speakSpeed.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                speedStatus.text = "$progress %"
                currentSettings.playbackSettings.speed = progress
                currentSettings.save(true)
            }
        })
        resetView(this.currentSettings)
        Log.d(TAG, "Finished displaying Speak view")
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
                        speakControl.speakText(selectedNumPagesToSpeak(), queue.isChecked, repeat.isChecked);
                    }
                forwardButton -> speakControl.forward()
            }
        } catch (e: Exception) {
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
    }

    fun selectedNumPagesToSpeak() = numPagesToSpeakDefinitions.first { numChapters.checkedRadioButtonId == it.radioButtonId }

    @Inject
    internal fun setSpeakControl(speakControl: SpeakControl) {
        this.speakControl = speakControl
    }

    override fun resetView(settings: SpeakSettings) {
        sleepTimer.isChecked = settings.sleepTimer > 0
        sleepTimer.text = if(settings.sleepTimer>0) getString(R.string.sleep_timer_timer_set, settings.sleepTimer) else getString(R.string.conf_sleep_timer)
    }

    companion object {
        private const val TAG = "Speak"
    }
}
