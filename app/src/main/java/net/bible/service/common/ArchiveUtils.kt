package net.bible.service.common

import okio.use
import net.bible.sharedcore.log.Log
import okio.FileSystem
import okio.Path
import okio.Source
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import org.crosswire.common.compress.GzipStreams
import org.crosswire.common.compress.ZipReader

/** Platform-independent archive and URL utilities; retains the legacy file semantics. */
object ArchiveUtils {
    private const val TAG = "ArchiveUtils"
    fun md5Hash(str: String): String = str.encodeUtf8().md5().hex()

    fun appendUrl(u: String, filename: String): String =
        if(u.endsWith("/"))
            "$u$filename"
        else
            "$u/$filename"

    fun unzip(zip: Path, destinationDir: Path, filePrefix: String = "") =
        unzip(FileSystem.SYSTEM.source(zip), destinationDir, filePrefix)

    /**
     * Extracts every non-directory entry under [destinationDir] (names get `\` -> `/`, parents are created,
     * existing files are overwritten). A non-directory entry whose normalised name is empty (such as `.`) is skipped. An unsafe entry name (absolute or containing `..`) fails with
     * [okio.IOException]; entries before it stay written. Closes [source].
     */
    fun unzip(source: Source, destinationDir: Path, filePrefix: String = "") {
        val fs = FileSystem.SYSTEM
        ZipReader(source).use { reader ->
            while (true) {
                val zipEntry = reader.nextEntry() ?: break
                val filePath = safeZipEntryPath(zipEntry.name)
                    ?: throw okio.IOException("Unsafe ZIP entry name")
                if (!zipEntry.isDirectory && filePath.isNotEmpty()) {
                    val file = destinationDir / (filePrefix + filePath)
                    Log.i(TAG, "Writing $file")
                    file.parent?.let { fs.createDirectories(it) }
                    fs.write(file) { writeAll(reader.entry) }
                }
            }
        }
    }

    fun gzipFile(source: Path, destination: Path) {
        val fs = FileSystem.SYSTEM
        GzipStreams.gzip(fs.sink(destination)).buffer().use { out ->
            fs.source(source).use { out.writeAll(it) }
        }
    }

    fun gunzipFile(source: Path, destination: Path) {
        val fs = FileSystem.SYSTEM
        GzipStreams.gunzip(fs.source(source)).buffer().use { input ->
            fs.write(destination) { writeAll(input) }
        }
    }
}
