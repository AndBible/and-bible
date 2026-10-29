package net.bible.service.cloudsync.host

import net.bible.android.control.backup.BackupHost
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest

private const val TAG = "IosBackupHost"

/** Installed by the UI host without introducing a sharedLogic -> sharedUi dependency. */
object IosErrorReportHooks {
    var report: (suspend (Throwable?) -> Unit)? = null
    var backup: (() -> Unit)? = null
}


/**
 * iOS [BackupHost]: file picking and delivery are delegated (I1's `IosFilePicker` / `IosFileDelivery`);
 * the error report is an app dialog. The live UI registers [onRestoreModules] to open InstallZip.
 */
class IosBackupHost(
    picker: FilePicker,
    delivery: FileDelivery,
    private val dialogs: () -> AppDialogController,
    private val okText: () -> String,
    private val reportText: () -> String = okText,
    private val backupText: () -> String = okText,
    private val skipText: () -> String = okText,
) : BackupHost, FilePicker by picker, FileDelivery by delivery {

    override suspend fun showErrorReport(message: String, cause: Throwable?) {
        if (cause != null) Log.w(TAG, "Error report: $message", cause)
        val html = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val result = dialogs().await(AppDialogRequest.Options(
            title = null, message = html,
            options = listOf(
                net.bible.sharedcore.settings.SettingsItem.Choice("report", reportText()),
                net.bible.sharedcore.settings.SettingsItem.Choice("backup", backupText()),
            ), dismissText = skipText(), asActionSheet = true,
        ))
        errorReportAction((result as? net.bible.sharedcore.ui.dialog.AppDialogResult.Selected)?.value, cause,
            { IosErrorReportHooks.report?.invoke(it) }, { IosErrorReportHooks.backup?.invoke() })
    }

    /** Bound only while the app's navigation hosts are alive; cleared when the composition is disposed. */
    var onRestoreModules: () -> Unit = {}

    override suspend fun restoreModules() = onRestoreModules()
}
