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
}
