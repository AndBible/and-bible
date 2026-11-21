package net.bible.service.device

import android.content.Context
import android.content.Intent
import net.bible.android.activity.StartupActivity
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes

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

/** Boolean extra on a nav-host intent: show the background-activity list once the reading view is up (#4174). */
internal const val ACTION_OPEN_ACTIVITY_LIST = "net.bible.android.OPEN_ACTIVITY_LIST"
internal const val EXTRA_OPEN_ACTIVITY_LIST = "net.bible.android.openActivityList"

/** The reading view with a request to open the background-activity list; the progress notification's tap target. */
internal fun openActivityListIntent(context: Context): Intent {
    val intent = NavHostComposeActivity.intentFor(context, NavRoutes.READING)
    // A distinct action keeps this PendingIntent apart from other getActivity(requestCode 0) intents to the same
    // component: PendingIntents match on everything but extras.
    intent.action = ACTION_OPEN_ACTIVITY_LIST
    // Spelled as a literal (== EXTRA_OPEN_ACTIVITY_LIST) because NavHostRoutingGuardTest allowlists extras by literal name.
    intent.putExtra("net.bible.android.openActivityList", true)
    return intent
}

/**
 * Forwards an [EXTRA_OPEN_ACTIVITY_LIST] request carried by this intent to [requests] and removes the extra, so a
 * re-delivered intent (recreate, process-death restore) does not open the list again. Returns whether it was set.
 */
internal fun Intent.consumeActivityListRequest(requests: ActivityListRequests?): Boolean {
    if (!getBooleanExtra(EXTRA_OPEN_ACTIVITY_LIST, false)) return false
    removeExtra(EXTRA_OPEN_ACTIVITY_LIST)
    requests?.request()
    return true
}
