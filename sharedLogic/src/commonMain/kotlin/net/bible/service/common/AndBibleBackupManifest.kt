package net.bible.service.common

import okio.use
import kotlinx.serialization.Serializable
import net.bible.sharedcore.platform.AppJson
import okio.Source
import okio.buffer
import org.crosswire.common.compress.ZipReader
import org.crosswire.common.compress.ZipWriter

enum class DbType {
    BOOKMARKS, WORKSPACES, READINGPLANS, SETTINGS, REPOSITORIES, MODULES, EPUBS, MYDOCUMENTS, AI_SETTINGS
}
enum class BackupType {
    // Note! We can only trust STUDYPAD_EXPORT, as manifest is not existing before AB version 822.
    STUDYPAD_EXPORT,
    DB_BACKUP,
    MODULE_BACKUP
}

const val ANDBIBLE_BACKUP_MANIFEST_FILENAME = "AndBibleBackupManifest.json"
@Serializable
data class AndBibleBackupManifest(
    val backupType: BackupType,
    val contains: Set<DbType>? = null,
    val manifestVersion: Int = 1,
    val andBibleVersion: Int = 0 // absent in manifests of releases 822/823; written manifests always carry the real version
) {
    fun toJson(): String {
        return AppJson.encodeToString(serializer(), this)
    }

    fun saveToZip(zip: ZipWriter, dosDateTime: Long = 0L) {
        zip.putEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME, dosDateTime) { it.writeUtf8(toJson()) }
    }
    companion object {
        fun fromJson(jsonString: String): AndBibleBackupManifest {
            return AppJson.decodeFromString(serializer(), jsonString)
        }

        /**
         * Reads the manifest from the first entry of the ZIP in [source] (the manifest is always written
         * first); null when that entry is not the manifest. Closes [source].
         */
        fun fromZip(source: Source): AndBibleBackupManifest? = ZipReader(source).use { zip ->
            val entry = zip.nextEntry()
            if (entry?.name == ANDBIBLE_BACKUP_MANIFEST_FILENAME) {
                fromJson(zip.entry.buffer().readUtf8())
            } else null
        }
    }
}
