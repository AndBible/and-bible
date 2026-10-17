package net.bible.service.db

import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.platform.files.DeliveryResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.*
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.platform.exportStudyPads
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.common.ANDBIBLE_BACKUP_MANIFEST_FILENAME
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import okio.Source
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ExportStudyPadsTest {
    @org.junit.Before fun setMain() { kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher()) }
    @org.junit.After fun resetMain() { kotlinx.coroutines.Dispatchers.resetMain() }

    @Test fun exportDeliversArchiveContainingOnlyChosenPadAndItsText() = runBlocking {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val pad = BookmarkEntities.Label(name = "Export pad")
        val other = BookmarkEntities.Label(name = "Not exported")
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        dao.insert(pad); dao.insert(other)
        val entry = BookmarkEntities.StudyPadTextEntry(labelId = pad.id, orderNumber = 0)
        dao.insert(entry); dao.insert(BookmarkEntities.StudyPadTextEntryText(entry.id, "Exported text"))
        var deliveries = 0
        val host = object : BackupHost {
            override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
                deliveries++
                assertEquals(SaveOrShare.SHARE, mode)
                assertEquals("Export_pad.abdb.zip", fileName)
                assertEquals("application/zip", shareMimeType)
                assertFalse(subject.isNullOrBlank())
                ZipFile(file).use { zip ->
                    assertNotNull(zip.getEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME))
                    val db = File.createTempFile("export-test", ".sqlite3", controller.get().cacheDir)
                    try {
                        zip.getInputStream(zip.getEntry("db/bookmarks.sqlite3")).use { input -> db.outputStream().use { input.copyTo(it) } }
                        openSqlite(db.path).use { sql ->
                            assertEquals(listOf("Export pad"), sql.queryRows("SELECT name FROM Label") { it.getText(0) })
                            assertEquals(listOf("Exported text"), sql.queryRows("SELECT text FROM StudyPadTextEntryText") { it.getText(0) })
                        }
                    } finally { db.delete() }
                }
                return DeliveryResult.DELIVERED
            }
            override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
            override suspend fun openInput(source: PickedSource): Source = error("unused")
            override suspend fun showErrorReport(message: String, cause: Throwable?) = error(message)
            override suspend fun restoreModules() = error("unused")
        }
        try {
            exportStudyPads(controller.get(), pad, chooseDestination = { SaveOrShare.SHARE }, host = host)
            assertEquals(1, deliveries)
            assertNotNull(dao.labelById(other.id))
        } finally { controller.pause().stop().destroy() }
    }

    /** Runs a real export of one pad and returns a copy of the delivered archive. */
    private fun exportOnePad(): File {
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val copy = File.createTempFile("export-copy", ".zip", controller.get().cacheDir)
        runBlocking {
            val pad = BookmarkEntities.Label(name = "G2 export pad")
            DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(pad)
            val host = object : BackupHost {
                override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
                    File(file).copyTo(copy, overwrite = true)
                    return DeliveryResult.DELIVERED
                }
                override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
                override suspend fun openInput(source: PickedSource): Source = error("unused")
                override suspend fun showErrorReport(message: String, cause: Throwable?) = error(message)
                override suspend fun restoreModules() = error("unused")
            }
            try { exportStudyPads(controller.get(), pad, chooseDestination = { SaveOrShare.SHARE }, host = host) }
            finally { controller.pause().stop().destroy() }
        }
        return copy
    }

    private fun fixtureZip(): File {
        val f = File.createTempFile("fixture", ".zip")
        javaClass.classLoader!!.getResourceAsStream("l1b-g2/studypad-export.abdb.zip")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f
    }

    private fun manifestWithoutVersion(zip: ZipFile): String {
        val json = zip.getInputStream(zip.getEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME)).readBytes().decodeToString()
        return org.json.JSONObject(json).also { it.remove("andBibleVersion") }.toString()
    }

    @Test fun exportedArchiveEntriesAndManifestMatchFixture() {
        val export = exportOnePad(); val fixture = fixtureZip()
        ZipFile(export).use { e -> ZipFile(fixture).use { f ->
            assertEquals(f.entries().toList().map { it.name }, e.entries().toList().map { it.name })
            // Only the app version differs per build; everything else must be identical.
            assertEquals(manifestWithoutVersion(f), manifestWithoutVersion(e))
        } }
    }

    @Test fun newExportIsReadByJdkZipInputStream() {
        val export = exportOnePad()
        java.util.zip.ZipInputStream(export.inputStream()).use { zin ->
            val names = generateSequence { zin.nextEntry }.map { entry ->
                val bytes = zin.readBytes()
                assertTrue(entry.name, bytes.isNotEmpty())
                if (entry.name.endsWith(".sqlite3")) assertEquals("SQLite format 3", bytes.decodeToString(0, 15))
                assertTrue("DOS time set for ${entry.name}", entry.time > 0)
                entry.name
            }.toList()
            assertEquals(listOf(ANDBIBLE_BACKUP_MANIFEST_FILENAME, "db/bookmarks.sqlite3"), names)
        }
    }
}
