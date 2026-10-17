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

package net.bible.android.control.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import net.bible.android.AppDialogControllerResetRule
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.BookmarkDatabase
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.files.DeliveryResult
import net.bible.sharedcore.platform.files.PickedSource
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.sharedcore.ui.dialog.AppDialogResult
import net.bible.test.DatabaseResetter
import okio.Source
import okio.source
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.sword.SwordBookPathJvm
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * L1b G2 Task 10: the restore and install paths of [BackupControl] over the `l1b-g2` fixtures (written by the
 * `java.util.zip` code) and the `old-release` fixtures (written by the released build 1117), and the filename parser.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class RestorePathsTest {
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private val testDispatcher = StandardTestDispatcher()
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private val backup by lazy { net.bible.test.testBackupControl() }
    private val strings get() = net.bible.android.platform.AndroidCoreStrings(RuntimeEnvironment.getApplication())
    private val dirs = mutableListOf<File>()
    private var wasReady = false

    @Before fun setUp() {
        SwordBookPathJvm.setDownloadDir(SharedConstants.modulesDir)
        DatabaseResetter.resetDatabase()
        wasReady = DatabaseContainer.ready
        DatabaseContainer.ready = true // the restore closes the databases only when they are ready
        Dispatchers.setMain(testDispatcher) // after the reset: the resetter needs the real Main
    }

    @After fun tearDown() {
        dialogs.cancelAll()
        Dispatchers.resetMain()
        DatabaseResetter.resetDatabase()
        DatabaseContainer.ready = wasReady
        dirs.forEach { it.deleteRecursively() }
    }

    private fun fixture(name: String): File {
        val dir = File(CommonUtils.tmpDir, "restore-paths-${System.nanoTime()}").also { it.mkdirs(); dirs += it }
        return File(dir, name.substringAfterLast('/')).also { f ->
            javaClass.classLoader!!.getResourceAsStream("l1b-g2/$name")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        }
    }

    // ---- parseBackupFiles ----

    @Test fun parseBackupFilesFormatsKnownNames() {
        val names = listOf(
            "dbBackup-1117-1-2-3-4-5-6-7-8-20261017-093005.abdb.zip", // app version, one version per database
            "dbBackup-1100-5-20240131-235959.abdb.zip",
            "dbBackup-69-20200101-000000.abdb.zip", // no per-database versions
            "dbBackup-69-20200101-120000.db", // the old monolithic backup name: not a zip backup
            "AndBibleDatabaseBackup.abdb.zip",
        )
        val parsed = parse(names)
        assertEquals(1117, parsed[0].appVersion); assertEquals("2026-10-17 09:30:05", parsed[0].displayDate)
        assertEquals(1100, parsed[1].appVersion); assertEquals("2024-01-31 23:59:59", parsed[1].displayDate)
        assertEquals(69, parsed[2].appVersion); assertEquals("2020-01-01 00:00:00", parsed[2].displayDate)
        for (i in 3..4) { assertNull(parsed[i].appVersion); assertEquals(names[i], parsed[i].displayDate) }
        assertEquals(names, parsed.map { fileNameOf(it) })
    }

    private fun parse(names: List<String>) = backup.parseBackupFiles(names.map { File("/data/backups/$it") })
    private fun fileNameOf(info: BackupControl.BackupFileInfo) = info.file.name

    // ---- the zip restore flow over fixtures ----

    private class FixtureHost(private val file: File) : BackupHost {
        val errors = mutableListOf<String>()
        override suspend fun deliver(file: String, fileName: String, mode: SaveOrShare, shareMimeType: String, saveMimeType: String, subject: String?, message: String?, chooserTitle: String): DeliveryResult = error("unused")
        override suspend fun pickBackupFile(mime: String): PickedSource? = error("unused")
        override suspend fun openInput(source: PickedSource): Source = file.source()
        override suspend fun restoreModules() = Unit
        override suspend fun showErrorReport(message: String, cause: Throwable?) { errors += message }
    }

    /** Answers the restore flow's dialogs: only the bookmarks section is selected, [restoreNotImport] answers the question. */
    private fun answer(request: AppDialogRequest, restoreNotImport: Boolean): AppDialogResult = when (request) {
        is AppDialogRequest.MultiChoice -> AppDialogResult.SelectedMany(
            request.options.filter { it.label == strings.databaseTitle(BookmarkDatabase.dbFileName) }.map { it.value },
        )
        is AppDialogRequest.Options -> AppDialogResult.Selected(request.options[if (restoreNotImport) 0 else 1].value)
        else -> AppDialogResult.Ok
    }

    private fun restoreFlow(file: File, restoreNotImport: Boolean): Pair<Boolean, FixtureHost> {
        val host = FixtureHost(file)
        var ok: Boolean? = null
        runTest(testDispatcher, timeout = 120.seconds) {
            val job = async { backup.restoreAppDatabaseFromSourceWithUI(host, PickedSource(file.path, file.name, null)) }
            while (!job.isCompleted) {
                advanceUntilIdle()
                dialogs.pending.value?.let { dialogs.respond(it.id, answer(it.request, restoreNotImport)) }
                withContext(Dispatchers.Default) { Thread.sleep(20) }
            }
            ok = job.await()
        }
        return ok!! to host
    }

    private suspend fun labelNames() = DatabaseContainer.instance.bookmarkDb.bookmarkDao().allLabelsSortedByName().map { it.name }
    private suspend fun bookmarks() = DatabaseContainer.instance.bookmarkDb.bookmarkDao().allBookmarks()

    @Test fun restoreOfTask1DbBackupFixtureRestoresTheBookmark() {
        kotlinx.coroutines.runBlocking {
            DatabaseContainer.instance.bookmarkDb.bookmarkDao().insert(BookmarkEntities.Label(name = "live-label"))
        }
        val (ok, host) = restoreFlow(fixture("db-backup.abdb.zip"), restoreNotImport = true)
        assertTrue(host.errors.toString(), ok)
        kotlinx.coroutines.runBlocking {
            assertTrue(labelNames().toString(), "G2 fixture label" in labelNames())
            assertFalse("live-label" in labelNames())
            assertEquals(listOf(5), bookmarks().map { it.kjvOrdinalStart })
        }
    }

    @Test fun oldReleaseDbBackupRestoresItsLabelsAndBookmarks() {
        val (ok, host) = restoreFlow(fixture("old-release/db-backup.abdb.zip"), restoreNotImport = true)
        assertTrue(host.errors.toString(), ok)
        kotlinx.coroutines.runBlocking {
            assertTrue("G2Fixture" in labelNames())
            assertTrue("Salvation" in labelNames())
            val all = bookmarks()
            assertEquals(14, all.size)
            assertEquals("G2 fixture bookmark note", all.single { it.kjvOrdinalStart == 33099 }.notes)
            assertTrue(all.any { it.kjvOrdinalStart == 33100 })
            val pad = DatabaseContainer.instance.bookmarkDb.bookmarkDao().allLabelsSortedByName().single { it.name == "G2Fixture" }
            assertEquals(
                listOf("G2 fixture studypad entry"),
                DatabaseContainer.instance.bookmarkDb.bookmarkDao().studyPadTextEntriesByLabelId(pad.id).map { it.text },
            )
        }
    }

    @Test fun oldReleaseStudyPadExportImports() {
        val (ok, host) = restoreFlow(fixture("old-release/studypad-export.abdb.zip"), restoreNotImport = false)
        assertTrue(host.errors.toString(), ok)
        kotlinx.coroutines.runBlocking {
            assertTrue("G2Fixture" in labelNames())
            assertEquals(setOf(33099, 33100), bookmarks().map { it.kjvOrdinalStart }.toSet())
            val pad = DatabaseContainer.instance.bookmarkDb.bookmarkDao().allLabelsSortedByName().single { it.name == "G2Fixture" }
            assertEquals(
                listOf("G2 fixture studypad entry"),
                DatabaseContainer.instance.bookmarkDb.bookmarkDao().studyPadTextEntriesByLabelId(pad.id).map { it.text },
            )
        }
    }

    @Test fun aFileThatIsNeitherZipNorSqliteIsReportedNotRestored() {
        val junk = File(fixture("db-backup.abdb.zip").parentFile, "junk.bin").apply { writeBytes(ByteArray(300) { (it * 7).toByte() }) }
        val before = kotlinx.coroutines.runBlocking { labelNames() }
        val (ok, _) = restoreFlow(junk, restoreNotImport = true)
        assertFalse(ok)
        assertEquals(before, kotlinx.coroutines.runBlocking { labelNames() })
    }

    // ---- module archives ----

    private fun installFixture(file: File): Boolean = kotlinx.coroutines.runBlocking { backup.installModuleArchive(file, null) }

    /** Installs [zip] with [initials] taken out of the registry first, and puts the original book and files back after. */
    private fun withModuleInstalled(zip: File, initials: String, confName: String, body: () -> Unit) {
        val original = Books.installed().getBook(initials) ?: error("test module $initials not installed in ~/.sword")
        val target = SwordBookPathJvm.getSwordDownloadDir()
        Books.installed().removeBook(original)
        try {
            assertTrue(installFixture(zip))
            assertNotNull(Books.installed().getBook(initials))
            body()
        } finally {
            Books.installed().getBook(initials)?.let { Books.installed().removeBook(it) }
            Books.installed().addBook(original)
            File(target, "mods.d/$confName").delete()
            File(target, "modules/texts/ztext/${initials.lowercase()}").deleteRecursively()
        }
    }

    @Test fun moduleFixtureInstallsThroughExtractAndRegister() {
        val target = SwordBookPathJvm.getSwordDownloadDir()
        withModuleInstalled(fixture("module-backup.abmd.zip"), "ISV", "isv.conf") {
            assertTrue(File(target, "mods.d/isv.conf").isFile)
            assertEquals(518203L, File(target, "modules/texts/ztext/isv/nt.bzz").length())
            assertFalse(File(target, "AndBibleBackupManifest.json").exists())
        }
    }

    @Test fun oldReleaseModuleBackupInstalls() {
        val target = SwordBookPathJvm.getSwordDownloadDir()
        withModuleInstalled(fixture("old-release/module-backup.abmd.zip"), "KJV", "kjv.conf") {
            assertTrue(File(target, "mods.d/kjv.conf").isFile)
            assertTrue(File(target, "modules/texts/ztext/kjv").listFiles()!!.isNotEmpty())
            assertFalse(File(target, "AndBibleBackupManifest.json").exists())
        }
    }

    @Test fun anArchiveThatIsNotAZipFailsToInstallWithoutACrash() {
        val junk = File(fixture("module-backup.abmd.zip").parentFile, "junk.abmd.zip").apply { writeBytes(ByteArray(300) { (it * 7).toByte() }) }
        assertFalse(installFixture(junk))
    }

    @Test fun aMissingArchiveFailsToInstallWithoutACrash() {
        assertFalse(installFixture(File(CommonUtils.tmpDir, "no-such-archive.abmd.zip")))
    }

    @Test fun aTruncatedArchiveFailsToInstallWithoutACrash() {
        val full = fixture("module-backup.abmd.zip")
        val cut = File(full.parentFile, "cut.abmd.zip").apply { writeBytes(full.readBytes().copyOf(1000)) }
        assertFalse(installFixture(cut))
    }
}
