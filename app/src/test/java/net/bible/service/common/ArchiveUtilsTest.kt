package net.bible.service.common

import okio.Path.Companion.toOkioPath
import okio.source
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
        ArchiveUtils.gzipFile(source.toOkioPath(), compressed.toOkioPath())
        ArchiveUtils.gunzipFile(compressed.toOkioPath(), restored.toOkioPath())
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
        ArchiveUtils.unzip(zip.toOkioPath(), destination.toOkioPath(), "prefix-")
        assertEquals("one.txt", File(destination, "prefix-one.txt").readText())
        assertEquals("nested\\two.txt", File(destination, "prefix-nested/two.txt").readText())
    }

    @Test fun hashAndUrlRetainBaselineSemantics() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ArchiveUtils.md5Hash("abc"))
        assertEquals("https://a/b/c", ArchiveUtils.appendUrl("https://a/b", "c"))
        assertEquals("https://a/b/c", ArchiveUtils.appendUrl("https://a/b/", "c"))
    }

    @Test fun md5OfEmptyAndUnicode() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", ArchiveUtils.md5Hash(""))
        assertEquals("6c32ac9736a5b1fb333b006d8385e18e", ArchiveUtils.md5Hash("\u00e4\uD835\uDD04"))
    }

    @Test fun unzipSkipsFileEntryWithEmptyNormalisedName() {
        val zip = folder.newFile("dot.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("."))
            output.write("x".toByteArray())
            output.closeEntry()
        }
        val destination = folder.newFolder("dotDestination")
        ArchiveUtils.unzip(zip.toOkioPath(), destination.toOkioPath())
        assertEquals(0, destination.listFiles()!!.size)
    }

    @Test fun unzipWritesBackslashNamesAsSubdirs() {
        val zip = folder.newFile("backslash.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("a\\b\\c.txt"))
            output.write("deep".toByteArray())
            output.closeEntry()
        }
        val destination = folder.newFolder("backslashDestination")
        ArchiveUtils.unzip(zip.toOkioPath(), destination.toOkioPath())
        assertEquals("deep", File(destination, "a/b/c.txt").readText())
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
        assertThrows(java.io.IOException::class.java) { ArchiveUtils.unzip(zip.toOkioPath(), target.toOkioPath()) }
        assertFalse(File(target.parentFile, "evil.txt").exists())
    }

    @Test fun gzipPathRoundTripPreservesRandomBytes() {
        val bytes = ByteArray(64 * 1024).also { Random(11).nextBytes(it) }
        val source = folder.newFile("pathSource").apply { writeBytes(bytes) }
        val compressed = folder.newFile("pathCompressed")
        val restored = folder.newFile("pathRestored")
        ArchiveUtils.gzipFile(source.toOkioPath(), compressed.toOkioPath())
        ArchiveUtils.gunzipFile(compressed.toOkioPath(), restored.toOkioPath())
        assertArrayEquals(bytes, restored.readBytes())
        // The gzip written by the okio code is readable by java.util.zip.
        assertArrayEquals(bytes, java.util.zip.GZIPInputStream(compressed.inputStream()).use { it.readBytes() })
    }

    @Test fun unzipSourceWritesEntriesWithPrefixAndSkipsDirectories() {
        val zip = folder.newFile("source.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("dir/"))
            output.closeEntry()
            output.putNextEntry(ZipEntry("dir\\f.txt"))
            output.write("f".toByteArray())
            output.closeEntry()
        }
        val destination = folder.newFolder("sourceDestination")
        ArchiveUtils.unzip(zip.source(), destination.toOkioPath(), "p-")
        assertEquals("f", File(destination, "p-dir/f.txt").readText())
        assertEquals(listOf("p-dir"), destination.list()!!.toList())
    }

    @Test fun unzipOverwritesExistingFiles() {
        val zip = folder.newFile("overwrite.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("x.txt"))
            output.write("new".toByteArray())
            output.closeEntry()
        }
        val destination = folder.newFolder("overwriteDestination")
        File(destination, "x.txt").writeText("old and longer")
        ArchiveUtils.unzip(zip.toOkioPath(), destination.toOkioPath())
        assertEquals("new", File(destination, "x.txt").readText())
    }

    @Test fun unzipReadsOldReleaseModuleBackupFixture() {
        val expected = mapOf(
            "AndBibleBackupManifest.json" to "9d3d48d424f67f9299d32e684cec966156ecb2e891db427f2cc1adf76860fc63",
            "mods.d/kjv.conf" to "6d1936b9a09be3b51f62497dbc5e20aebf7a8f53c63bd94dac7fcd4cc8ad812f",
            "modules/texts/ztext/kjv/kjvpref.rtf" to "4a98197967259d6dc0ebc2482bc96631d2d8a3aeae55a88e55075c3fe99550eb",
            "modules/texts/ztext/kjv/nt.bzz" to "edb0ca76f00c3ff8ef60d6d338507cffaefbdf438bb672ad0eddc8bdd30de949",
            "modules/texts/ztext/kjv/ot.bzz" to "97a7ba7e8250a36190c24122afe7c213727f5204d154b989bbdd35e4a8160918",
            "modules/texts/ztext/kjv/nt.bzv" to "28d78d603333dd8612598b7dc447d960a40b289120d5f17e226ac1708ed9918a",
            "modules/texts/ztext/kjv/ot.bzv" to "a878075030340b66dfc01a3d31cc9845f24c1f726e6e8aa06cec70e76a5112d7",
            "modules/texts/ztext/kjv/ot.bzs" to "8e5b1476478aa8d86e019fc29ecb0d8ff547d05ecb0f9c07d7e4f6289cb47901",
            "modules/texts/ztext/kjv/nt.bzs" to "c9a1be9dcb248e0dffb41b9559aa1485bd920a8c314d67d5688fbd44fa593b44",
        )
        val fixture = File(javaClass.classLoader!!.getResource("l1b-g2/old-release/module-backup.abmd.zip")!!.toURI())
        val destination = folder.newFolder("fixtureDestination")
        ArchiveUtils.unzip(fixture.toOkioPath(), destination.toOkioPath())
        for ((name, sha) in expected) {
            val bytes = File(destination, name).readBytes()
            val hex = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(name, sha, hex)
        }
    }
}
