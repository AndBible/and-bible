package net.bible.service.common

import net.bible.sharedcore.log.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

/** Platform-independent archive and URL utilities; retains the legacy file semantics. */
object ArchiveUtils {
    private const val TAG = "ArchiveUtils"
    fun md5Hash(str: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bigInt = BigInteger(1, md.digest(str.toByteArray(Charsets.UTF_8)))
        return String.format("%032x", bigInt)
    }

    fun appendUrl(u: String, filename: String): String =
        if(u.endsWith("/"))
            "$u$filename"
        else
            "$u/$filename"

    fun unzipFile(zipFile: File, destinationDir: File, filePrefix: String = "") =
        unzipInputStream(zipFile.inputStream(), destinationDir, filePrefix)

    fun unzipInputStream(inputStream: InputStream, destinationDir: File, filePrefix: String = "") {
        val buffer = ByteArray(8192)
        ZipInputStream(inputStream).use { zIn ->
            var zipEntry = zIn.nextEntry
            while (zipEntry != null) {
                val filePath = safeZipEntryPath(zipEntry.name)
                    ?: throw okio.IOException("Unsafe ZIP entry name")
                if (!zipEntry.isDirectory) {
                    val file = File(destinationDir, filePrefix + filePath)
                    Log.i(TAG, "Writing $file")
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fOut ->
                        var count = zIn.read(buffer)
                        while (count != -1) {
                            fOut.write(buffer, 0, count)
                            count = zIn.read(buffer)
                        }
                    }
                }
                zipEntry = zIn.nextEntry
            }
        }
    }

    fun gzipFile(sourceFile: File, destinationFile: File) {
        destinationFile.outputStream().use {
            GZIPOutputStream(it).use {
                sourceFile.inputStream().use { input ->
                    input.copyTo(it)
                }
            }
        }
    }

    fun gunzipFile(sourceFile: File, destinationFile: File) {
        sourceFile.inputStream().use {
            GZIPInputStream(it).use {
                destinationFile.outputStream().use { output ->
                    it.copyTo(output)
                }
            }
        }
    }

}
