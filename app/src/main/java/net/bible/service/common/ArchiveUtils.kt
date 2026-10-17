package net.bible.service.common

import net.bible.sharedcore.log.Log
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Source
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import org.crosswire.common.compress.GzipStreams
import org.crosswire.common.compress.ZipReader
import java.io.File

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
     * existing files are overwritten). An unsafe entry name (absolute or containing `..`) fails with
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

    // L1b-handover(G1): EpubBackendState switches to Path, then delete
    @Deprecated("Use the okio Path overload", ReplaceWith("gzipFile(sourceFile.toOkioPath(), destinationFile.toOkioPath())"))
    fun gzipFile(sourceFile: File, destinationFile: File) =
        gzipFile(sourceFile.toOkioPath(), destinationFile.toOkioPath())

    // L1b-handover(G1): EpubBackendState switches to Path, then delete
    @Deprecated("Use the okio Path overload", ReplaceWith("gunzipFile(sourceFile.toOkioPath(), destinationFile.toOkioPath())"))
    fun gunzipFile(sourceFile: File, destinationFile: File) =
        gunzipFile(sourceFile.toOkioPath(), destinationFile.toOkioPath())

}
