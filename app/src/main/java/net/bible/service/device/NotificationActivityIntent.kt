package net.bible.service.device

import android.content.Context
import android.content.Intent
import net.bible.android.activity.StartupActivity
import net.bible.service.common.CommonUtils

internal const val EXTRA_FROM_NOTIFICATION = "net.bible.android.fromNotification"

/**
 * Calculator notifications behave like the launcher: retain a live task (including editors and
 * pending activity results), and use Startup's PIN gate only on a cold start. ActivityBase's resume
 * guard locks a backgrounded task. Mark the unlocked destination too: a frozen PendingIntent may be
 * tapped after the calculator is enabled, when a fresh host must start locked.
 */
internal fun notificationActivityIntent(context: Context, destination: Intent): Intent =
    if (CommonUtils.showCalculator) {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent.makeMainActivity(android.content.ComponentName(context, StartupActivity::class.java))
    } else {
        destination.putExtra(EXTRA_FROM_NOTIFICATION, true)
    }
