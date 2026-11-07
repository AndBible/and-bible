package net.bible.sharedcore.platform.files

import okio.Source

class PickedSource(val token: String, val displayName: String?, val mimeType: String?)
enum class SaveOrShare { SAVE, SHARE }

/**
 * What a platform's [FileDelivery] lets the user choose between when exporting a file (#4027). The
 * shared export code asks this instead of testing the platform: a prompt is only shown when there is
 * something to choose.
 */
enum class DeliveryDestinations {
    /** Android: a share sheet, or a folder on the phone's storage. */
    SHARE_AND_PHONE_STORAGE,

    /** iOS, iPadOS, Mac: the share sheet, or the Files app. */
    SHARE_AND_FILES,

    /** Windows, Linux: no share sheet; the only destination is the save dialog. */
    SAVE_ONLY,
}
enum class DeliveryResult { DELIVERED, CANCELLED, FAILED }
enum class BackupFileType { SQLITE3, ZIP, UNKNOWN }

interface FilePicker {
    /** null when the user cancels. */
    suspend fun pickBackupFile(mime: String = "application/*"): PickedSource?
    /** Throws [okio.IOException] when the source cannot be opened. */
    suspend fun openInput(source: PickedSource): Source
}

interface FileDelivery {
    /** The destinations [deliver] offers; defaults to Android's. A [DeliveryDestinations.SAVE_ONLY] host is never asked to choose. */
    val destinations: DeliveryDestinations get() = DeliveryDestinations.SHARE_AND_PHONE_STORAGE

    /** Saves [file] (a local path) to a user-chosen place or hands it to the share sheet. */
    suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String,
                        subject: String?, message: String?, chooserTitle: String): DeliveryResult
}
