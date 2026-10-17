/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.service.common

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.backup.BackupHost
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.OLD_DATABASE_VERSION
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.migrations.bookmarkMigrations
import net.bible.android.platform.exportStudyPads
import net.bible.android.view.activity.discrete.CalculatorComposeActivity
import net.bible.service.cloudsync.SyncableDatabaseAccessor
import net.bible.service.cloudsync.SyncableDatabaseDefinition
import net.bible.service.cloudsync.createPatchForDatabase
import net.bible.service.cloudsync.createTriggers
import net.bible.service.db.DatabaseContainer
import net.bible.service.db.openSqlite
import net.bible.service.llm.agent.RawLlmLog
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.test.DatabaseResetter
import okio.Path.Companion.toOkioPath
import okio.Source
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

/**
 * Generator and guard for the L1b G2 compatibility fixtures in `src/test/resources/l1b-g2/`.
 *
 * The fixtures were written by the `java.util.zip` / `java.util.zip.GZIP*` code that shipped before the
 * ZipWriter/ZipReader swap, so later tests can prove files from old releases still read. They are written only
 * when the environment variable `G2_WRITE_FIXTURES=1` is set; without it the test asserts that they exist and
 * are well formed. Do not regenerate them after the swap: that would defeat their purpose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class G2FixtureWriterTest {
    private val fixtureDir = File("src/test/resources/l1b-g2")
    private val tmpFiles = mutableListOf<File>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        DatabaseResetter.resetDatabase()
    }

    @After fun tearDown() {
        tmpFiles.forEach { it.delete() }
        DatabaseResetter.resetDatabase()
        Dispatchers.resetMain()
    }

    private val zips = listOf("db-backup.abdb.zip", "module-backup.abmd.zip", "studypad-export.abdb.zip")
    private val gzips = listOf("bookmarks-patch.sqlite3.gz", "raw-llm-log.txt.gz", "old-monolithic.db.gz")

    @Test fun fixturesExistAndAreWellFormed() {
        if (System.getenv("G2_WRITE_FIXTURES") == "1") writeFixtures()
        for (name in zips) {
            val f = File(fixtureDir, name)
            assertTrue("missing fixture $f", f.isFile && f.length() > 0)
            ZipFile(f).use { z ->
                assertTrue("$name has no manifest", z.getEntry(ANDBIBLE_BACKUP_MANIFEST_FILENAME) != null)
                assertTrue("$name has no entries", z.entries().hasMoreElements())
            }
        }
        for (name in gzips) {
            val f = File(fixtureDir, name)
            assertTrue("missing fixture $f", f.isFile && f.length() > 0)
            GZIPInputStream(f.inputStream()).use { it.readBytes() }
        }
        assertTrue(File(fixtureDir, "fixtures.md").isFile)
    }

    private fun tmp(prefix: String, suffix: String) =
        File.createTempFile(prefix, suffix, CommonUtils.tmpDir).also { tmpFiles += it }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeFixtures() = runBlocking {
        fixtureDir.mkdirs()
        val backupControl = KoinJavaComponent.get<BackupControl>(BackupControl::class.java)
        val env = KoinJavaComponent.get<AppEnvironment>(AppEnvironment::class.java)

        // 1. Database backup: BackupControl.zipDatabaseFiles over a container with one label and one bookmark.
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val label = BookmarkEntities.Label(name = "G2 fixture label")
        dao.insert(label)
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 5, kjvOrdinalEnd = 6)
        dao.insert(bookmark.bookmarkEntity)
        dao.insert(BookmarkEntities.BibleBookmarkToLabel(bookmark.bookmarkEntity, label))
        val dbZip = backupControl.zipDatabaseFiles()!!.toFile()
        dbZip.copyTo(File(fixtureDir, "db-backup.abdb.zip"), overwrite = true)

        // 2. Module backup: the smallest installed text module.
        val book = Books.installed().getBook("ISV") ?: error("test module ISV not installed in ~/.sword")
        val moduleZip = tmp("module-", ".abmd.zip").also { it.delete() }
        backupControl.createSingleModuleZip(book, moduleZip.toOkioPath())
        moduleZip.copyTo(File(fixtureDir, "module-backup.abmd.zip"), overwrite = true)

        // 3. StudyPad export of one label.
        val controller = Robolectric.buildActivity(CalculatorComposeActivity::class.java).setup()
        val pad = BookmarkEntities.Label(name = "G2 export pad")
        dao.insert(pad)
        val entry = BookmarkEntities.StudyPadTextEntry(labelId = pad.id, orderNumber = 0)
        dao.insert(entry)
        dao.insert(BookmarkEntities.StudyPadTextEntryText(entry.id, "G2 exported text"))
        val host = object : BackupHost {
            override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult {
                File(file).copyTo(File(fixtureDir, "studypad-export.abdb.zip"), overwrite = true)
                return DeliveryResult.DELIVERED
            }
            override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
            override suspend fun openInput(source: PickedSource): Source = error("unused")
            override suspend fun showErrorReport(message: String, cause: Throwable?) = error(message)
            override suspend fun restoreModules() = error("unused")
        }
        try {
            exportStudyPads(controller.get(), pad, chooseDestination = { SaveOrShare.SHARE }, host = host)
        } finally { controller.pause().stop().destroy() }

        // 4. Sync patch with one new bookmark.
        val patchDbFile = tmp("bookmarks-", ".sqlite3").also { it.delete() }
        val patchDb = Room.databaseBuilder(application, BookmarkDatabase::class.java, patchDbFile.absolutePath)
            .addMigrations(*bookmarkMigrations)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setDriver(BundledSQLiteDriver())
            .build()
        try {
            val dbDef = SyncableDatabaseAccessor(
                patchDb, { path ->
                    Room.databaseBuilder(application, BookmarkDatabase::class.java, path)
                        .addMigrations(*bookmarkMigrations)
                        .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                        .setDriver(BundledSQLiteDriver())
                        .build()
                }, { patchDb }, patchDbFile,
                SyncableDatabaseDefinition.BOOKMARKS, deviceId = UUID.randomUUID().toString(),
            )
            createTriggers(dbDef)
            val newBookmark = BookmarkEntities.BibleBookmarkWithNotes(kjvOrdinalStart = 7, kjvOrdinalEnd = 8)
            patchDb.bookmarkDao().insert(newBookmark.bookmarkEntity)
            val patch = createPatchForDatabase(dbDef, env)!!
            patch.copyTo(File(fixtureDir, "bookmarks-patch.sqlite3.gz"), overwrite = true)
            patch.delete()
        } finally {
            patchDb.close()
            File(patchDbFile.path + "-journal").delete()
        }

        // 5. Raw LLM log.
        val text = "G2 raw LLM log\nLine two: äöå 𝔄 😀 👍\nLine three: end\n"
        File(fixtureDir, "raw-llm-log.txt.gz").writeBytes(RawLlmLog.gzipCompress(text))

        // 6. Old monolithic database: a tiny SQLite file with the old user_version, gzipped.
        val oldDb = tmp("old-monolithic-", ".db").also { it.delete() }
        openSqlite(oldDb.path).use {
            it.execSQL("CREATE TABLE G2Marker (id INTEGER PRIMARY KEY, note TEXT)")
            it.execSQL("INSERT INTO G2Marker VALUES (1, 'old monolithic')")
            it.execSQL("PRAGMA user_version = $OLD_DATABASE_VERSION")
        }
        GZIPOutputStream(File(fixtureDir, "old-monolithic.db.gz").outputStream()).use { out -> oldDb.inputStream().use { it.copyTo(out) } }

        writeManifest()
    }

    private fun writeManifest() {
        val producers = mapOf(
            "db-backup.abdb.zip" to "BackupControl.zipDatabaseFiles() over a container with one label and one bookmark",
            "module-backup.abmd.zip" to "BackupControl.createSingleModuleZip(book ISV)",
            "studypad-export.abdb.zip" to "exportStudyPads(label \"G2 export pad\")",
            "bookmarks-patch.sqlite3.gz" to "createPatchForDatabase on a bookmarks DB with one new bookmark",
            "raw-llm-log.txt.gz" to "RawLlmLog.gzipCompress(fixed text with ä, 𝔄, emoji)",
            "old-monolithic.db.gz" to "GZIPOutputStream over a tiny SQLite file, user_version = $OLD_DATABASE_VERSION",
        )
        val sb = StringBuilder("# L1b G2 compatibility fixtures\n\nWritten by the java.util.zip code before the ZipWriter/ZipReader swap " +
            "(generator: `G2FixtureWriterTest`, run with `G2_WRITE_FIXTURES=1`). Do not regenerate after the swap.\n")
        for ((name, producer) in producers) {
            val f = File(fixtureDir, name)
            sb.append("\n## $name\n\n- Producer: $producer\n- File SHA-256: ${sha256(f.readBytes())}\n")
            if (name.endsWith(".zip")) {
                sb.append("- Entries (name, size, SHA-256):\n")
                ZipFile(f).use { z ->
                    for (e in z.entries().asSequence()) {
                        val bytes = z.getInputStream(e).use { it.readBytes() }
                        sb.append("  - `${e.name}` ${bytes.size} ${sha256(bytes)}\n")
                    }
                }
            } else {
                val bytes = GZIPInputStream(f.inputStream()).use { it.readBytes() }
                sb.append("- Decompressed: ${bytes.size} bytes, SHA-256 ${sha256(bytes)}\n")
            }
        }
        File(fixtureDir, "fixtures.md").writeText(sb.toString())
    }
}
