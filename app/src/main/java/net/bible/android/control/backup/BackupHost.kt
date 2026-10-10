package net.bible.android.control.backup

import java.io.InputStream

class PickedSource(val token: String, val displayName: String?, val mimeType: String?)
enum class DeliveryResult { DELIVERED, CANCELLED, FAILED }

/** Platform side of backup/restore: pickers, share sheet and picked-source streams. */
interface BackupHost {
    suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
                        subject: String?, message: String?, chooserTitle: String): DeliveryResult
    suspend fun pickBackupFile(mime: String = "application/*"): PickedSource?
    suspend fun openInput(source: PickedSource): InputStream
    suspend fun sniffFileType(source: PickedSource): BackupFileType
    /** Error dialog with Report/Backup buttons. */
    suspend fun showErrorReport(message: String, cause: Throwable?)
}
