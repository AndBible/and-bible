package net.bible.android.view.activity.speak

import android.os.Build
import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AlertDialog
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.speak_settings.*
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.speak.*
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.Dialogs
import net.bible.service.device.speak.BibleSpeakTextProvider.Companion.FLAG_SHOW_ALL
import net.bible.service.device.speak.event.SpeakProgressEvent

@ActivityScope
class SpeakSettingsActivity : AbstractSpeakActivity() {
    companion object {
        const val TAG = "SpeakSettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.speak_settings)
        super.buildActivityComponent().inject(this)
        ABEventBus.getDefault().register(this)
        resetView(SpeakSettings.load())
    }

    override fun onDestroy() {
        ABEventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun resetView(settings: SpeakSettings) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
        synchronize.isChecked = settings.synchronize
        replaceDivineName.isChecked = settings.replaceDivineName
        restoreSettingsFromBookmarks.isChecked = settings.restoreSettingsFromBookmarks

        autoBookmark.isChecked = settings.autoBookmark
        if(!autoBookmark.isChecked) {
            restoreSettingsFromBookmarks.isChecked = false;
            restoreSettingsFromBookmarks.isEnabled = false;
        }
        else {
            restoreSettingsFromBookmarks.isEnabled = true;
        }
        multiTranslation.isChecked = settings.multiTranslation
    }

    fun onEventMainThread(ev: SpeakSettingsChangedEvent) {
        currentSettings = ev.speakSettings;
        resetView(ev.speakSettings)
    }

    fun onSettingsChange(widget: View) = updateSettings()

    fun updateSettings() {
        val settings = SpeakSettings.load().apply {
            sleepTimer = currentSettings.sleepTimer
            lastSleepTimer = currentSettings.lastSleepTimer
        }
        settings.multiTranslation = multiTranslation.isChecked
        settings.synchronize = synchronize.isChecked
        settings.autoBookmark = autoBookmark.isChecked
        settings.replaceDivineName = replaceDivineName.isChecked
        settings.restoreSettingsFromBookmarks = restoreSettingsFromBookmarks.isChecked
        settings.save(updateBookmark = true)
    }

    fun onHelpButtonClick(button: View) {
        val htmlMessage = ("<b>${getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
                + getString(R.string.speak_help_auto_bookmark)
                + "<br><br><b>${getString(R.string.conf_save_playback_settings_to_bookmarks)}</b><br><br>"
                + getString(R.string.speak_help_playback_settings)
                + "<br><br>"
                + getString(R.string.speak_help_playback_settings_example)
                )

        val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(htmlMessage)
        }

        AlertDialog.Builder(this)
                .setMessage(spanned)
                .setPositiveButton(android.R.string.ok) { _, _ ->  }
                .show()
    }

    fun onButtonClick(button: View) {
        try {
            when (button) {
                prevButton -> speakControl.rewind(SpeakSettings.RewindAmount.ONE_VERSE)
                nextButton -> speakControl.forward(SpeakSettings.RewindAmount.ONE_VERSE)
                rewindButton -> speakControl.rewind()
                stopButton -> speakControl.stop()
                speakPauseButton ->
                    if (speakControl.isPaused) {
                        speakControl.continueAfterPause()
                    } else if (speakControl.isSpeaking) {
                        speakControl.pause()
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

    fun onEventMainThread(ev: SpeakProgressEvent) {
        statusText.text = speakControl.getStatusText(FLAG_SHOW_ALL)
    }

}
