package net.bible.service.common

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveUtilsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun gzipRoundTripPreservesRandomBytes() {
        val bytes = ByteArray(64 * 1024).also { Random(7).nextBytes(it) }
        val source = folder.newFile("source").apply { writeBytes(bytes) }
        val compressed = folder.newFile("compressed")
        val restored = folder.newFile("restored")
        ArchiveUtils.gzipFile(source, compressed)
        ArchiveUtils.gunzipFile(compressed, restored)
        assertArrayEquals(bytes, restored.readBytes())
    }

    @Test fun unzipWritesBothEntriesWithPrefixAndNormalizedSeparators() {
        val zip = folder.newFile("archive.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            for (name in listOf("one.txt", "nested\\two.txt")) {
                output.putNextEntry(ZipEntry(name))
                output.write(name.toByteArray())
                output.closeEntry()
            }
        }
        val destination = folder.newFolder("destination")
        ArchiveUtils.unzipFile(zip, destination, "prefix-")
        assertEquals("one.txt", File(destination, "prefix-one.txt").readText())
        assertEquals("nested\\two.txt", File(destination, "prefix-nested/two.txt").readText())
    }

    @Test fun hashAndUrlRetainBaselineSemantics() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ArchiveUtils.md5Hash("abc"))
        assertEquals("https://a/b/c", ArchiveUtils.appendUrl("https://a/b", "c"))
        assertEquals("https://a/b/c", ArchiveUtils.appendUrl("https://a/b/", "c"))
    }

    @Test fun unzipRejectsTraversalAndWritesNothingOutside() {
        val zip = folder.newFile("evil.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            for (name in listOf("ok.txt", "../evil.txt")) {
                output.putNextEntry(ZipEntry(name))
                output.write(name.toByteArray())
                output.closeEntry()
            }
        }
        val target = folder.newFolder("tmp", "target")
        assertThrows(java.io.IOException::class.java) { ArchiveUtils.unzipFile(zip, target) }
        assertFalse(File(target.parentFile, "evil.txt").exists())
    }
}
