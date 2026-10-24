package net.bible.android.control.backup

import net.bible.sharedcore.platform.files.FileDelivery
import net.bible.sharedcore.platform.files.FilePicker

/** Platform side of backup/restore: pickers, share sheet and picked-source streams. */
interface BackupHost : FilePicker, FileDelivery {
    /** Error dialog with Report/Backup buttons. */
    suspend fun showErrorReport(message: String, cause: Throwable?)
    suspend fun restoreModules()
}
