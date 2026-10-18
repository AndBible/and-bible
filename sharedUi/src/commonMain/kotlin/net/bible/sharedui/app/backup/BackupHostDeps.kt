package net.bible.sharedui.app.backup

import androidx.navigation.NavHostController
import kotlinx.coroutines.CompletableDeferred
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.service.backup.BackupServiceImpl
import net.bible.sharedcore.backup.BackupController
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppDirectories
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.DateTimeFormats
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.backup.nav.BackupNavDeps
import net.bible.sharedui.strings.Strings

/**
 * Builds the Backup & restore destination's deps. Named `BackupFeatureHost` because
 * [net.bible.android.control.backup.BackupHost] is the platform file/dialog port it is handed.
 * Construction is cheap and must not touch the database: Backup starts uninitialised
 * (`BackupBeforeDbInitTest`), so every collaborator is a provider that runs only when a screen opens.
 */
class BackupFeatureHost(
    private val shell: HostShell,
    private val strings: Strings,
    /** Host-scoped (its dialogs and file pickers need the calling Activity on Android). */
    private val backupHost: BackupHost,
    private val backupControlProvider: () -> BackupControl,
    private val settingsProvider: () -> AppSettings,
    private val environmentProvider: () -> AppEnvironment,
    private val coreStringsProvider: () -> CoreStrings,
    private val directoriesProvider: () -> AppDirectories,
    private val dateTimeFormatsProvider: () -> DateTimeFormats,
) {
    /**
     * The one in-flight [awaitDestination], completed when the Backup entry is no longer on the back stack -- not
     * when it stops being the CURRENT destination, because Backup's own "restore modules" pushes InstallZip on top.
     */
    private var backupReturn: CompletableDeferred<Unit>? = null

    /** The destination's deps. The controller (and its service) is built per destination entry. */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(navController: NavHostController): BackupNavDeps = BackupNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        windowTitle = strings.backupAndRestoreTitle,
        controllerFor = {
            BackupController(
                BackupServiceImpl(
                    backupHost, backupControlProvider(), settingsProvider(), environmentProvider(), coreStringsProvider(),
                    directoriesProvider(), dateTimeFormatsProvider(),
                ),
                shell.hostScope,
            )
        },
    )

    /**
     * Open Backup & restore in-graph (through [navigate]) and suspend until the user has left it.
     * `BackupNavigation.backupPopup` calls this; its other callers' `showErrorDialog` loop re-shows its dialog only
     * once this returns. A second request supersedes the first (the first returns).
     *
     * Already ON Backup ([currentRoute], an error dialog raised inside it offered "Backup & restore"): return at
     * once, so the dialog re-shows over Backup. Any outer await stays armed.
     */
    suspend fun awaitDestination(currentRoute: String?, navigate: () -> Unit) {
        if (currentRoute == NavRoutes.BACKUP) return
        val done = CompletableDeferred<Unit>()
        backupReturn?.complete(Unit)
        backupReturn = done
        navigate()
        done.await()
    }

    /** Called once Backup has left the back stack: releases the pending [awaitDestination], if any. */
    fun completeReturn() {
        val pending = backupReturn ?: return
        backupReturn = null
        pending.complete(Unit)
    }

    /** True while an [awaitDestination] is waiting for Backup to leave. */
    val hasPendingReturn: Boolean get() = backupReturn != null
}
