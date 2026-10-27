package net.bible.android.control.backup

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import net.bible.test.DatabaseResetter
import net.bible.test.testBackupControl
import okio.Path.Companion.toOkioPath
import org.crosswire.common.util.NetUtilJvm
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ModuleBackupDuplicateEntriesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val backup by lazy { testBackupControl() }

    @Before fun setUp() = DatabaseResetter.resetDatabase()
    @After fun tearDown() = DatabaseResetter.resetDatabase()

    private fun book(root: File, initials: String, description: String = initials, dataPath: String = "./data/dict"): Book {
        File(root, dataPath.removePrefix("./") + ".dat").apply {
            parentFile!!.mkdirs()
            writeText("dictionary data for $description")
        }
        val conf = File(root, "mods.d/${initials.lowercase()}.conf").apply {
            parentFile!!.mkdirs()
            writeText("""
                [$initials]
                Description=$description
                ModDrv=RawLD
                DataPath=$dataPath
                Encoding=UTF-8
                Lang=en
            """.trimIndent())
        }
        return SwordBook(SwordBookMetaDataJvm.create(conf, NetUtilJvm.getURI(root)), NullBackend())
    }

    private fun assertArchive(zip: File, expected: Map<String, ByteArray>) {
        // Check local entries as well as the central directory: a map/set alone hides duplicates.
        val names = ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.map { it.name }.toList()
        }
        assertEquals(ANDBIBLE_BACKUP_MANIFEST_FILENAME, names.first())
        assertEquals("each path must occur once: $names", names.toSet().size, names.size)
        assertEquals(expected.keys + ANDBIBLE_BACKUP_MANIFEST_FILENAME, names.toSet())
        ZipFile(zip).use { archive ->
            assertEquals(names, archive.entries().toList().map { it.name })
            for ((name, bytes) in expected) {
                val actual = archive.getInputStream(archive.getEntry(name)).use { it.readBytes() }
                assertTrue("content of $name", bytes.contentEquals(actual))
            }
        }
    }

    @Test fun sameBookSelectedTwiceWritesEachFileOnce() = runBlocking {
        val root = temporaryFolder.newFolder("sword")
        val book = book(root, "DuplicateBackupDict")
        val zip = File(temporaryFolder.root, "repeated.zip")

        backup.createModulesZip(listOf(book, book), zip.toOkioPath())

        assertArchive(zip, listOf("mods.d/duplicatebackupdict.conf", "data/dict.dat")
            .associateWith { File(root, it).readBytes() })
        // Entry tracking belongs to one archive, not the BackupControl instance.
        backup.createSingleModuleZip(book, zip.toOkioPath())
        assertArchive(zip, listOf("mods.d/duplicatebackupdict.conf", "data/dict.dat")
            .associateWith { File(root, it).readBytes() })
    }

    @Test fun installedVersionsWithSameArchivePathsKeepFirstContents() = runBlocking {
        val firstRoot = temporaryFolder.newFolder("first")
        val secondRoot = temporaryFolder.newFolder("second")
        val first = book(firstRoot, "DuplicateBackupDict", "First version")
        val second = book(secondRoot, "DuplicateBackupDict", "Second version")
        val installed = Books.installed()
        installed.addBook(first)
        installed.addBook(second)
        try {
            // Books compares category, abbreviation, initials AND name: two installations of the
            // same initials with differing descriptions survive the registry's set deduplication.
            val versions = installed.getBooks().filter { it.initials == "DuplicateBackupDict" }
            assertEquals(2, versions.size)
            val zip = File(temporaryFolder.root, "versions.zip")
            backup.createModulesZip(listOf(first, second), zip.toOkioPath())
            assertArchive(zip, listOf("mods.d/duplicatebackupdict.conf", "data/dict.dat")
                .associateWith { File(firstRoot, it).readBytes() })
        } finally {
            installed.removeBook(first)
            installed.removeBook(second)
        }
    }

    @Test fun dictionariesSharingDataDirectoryKeepBothConfsAndOneCopyOfData() = runBlocking {
        val root = temporaryFolder.newFolder("sword")
        val first = book(root, "SharedBackupDictA")
        val second = book(root, "SharedBackupDictB")
        val zip = File(temporaryFolder.root, "shared.zip")

        backup.createModulesZip(listOf(first, second), zip.toOkioPath())

        assertArchive(zip, listOf("mods.d/sharedbackupdicta.conf", "mods.d/sharedbackupdictb.conf", "data/dict.dat")
            .associateWith { File(root, it).readBytes() })
    }

    @Test fun dictionaryParentWalkIncludingItsConfDoesNotDuplicateItInSyncArchive() = runBlocking {
        val root = temporaryFolder.newFolder("sword")
        // A root-level dictionary prefix makes the category-specific parent walk include mods.d.
        val book = book(root, "FlatBackupDict", dataPath = "./dict")
        val zip = File(temporaryFolder.root, "single.zip")

        backup.createSingleModuleZip(book, zip.toOkioPath())

        assertArchive(zip, listOf("mods.d/flatbackupdict.conf", "dict.dat")
            .associateWith { File(root, it).readBytes() })
    }
}
