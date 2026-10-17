package net.bible.service.installzip

import kotlinx.coroutines.runBlocking
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.files.BackupFileType
import net.bible.test.DatabaseResetter
import net.bible.test.testBackupControl
import net.bible.test.testCoreStrings
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.source
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * L1b G2 Task 14: the install pipeline (scan, StudyPad stats, acquire progress, commit) over the Task 1 and
 * `old-release` fixtures (written by the released build 1117) and hand-made archives. Written against the
 * `File` API first (characterization), then moved to `Path`/`InstallStream` with the same assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallZipFixtureTest {
    private val dirs = mutableListOf<File>()
    private var wasReady = false

    @Before fun setUp() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true
    }

    @After fun tearDown() {
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        dirs.forEach { it.deleteRecursively() }
    }

    private fun tmpDir() = File(CommonUtils.tmpDir, "install-zip-fixture-${System.nanoTime()}").also { it.mkdirs(); dirs += it }

    private fun fixture(name: String): Path =
        File(tmpDir(), name.substringAfterLast('/')).also { f ->
            javaClass.classLoader!!.getResourceAsStream("l1b-g2/$name")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        }.toOkioPath()

    private fun zip(vararg entries: String): Path = File(tmpDir(), "t.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            entries.forEach { out.putNextEntry(ZipEntry(it)); out.write(1); out.closeEntry() }
        }
    }.toOkioPath()

    /** One stored, empty local entry per name (raw bytes, so a name need not be valid UTF-8). */
    private fun rawZip(vararg names: ByteArray): Path = File(tmpDir(), "raw.zip").also { file ->
        file.outputStream().use { out ->
            for (name in names) {
                val header = java.nio.ByteBuffer.allocate(30).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                header.putInt(0x04034b50).putShort(20).putShort(0).putShort(0).putInt(0)
                header.putInt(0).putInt(0).putInt(0).putShort(name.size.toShort()).putShort(0)
                out.write(header.array()); out.write(name)
            }
        }
    }.toOkioPath()

    @Test fun scanOfTask1ModuleFixtureIsValid() {
        val scan = scanSwordZip(fixture("module-backup.abmd.zip"))
        assertFalse(scan.invalid)
        assertFalse(scan.isEpub)
        assertTrue(scan.totalEntries > 2)
    }

    @Test fun scanOfMalformedNameZipIsInvalid() {
        val scan = scanSwordZip(rawZip("mods.d/".toByteArray() + byteArrayOf(0x81.toByte())))
        assertTrue(scan.invalid)
    }

    @Test fun malformedNameAfterAValidEntryIsInvalidNotACrash() {
        val bad = "mods.d/".toByteArray() + byteArrayOf(0x81.toByte())
        assertTrue(scanSwordZip(rawZip("mods.d/a.conf".toByteArray(), bad)).invalid)
    }

    @Test fun traversalZipIsInvalid() {
        assertTrue(scanSwordZip(zip("mods.d/test.conf", "modules/../../evil")).invalid)
        assertTrue(scanSwordZip(zip("../evil")).invalid)
        assertTrue(scanSwordZip(zip("/abs/evil")).invalid)
    }

    @Test fun acquireProgressUsesAvailableHint() = runBlocking {
        val phases = mutableListOf<InstallPhase>()
        val bytes = ByteArray(32768) { it.toByte() }
        val target = File(tmpDir(), "acq.tmp").toOkioPath()
        val runner = InstallJobRunner(InstallInspector { BackupFileType.UNKNOWN }, FixtureCommitter())
        runner.runJob(
            InstallSource("content://x/a.bin", null, "a.bin", null), target,
            openStream = { InstallStream(ByteArrayInputStream(bytes).source(), bytes.size.toLong()) },
            isStudyPadExport = { false }, studyPadStats = { error("") }, swordZipScan = { error("") },
            epubUpgradeCheck = { false }, onPhase = { phases += it }, awaitDecision = { true },
        )
        assertEquals(
            listOf(0, 25, 50, 75, 99, 100),
            phases.filterIsInstance<InstallPhase.Acquiring>().map { it.percent },
        )
    }

    private class FixtureCommitter : InstallCommitter {
        override suspend fun commitSwordZip(localFile: Path, totalEntries: Int, onProgress: (Int) -> Unit) = Unit
        override suspend fun commitEpub(localFile: Path, displayName: String, deleteExisting: Boolean) = true
        override suspend fun commitSqlite(localFile: Path, type: SqliteBookType, displayName: String) = Unit
        override suspend fun commitTtf(localFile: Path, displayName: String) = Unit
        override suspend fun commitCsv(localFile: Path, displayName: String) = Unit
        override suspend fun commitBackgroundImage(localFile: Path, fileName: String) = Unit
        override suspend fun commitStudyPad(unzipFolder: Path) = Unit
    }

    private fun committer() = DefaultInstallCommitter(
        testBackupControl(), testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(RuntimeEnvironment.getApplication()),
        clearAddonCaches = {},
    )

    @Test fun oldReleaseModuleBackupScansValidAndInstalls() = runBlocking {
        val zip = fixture("old-release/module-backup.abmd.zip")
        val scan = scanSwordZip(zip)
        assertFalse(scan.invalid)
        val original = Books.installed().getBook("KJV") ?: error("test module KJV not installed in ~/.sword")
        val target = org.crosswire.jsword.book.sword.SwordBookPath.getSwordDownloadDir()
        Books.installed().removeBook(original)
        try {
            committer().commitSwordZip(zip, scan.totalEntries) {}
            assertNotNull(Books.installed().getBook("KJV"))
            assertTrue(File(target.toString(), "mods.d/kjv.conf").isFile)
        } finally {
            Books.installed().getBook("KJV")?.let { Books.installed().removeBook(it) }
            Books.installed().addBook(original)
            File(target.toString(), "mods.d/kjv.conf").delete()
            File(target.toString(), "modules/texts/ztext/kjv").deleteRecursively()
        }
    }

    @Test fun studyPadStatsOfTask1FixtureNamesItsLabel() = runBlocking {
        val (stats, unzipFolder) = studyPadStatsFor(fixture("studypad-export.abdb.zip"), testBackupControl(), testCoreStrings())
        assertTrue(stats, "G2 export pad" in stats)
        assertTrue(unzipFolder.toFile().resolve("db/bookmarks.sqlite3").isFile)
    }

    @Test fun oldReleaseStudyPadExportIsDetectedAndImported() = runBlocking {
        val zip = fixture("old-release/studypad-export.abdb.zip")
        assertEquals(BackupFileType.ZIP, determineLocalFileType(zip))
        val (stats, unzipFolder) = studyPadStatsFor(zip, testBackupControl(), testCoreStrings())
        assertTrue(stats, "G2Fixture" in stats)
        committer().commitStudyPad(unzipFolder)
        val labels = DatabaseContainer.instance.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name }
        assertTrue(labels.toString(), "G2Fixture" in labels)
        assertFalse(unzipFolder.toFile().exists())
    }
}
