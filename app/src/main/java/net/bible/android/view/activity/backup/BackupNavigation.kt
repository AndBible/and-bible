package net.bible.android.view.activity.backup

import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.sharedcore.nav.NavRoutes

/** Activity navigation stays outside the portable backup engine. */
object BackupNavigation {
    /**
     * "Restore documents" from the Backup destination: open InstallZip (slice 8 D2). In-graph on the nav
     * host. The old `awaitIntent` + `if (result.data?.data == null) return` + post was dead (finding M6):
     * InstallZip attaches no result data, and `DocumentInstallService.postTerminalEvents` already calls
     * `DocumentChanges.notifyInstalledChanged` when an install finishes.
     */
    fun restoreModulesViaIntent(activity: ActivityBase) {
        if (activity is NavHostComposeActivity) {
            activity.openInstallZip()
        } else {
            activity.startActivity(NavHostComposeActivity.intentFor(activity, NavRoutes.installZip()))
        }
    }

    /**
     * Open Backup & restore and suspend until the user leaves it (slice 8 C3, plan Correction 4).
     *
     * On the nav host it is an in-graph destination: `awaitBackupDestination` navigates and waits for the
     * entry to leave the back stack. An `awaitIntent` there would be an F53-shaped self-launch that the
     * platform answers with an immediate synthetic cancel, returning at once -- and
     * `ErrorReportControl.showErrorDialog`'s loop would put its dialog straight back over Backup.
     * From any other Activity (`StartupActivity`'s crash check, before DB init) it is a real
     * cross-Activity launch of the host on `BACKUP`, which starts uninitialised (spec §3.1 rule 2).
     */
    suspend fun backupPopup(activity: ActivityBase) {
        if (activity is NavHostComposeActivity) {
            activity.awaitBackupDestination()
        } else {
            awaitBackupFromAnotherActivity(activity)
        }
    }

    /** See [backupPopup]. The `check` is what `SelfLaunchRouteKindGuardTest` reads to exempt this await. */
    private suspend fun awaitBackupFromAnotherActivity(activity: ActivityBase) {
        check(activity !is NavHostComposeActivity) { "the nav host opens Backup in-graph (awaitBackupDestination)" }
        activity.awaitIntent(NavHostComposeActivity.intentFor(activity, NavRoutes.BACKUP))
    }

}
