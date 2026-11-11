package net.bible.service.device

import android.content.Context
import android.content.Intent
import net.bible.android.activity.StartupActivity
import net.bible.service.common.BuildVariant

/**
 * Notifications outlive both activities and the process. Never capture an unlocked state when
 * posting one: the startup gate must ask for the PIN again when the user actually taps it.
 * A fresh reading host cannot rely on ActivityBase's wasPaused guard, which starts false.
 */
internal fun notificationActivityIntent(context: Context, destination: Intent): Intent =
    if (BuildVariant.Appearance.isDiscrete) {
        // Startup owns the calculator result and only opens reading after RESULT_OK. Clear the old
        // task so cancelling cannot uncover reading (or another calculator) underneath this gate.
        Intent(context, StartupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
    } else {
        destination
    }
