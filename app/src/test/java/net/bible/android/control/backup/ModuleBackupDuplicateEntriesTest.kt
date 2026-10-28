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
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaDataJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    private fun book(root: File, initials: String, description: String = initials, dataPath: String = "./data/dict", version: String? = null): Book {
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
            """.trimIndent() + (version?.let { "\nVersion=$it" } ?: ""))
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

    /**
     * #3039: two installations whose files map to the same archive path with different contents cannot both be
     * represented; the backup fails naming both books instead of silently keeping a mix of the first one's files.
     */
    @Test fun installedVersionsWithDifferingContentsFailTheBackup() = runBlocking {
        val firstRoot = temporaryFolder.newFolder("first")
        val secondRoot = temporaryFolder.newFolder("second")
        val first = book(firstRoot, "DuplicateBackupDict", "Dictionary", version = "1.2")
        val second = book(secondRoot, "DuplicateBackupDict", "Dictionary", version = "2.0")
        val zip = File(temporaryFolder.root, "versions.zip")
        val conflict = assertThrows(ModuleBackupConflict::class.java) {
            runBlocking { backup.createModulesZip(listOf(first, second), zip.toOkioPath()) }
        }
        assertEquals("mods.d/duplicatebackupdict.conf", conflict.entryName)
        // #3039 review: the realistic conflict is one module in two SWORD roots with the same name, so the
        // message must tell the two apart by version and folder.
        assertEquals(ModuleBackupSource(first.name, "1.2", firstRoot.toOkioPath().toString()), conflict.first)
        assertEquals(ModuleBackupSource(second.name, "2.0", secondRoot.toOkioPath().toString()), conflict.second)
        val message = conflict.userMessage(net.bible.android.platform.AndroidCoreStrings(RuntimeEnvironment.getApplication()))
        assertEquals(
            "The backup was not made: Dictionary (version 1.2, folder $firstRoot) and Dictionary (version 2.0, " +
                "folder $secondRoot) both contain the file mods.d/duplicatebackupdict.conf, with different contents. " +
                "Select only one of them and try again.",
            message,
        )
        assertFalse("a failed backup leaves no archive", zip.exists())
        assertFalse("a failed backup leaves no partial archive", File(zip.path + ".tmp").exists())
    }

    @Test fun identicalCopiesInTwoRootsAreWrittenOnce() = runBlocking {
        val firstRoot = temporaryFolder.newFolder("first")
        val secondRoot = temporaryFolder.newFolder("second")
        val first = book(firstRoot, "CopiedBackupDict")
        val second = book(secondRoot, "CopiedBackupDict")
        val zip = File(temporaryFolder.root, "copies.zip")

        backup.createModulesZip(listOf(first, second), zip.toOkioPath())

        assertArchive(zip, listOf("mods.d/copiedbackupdict.conf", "data/dict.dat")
            .associateWith { File(firstRoot, it).readBytes() })
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
