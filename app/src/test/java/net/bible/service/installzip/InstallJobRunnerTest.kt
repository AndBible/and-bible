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

package net.bible.service.installzip

import android.net.Uri
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import net.bible.android.SharedConstants
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.backup.BackupControl
import net.bible.service.sword.backgroundimage.BACKGROUND_IMAGE_DIR
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class InstallJobRunnerTest {
    private class FakeCommitter : InstallCommitter {
        val calls = mutableListOf<String>()
        var epubOk = true; var sqliteThrows = false
        override suspend fun commitSwordZip(localFile: File, totalEntries: Int, onProgress: (Int) -> Unit) { calls += "sword"; onProgress(100) }
        override suspend fun commitEpub(localFile: File, displayName: String, deleteExisting: Boolean): Boolean { calls += "epub"; return epubOk }
        override suspend fun commitSqlite(localFile: File, type: SqliteBookType, displayName: String) { calls += "sqlite"; if (sqliteThrows) throw InvalidInstallFile(displayName) }
        override suspend fun commitTtf(localFile: File, displayName: String) { calls += "ttf" }
        override suspend fun commitCsv(localFile: File, displayName: String) { calls += "csv" }
        override suspend fun commitBackgroundImage(localFile: File, fileName: String) { calls += "img" }
        override suspend fun commitStudyPad(unzipFolder: File) { calls += "studypad" }
    }
    private fun tmp() = File.createTempFile("acq", ".bin").also { it.writeText("data") }
    private fun runner(c: InstallCommitter) = InstallJobRunner(InstallInspector { BackupControl.AbDbFileType.ZIP }, c)
    private fun sqliteRunner(c: InstallCommitter) = InstallJobRunner(InstallInspector { BackupControl.AbDbFileType.SQLITE3 }, c)
    private fun src(name: String, mime: String? = null) = InstallSource(Uri.parse("content://x/$name"), null, name, mime)

    /** Mirrors `InstallInspector.overwriteName`: `SharedConstants.modulesDir/<subDir>/<name>`. */
    private fun overwriteTarget(subDir: String, name: String) = File(File(SharedConstants.modulesDir, subDir), name)

    private val createdOverwriteTargets = mutableListOf<File>()

    /** Pre-creates the file [InstallInspector] checks for, so the plan's `overwriteName` is non-null. */
    private fun forceOverwrite(subDir: String, name: String): File {
        val target = overwriteTarget(subDir, name)
        target.parentFile?.mkdirs()
        target.writeText("existing")
        createdOverwriteTargets += target
        return target
    }

    @After
    fun cleanUpOverwriteTargets() {
        createdOverwriteTargets.forEach { it.delete() }
        createdOverwriteTargets.clear()
    }

    @Test fun `sword zip no conflict installs`() = runBlocking {
        val c = FakeCommitter(); val phases = mutableListOf<InstallPhase>(); val f = tmp()
        val terminal = runner(c).runJob(src("m.zip"), f, { "d".byteInputStream() },
            isStudyPadExport = { false }, studyPadStats = { error("") },
            swordZipScan = { InstallInspector.SwordZipScan(emptyList(), 3, false, false) },
            epubUpgradeCheck = { false }, onPhase = { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("sword"), c.calls)
        assertFalse(f.exists())  // temp cleaned
        assertTrue(phases.any { it is InstallPhase.Committing })
    }

    @Test fun `overwrite declined cancels without commit`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("m.zip"), f, { "d".byteInputStream() },
            { false }, { error("") },
            swordZipScan = { InstallInspector.SwordZipScan(listOf("mods.d/x.conf"), 3, false, false) },
            { false }, {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `overwrite accepted commits`() = runBlocking {
        val c = FakeCommitter(); val f = tmp(); val phases = mutableListOf<InstallPhase>()
        val terminal = runner(c).runJob(src("m.zip"), f, { "d".byteInputStream() },
            { false }, { error("") },
            swordZipScan = { InstallInspector.SwordZipScan(listOf("mods.d/x.conf"), 3, false, false) },
            { false }, { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("sword"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.Overwrite })
        // Phase ORDER, not just presence: Acquiring -> Inspecting -> AwaitingDecision -> Committing -> Done.
        // `distinct()` collapses the repeated Acquiring(percent) progress ticks while preserving first-seen order.
        assertEquals(
            listOf(
                InstallPhase.Acquiring::class, InstallPhase.Inspecting::class,
                InstallPhase.AwaitingDecision::class, InstallPhase.Committing::class, InstallPhase.Done::class,
            ),
            phases.map { it::class }.distinct(),
        )
    }

    @Test fun `invalid zip errors`() = runBlocking {
        val c = FakeCommitter()
        val terminal = runner(c).runJob(src("j.zip"), tmp(), { "d".byteInputStream() },
            { false }, { error("") }, { InstallInspector.SwordZipScan(emptyList(), 0, false, true) }, { false }, {}, { true })
        assertTrue(terminal is InstallPhase.Error)
        assertEquals(R.string.sqlite_invalid_file, (terminal as InstallPhase.Error).messageKey)
        assertTrue(c.calls.isEmpty())
    }

    @Test fun `epub upgrade accepted commits epub with delete existing`() = runBlocking {
        val c = FakeCommitter(); val phases = mutableListOf<InstallPhase>(); val f = tmp()
        val terminal = runner(c).runJob(src("b.epub", "application/epub+zip"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, epubUpgradeCheck = { true },
            onPhase = { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("epub"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.EpubUpgrade })
    }

    @Test fun `epub upgrade declined cancels without commit`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("b.epub", "application/epub+zip"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, epubUpgradeCheck = { true },
            onPhase = {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    /**
     * Finding I1: a zip whose content is actually an epub (`InstallPlan.EpubFromZip`) must go
     * through the SAME upgrade-confirmation gate as a mime-typed epub -- declining must cancel
     * WITHOUT deleting/committing (parity with classic `InstallZip.installEpub`'s
     * `documentUpgradeConfirmation` gate, reached via the `EpubFile` exception route).
     */
    @Test fun `epub-from-zip upgrade declined cancels without commit`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("e.zip"), f, { "d".byteInputStream() },
            { false }, { error("") }, swordZipScan = { InstallInspector.SwordZipScan(emptyList(), 0, isEpub = true, invalid = false) },
            epubUpgradeCheck = { true }, onPhase = {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue("commitEpub must NOT run when the upgrade is declined", c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `epub-from-zip upgrade accepted commits epub with delete existing`() = runBlocking {
        val c = FakeCommitter(); val phases = mutableListOf<InstallPhase>(); val f = tmp()
        val terminal = runner(c).runJob(src("e.zip"), f, { "d".byteInputStream() },
            { false }, { error("") }, swordZipScan = { InstallInspector.SwordZipScan(emptyList(), 0, isEpub = true, invalid = false) },
            epubUpgradeCheck = { true }, onPhase = { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("epub"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.EpubUpgrade })
    }

    @Test fun `epub-from-zip with no upgrade needed commits without asking`() = runBlocking {
        val c = FakeCommitter(); val phases = mutableListOf<InstallPhase>(); val f = tmp()
        val terminal = runner(c).runJob(src("e.zip"), f, { "d".byteInputStream() },
            { false }, { error("") }, swordZipScan = { InstallInspector.SwordZipScan(emptyList(), 0, isEpub = true, invalid = false) },
            epubUpgradeCheck = { false }, onPhase = { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("epub"), c.calls)
        assertFalse(phases.any { it is InstallPhase.AwaitingDecision })
    }

    @Test fun `epub rejected by discovery scanner errors`() = runBlocking {
        val c = FakeCommitter(); c.epubOk = false; val phases = mutableListOf<InstallPhase>()
        val terminal = runner(c).runJob(src("b.epub", "application/epub+zip"), tmp(), { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, epubUpgradeCheck = { false }, onPhase = { phases += it }, awaitDecision = { true })
        assertTrue(terminal is InstallPhase.Error)
        assertEquals(R.string.sqlite_invalid_file, (terminal as InstallPhase.Error).messageKey)
        // A failed commit must never emit the Committing(100) success side effect (finding 3): a
        // `commitEpub` returning false is a failure just like a thrown exception.
        assertFalse(phases.any { it is InstallPhase.Committing && it.percent == 100 })
    }

    @Test fun `studypad accepted commits and keeps unzip folder handling to committer`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val unzipFolder = File.createTempFile("unzip", "").also { it.delete(); it.mkdirs() }
        val terminal = runner(c).runJob(src("sp.abpb"), f, { "d".byteInputStream() },
            isStudyPadExport = { true }, studyPadStats = { "3 bookmarks" to unzipFolder },
            swordZipScan = { error("") }, epubUpgradeCheck = { false }, onPhase = {}, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("studypad"), c.calls)
        unzipFolder.deleteRecursively()
        Unit
    }

    @Test fun `studypad declined cancels and deletes unzip folder`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val unzipFolder = File.createTempFile("unzip", "").also { it.delete(); it.mkdirs() }
        val terminal = runner(c).runJob(src("sp.abpb"), f, { "d".byteInputStream() },
            isStudyPadExport = { true }, studyPadStats = { "3 bookmarks" to unzipFolder },
            swordZipScan = { error("") }, epubUpgradeCheck = { false }, onPhase = {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse("declined studypad import must delete its unzip folder", unzipFolder.exists())
    }

    @Test fun `cancellation mid job deletes temp and skips commit`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val job = launch {
            runner(c).runJob(src("m.zip"), f, { awaitCancellation() /* block acquire */ },
                { false }, { error("") }, { InstallInspector.SwordZipScan(emptyList(),3,false,false) }, { false }, {}, { true })
        }
        yield(); job.cancelAndJoin()
        assertTrue(c.calls.isEmpty()); assertFalse(f.exists())
    }

    @Test fun `IOException while acquiring maps to error_occurred and deletes temp`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("m.zip"), f, { throw IOException("boom") },
            { false }, { error("") }, { error("") }, { false }, {}, { true })
        assertTrue(terminal is InstallPhase.Error)
        assertEquals(R.string.error_occurred, (terminal as InstallPhase.Error).messageKey)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `sqlite header gate rejection maps to sqlite_invalid_file`() = runBlocking {
        val c = FakeCommitter(); c.sqliteThrows = true
        val terminal = InstallJobRunner(InstallInspector { BackupControl.AbDbFileType.SQLITE3 }, c).runJob(
            src("b.sqlite3"), tmp(), { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, {}, { true })
        assertTrue(terminal is InstallPhase.Error)
        assertEquals(R.string.sqlite_invalid_file, (terminal as InstallPhase.Error).messageKey)
        assertEquals(listOf("sqlite"), c.calls)
    }

    @Test fun `sqlite overwrite accepted commits`() = runBlocking {
        forceOverwrite("mybible", "b.sqlite3")
        val c = FakeCommitter(); val f = tmp(); val phases = mutableListOf<InstallPhase>()
        val terminal = sqliteRunner(c).runJob(src("b.sqlite3"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("sqlite"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.Overwrite })
    }

    @Test fun `sqlite overwrite declined cancels without commit`() = runBlocking {
        forceOverwrite("mybible", "b.sqlite3")
        val c = FakeCommitter(); val f = tmp()
        val terminal = sqliteRunner(c).runJob(src("b.sqlite3"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `ttf overwrite accepted commits`() = runBlocking {
        forceOverwrite("ttf", "Font.ttf")
        val c = FakeCommitter(); val f = tmp(); val phases = mutableListOf<InstallPhase>()
        val terminal = runner(c).runJob(src("Font.ttf"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("ttf"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.Overwrite })
    }

    @Test fun `ttf overwrite declined cancels without commit`() = runBlocking {
        forceOverwrite("ttf", "Font.ttf")
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("Font.ttf"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `csv overwrite accepted commits`() = runBlocking {
        forceOverwrite("prompts", "prompts.csv")
        val c = FakeCommitter(); val f = tmp(); val phases = mutableListOf<InstallPhase>()
        val terminal = runner(c).runJob(src("prompts.csv"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("csv"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.Overwrite })
    }

    @Test fun `csv overwrite declined cancels without commit`() = runBlocking {
        forceOverwrite("prompts", "prompts.csv")
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("prompts.csv"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `background image overwrite accepted commits`() = runBlocking {
        forceOverwrite(BACKGROUND_IMAGE_DIR, "pic.png")
        val c = FakeCommitter(); val f = tmp(); val phases = mutableListOf<InstallPhase>()
        val terminal = runner(c).runJob(src("pic.png"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, { phases += it }, awaitDecision = { true })
        assertEquals(InstallPhase.Done, terminal)
        assertEquals(listOf("img"), c.calls)
        assertTrue(phases.any { it is InstallPhase.AwaitingDecision && it.request is DecisionRequest.Overwrite })
    }

    @Test fun `background image overwrite declined cancels without commit`() = runBlocking {
        forceOverwrite(BACKGROUND_IMAGE_DIR, "pic.png")
        val c = FakeCommitter(); val f = tmp()
        val terminal = runner(c).runJob(src("pic.png"), f, { "d".byteInputStream() },
            { false }, { error("") }, { error("") }, { false }, {}, awaitDecision = { false })
        assertEquals(InstallPhase.Cancelled, terminal)
        assertTrue(c.calls.isEmpty())
        assertFalse(f.exists())
    }

    @Test fun `temp file is always deleted even on invalid plan`() = runBlocking {
        val c = FakeCommitter(); val f = tmp()
        runner(c).runJob(src("j.zip"), f, { "d".byteInputStream() },
            { false }, { error("") }, { InstallInspector.SwordZipScan(emptyList(), 0, false, true) }, { false }, {}, { true })
        assertFalse(f.exists())
    }
}
