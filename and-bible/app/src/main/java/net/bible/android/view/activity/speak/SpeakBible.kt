package net.bible.android.view.activity.speak

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import de.greenrobot.event.EventBus
import kotlinx.android.synthetic.main.speak_bible.*
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.CustomTitlebarActivityBase
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.device.speak.TextToSpeechServiceManager
import net.bible.service.device.speak.event.SpeakProggressEvent
import javax.inject.Inject

/** Allow user to enter search criteria

 * @author Martin Denham [mjdenham at gmail dot com]
 * *
 * @see gnu.lgpl.License for license details.<br></br>
 * The copyright to this program is held by it's author.
 */
@ActivityScope
class SpeakBible : CustomTitlebarActivityBase() {
    private lateinit var speakControl: SpeakControl

    /** Called when the activity is first created.  */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_bible)
        super.buildActivityComponent().inject(this)
        EventBus.getDefault().register(this)
    }

    fun onEventMainThread(ev: SpeakProggressEvent) {
        statusText.text = speakControl.statusText
    }

    fun onSettingsChange(widget: View) = updateSettings()

    private fun updateSettings() {
        speakControl.settings = SpeakSettings(synchronize.isChecked)
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
        val settings = speakControl.settings
        statusText.text = speakControl.statusText
        if(settings != null) {
            synchronize.isChecked = settings.synchronize
        }
    }

    companion object {
        private val TAG = "Speak"
    }
}
