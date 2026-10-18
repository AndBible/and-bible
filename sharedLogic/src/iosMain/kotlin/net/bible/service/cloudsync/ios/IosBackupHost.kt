package net.bible.service.cloudsync.ios

import net.bible.android.control.backup.BackupHost
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest

private const val TAG = "IosBackupHost"

/**
 * iOS [BackupHost]: file picking and delivery are delegated (I1's `IosFilePicker` / `IosFileDelivery`);
 * the error report is an app dialog. Restoring modules from a file has no iOS mechanism yet
 * (spec C7): [restoreModules] logs `I2-pending: restore modules` and does nothing.
 */
class IosBackupHost(
    picker: FilePicker,
    delivery: FileDelivery,
    private val dialogs: () -> AppDialogController,
    private val okText: () -> String,
) : BackupHost, FilePicker by picker, FileDelivery by delivery {

    override suspend fun showErrorReport(message: String, cause: Throwable?) {
        Log.i(TAG, "I2-pending: error report buttons")
        if (cause != null) Log.w(TAG, "Error report: $message", cause)
        // Message bodies are HTML (AppDialogRequest.Message), so escape the plain text.
        val html = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        dialogs().await(AppDialogRequest.Message(title = null, message = html, confirmText = okText()))
    }

    override suspend fun restoreModules() {
        Log.i(TAG, "I2-pending: restore modules")
    }
}
