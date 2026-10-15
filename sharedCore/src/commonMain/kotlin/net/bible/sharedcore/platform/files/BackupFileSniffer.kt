package net.bible.sharedcore.platform.files

import okio.Source
import okio.buffer

object BackupFileSniffer {
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    /** Reads up to 16 bytes: full SQLite header or ZIP local-file signature; any read failure is UNKNOWN. */
    fun sniff(open: () -> Source): BackupFileType = try {
        open().buffer().use { source ->
            val header = ByteArray(16)
            var count = 0
            while (count < header.size) {
                val read = source.read(header, count, header.size - count)
                if (read == -1) break
                count += read
            }
            val text = header.copyOf(count).decodeToString()
            when {
                count == 16 && text == SQLITE_HEADER -> BackupFileType.SQLITE3
                text.startsWith("PK\u0003\u0004") -> BackupFileType.ZIP
                else -> BackupFileType.UNKNOWN
            }
        }
    } catch (e: Exception) {
        BackupFileType.UNKNOWN
    }
}
