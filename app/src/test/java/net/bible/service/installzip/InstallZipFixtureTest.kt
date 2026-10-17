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

    /** Intended divergence: the old `ZipInputStream` code let this escape as Error(GENERIC); it is now an invalid archive. */
    @Test fun malformedNameAfterAValidEntryScansAsInvalid() {
        val bad = "mods.d/".toByteArray() + byteArrayOf(0x81.toByte())
        assertTrue(scanSwordZip(rawZip("mods.d/a.conf".toByteArray(), bad)).invalid)
    }

    @Test fun nulCharInEntryNameScansAsInvalid() {
        assertTrue(scanSwordZip(rawZip("mods.d/a\u0000b.conf".toByteArray())).invalid)
        assertTrue(scanSwordZip(rawZip("mods.d/a.conf".toByteArray(), "mods.d/a\u0000b.conf".toByteArray())).invalid)
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
            val installed = File(target.toString(), "mods.d/kjv.conf")
            assertTrue(installed.isFile)
            // The installed bytes are the archive entry's bytes (old-release/README.md: mods.d/kjv.conf).
            val expected = java.util.zip.ZipFile(zip.toFile()).use { z -> z.getInputStream(z.getEntry("mods.d/kjv.conf")).readBytes() }
            assertEquals(sha256(expected), sha256(installed.readBytes()))
            assertTrue(expected.isNotEmpty())
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

    private fun sha256(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- copy routines: content, truncation of a larger target, add-on cache clearing, absolute names ----
    // Written against the old File.copyTo(overwrite = true) semantics: the target holds exactly the new bytes.

    private val installed = mutableListOf<File>()

    private fun source(bytes: ByteArray): Path = File(tmpDir(), "src.bin").also { it.writeBytes(bytes) }.toOkioPath()

    private fun target(sub: String, name: String) = File(SharedConstants.modulesDir, "$sub/$name").also { installed += it }

    private fun recordingCommitter(calls: MutableList<String>) = DefaultInstallCommitter(
        testBackupControl(), testCoreStrings(), net.bible.android.platform.AndroidAppEnvironment(RuntimeEnvironment.getApplication()),
        clearAddonCaches = { calls += "clear" },
    )

    @After fun removeInstalled() { installed.forEach { it.delete() } }

    @Test fun ttfCsvAndImageCopiesAreExactAndTruncateALargerTarget() = runBlocking {
        val calls = mutableListOf<String>()
        val c = recordingCommitter(calls)
        val bytes = ByteArray(300) { (it * 3).toByte() }
        class Case(val dir: String, val name: String, val commit: suspend (Path) -> Unit)
        val cases = listOf(
            Case("ttf", "g2-font.ttf") { c.commitTtf(it, "g2-font.ttf") },
            Case("prompts", "g2-prompts.csv") { c.commitCsv(it, "g2-prompts.csv") },
            Case(net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR, "g2-pic.png") { c.commitBackgroundImage(it, "g2-pic.png") },
        )
        for ((n, case) in cases.withIndex()) {
            val name = case.name
            val out = target(case.dir, name)
            out.parentFile!!.mkdirs()
            out.writeBytes(ByteArray(5000) { 7 }) // larger existing target
            case.commit(source(bytes))
            assertEquals(name, sha256(bytes), sha256(out.readBytes()))
            assertEquals(300L, out.length())
            assertEquals(n + 1, calls.size)
        }
    }

    @Test fun validSqliteCopyIsExactAndBadHeaderLeavesNoClear() = runBlocking {
        val calls = mutableListOf<String>()
        val c = recordingCommitter(calls)
        val bytes = "SQLite format 3\u0000".toByteArray() + ByteArray(400) { it.toByte() }
        val out = target("mybible", "g2-copy.sqlite3")
        out.parentFile!!.mkdirs()
        out.writeBytes(ByteArray(5000) { 7 })
        runCatching { c.commitSqlite(source(bytes), SqliteBookType.MYBIBLE, "g2-copy.sqlite3") } // registering a fake db may fail; the copy comes first
        assertEquals(sha256(bytes), sha256(out.readBytes()))
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun absoluteDisplayNameStaysInsideTheModuleDirectory() = runBlocking {
        val calls = mutableListOf<String>()
        recordingCommitter(calls).commitTtf(source(byteArrayOf(1, 2, 3)), "/g2-abs.ttf")
        val out = target("ttf", "g2-abs.ttf")
        assertTrue(out.isFile)
        assertEquals(listOf<Byte>(1, 2, 3), out.readBytes().toList())
    }
}
