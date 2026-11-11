package net.bible.service.device

import android.content.Context
import android.content.Intent
import net.bible.android.view.activity.StartupActivity
import net.bible.service.common.CommonUtils

/**
 * Notifications outlive both activities and the process. Never capture an unlocked state when
 * posting one: the startup gate must ask for the PIN again when the user actually taps it.
 * A fresh reading host cannot rely on ActivityBase's wasPaused guard, which starts false.
 */
internal fun notificationActivityIntent(context: Context, destination: Intent): Intent =
    if (CommonUtils.showCalculator) {
        // Startup owns the calculator result and only opens reading after RESULT_OK. In particular,
        // do not reuse an existing StartupActivity with SINGLE_TOP and skip its onCreate PIN check.
        Intent(context, StartupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    } else {
        destination
    }
