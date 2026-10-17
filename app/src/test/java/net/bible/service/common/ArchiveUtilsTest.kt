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

    @Test fun unzipReadsTask1ModuleBackupFixture() {
        val expected = mapOf(
            "AndBibleBackupManifest.json" to "8bdedd0f03732483cf87c5f98b4ca6224932a4d4c5ff99543542beb466ca8db9",
            "mods.d/isv.conf" to "4154a70db61d421253a722a9ed53d62b781c3131386d70744a56581a85df63cc",
            "modules/texts/ztext/isv/nt.bzz" to "d476a7dd076a07cc612ec957e8d51d0bc7464761e0fce474ae83f8e376370043",
            "modules/texts/ztext/isv/nt.bzs" to "b2d4f718b141373915412337fdc0b4ea36ff2b1a6f7f7cc26d09616d30f79d62",
            "modules/texts/ztext/isv/nt.bzv" to "83c7bdba9a1c97f20948ee674c4eeecff149a4f2caed062d6e80dde58402cf76",
        )
        val fixture = File(javaClass.classLoader!!.getResource("l1b-g2/module-backup.abmd.zip")!!.toURI())
        val destination = folder.newFolder("fixtureDestination")
        ArchiveUtils.unzip(fixture.toOkioPath(), destination.toOkioPath())
        for ((name, sha) in expected) {
            val bytes = File(destination, name).readBytes()
            val hex = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(name, sha, hex)
        }
    }
}
