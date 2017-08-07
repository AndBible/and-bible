package net.bible.android.view.activity.speak

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import kotlinx.android.synthetic.main.speak.*
import net.bible.android.activity.R
import net.bible.android.control.speak.NumPagesToSpeakDefinition
import net.bible.android.control.speak.SpeakControl
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import javax.inject.Inject

/** Allow user to enter search criteria

 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
class Speak : CustomTitlebarActivityBase() {

    private lateinit var numPagesToSpeakDefinitions: Array<NumPagesToSpeakDefinition>

    private lateinit var speakControl: SpeakControl

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Displaying Search view")
        setContentView(R.layout.speak)

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
                        speakControl.speak(selectedNumPagesToSpeak(), queue.isChecked, repeat.isChecked)
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

    companion object {
        private val TAG = "Speak"
    }
}
