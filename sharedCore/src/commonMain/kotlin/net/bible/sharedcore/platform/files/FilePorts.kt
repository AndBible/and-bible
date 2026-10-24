package net.bible.sharedcore.platform.files

import okio.Source

class PickedSource(val token: String, val displayName: String?, val mimeType: String?)
enum class SaveOrShare { SAVE, SHARE }
enum class DeliveryResult { DELIVERED, CANCELLED, FAILED }
enum class BackupFileType { SQLITE3, ZIP, UNKNOWN }

interface FilePicker {
    /** null when the user cancels. */
    suspend fun pickBackupFile(mime: String = "application/*"): PickedSource?
    /** Throws [okio.IOException] when the source cannot be opened. */
    suspend fun openInput(source: PickedSource): Source
}

interface FileDelivery {
    /** Saves [file] (a local path) to a user-chosen place or hands it to the share sheet. */
    suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
                        subject: String?, message: String?, chooserTitle: String): DeliveryResult
}
