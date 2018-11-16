package net.bible.android.view.activity.speak

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.NumberPicker
import net.bible.android.activity.R
import net.bible.android.control.speak.SpeakSettings
import net.bible.android.view.activity.base.CustomTitlebarActivityBase

abstract class AbstractSpeakActivity: CustomTitlebarActivityBase() {
    protected lateinit var currentSettings: SpeakSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        currentSettings = SpeakSettings.load()

        super.onCreate(savedInstanceState)
    }

    fun setSleepTime(sleepTimer: View) {
        if ((sleepTimer as CheckBox).isChecked) {
            val picker = NumberPicker(this)
            picker.minValue = 1
            picker.maxValue = 120
            picker.value = currentSettings.lastSleepTimer

            val layout = FrameLayout(this)
            layout.addView(picker)

            AlertDialog.Builder(this)
                    .setView(layout)
                    .setTitle(R.string.sleep_timer_title)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        currentSettings.sleepTimer = picker.value
                        currentSettings.lastSleepTimer = picker.value
                        currentSettings.save()
                        resetView(currentSettings)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> resetView(currentSettings) }
                    .show()
        }
        else {
            currentSettings.sleepTimer = 0;
            currentSettings.save();
            resetView(currentSettings)
        }
    }

    abstract fun resetView(settings: SpeakSettings)
}