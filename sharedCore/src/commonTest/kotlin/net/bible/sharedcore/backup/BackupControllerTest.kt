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

package net.bible.sharedcore.backup

import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class FakeBackupService(
    initialToggles: Map<ToggleKind, Boolean> = emptyMap(),
    initialFiles: List<BackupFileRow> = emptyList(),
    initialResettableDbs: List<ResetDbRow> = emptyList(),
    initialCrash: CrashInfo? = null,
) : BackupService {
    var loadCallCount = 0
    val toggleCalls = mutableListOf<Pair<ToggleKind, Boolean>>()
    var backupCallCount = 0
    var restoreCallCount = 0
    val backupToggles = mutableListOf<Map<ToggleKind, Boolean>>()
    val restoreToggles = mutableListOf<Map<ToggleKind, Boolean>>()
    val exportCalls = mutableListOf<String>()
    val restoreFileCalls = mutableListOf<String>()
    val resetDbCalls = mutableListOf<String>()

    var toggles = initialToggles
    var files = initialFiles
    var resettableDbs = initialResettableDbs
    var crash = initialCrash

    override suspend fun load(): BackupState {
        loadCallCount++
        return BackupState(toggles = toggles, backupFiles = files, resettableDbs = resettableDbs, crash = crash)
    }
    override fun setToggle(kind: ToggleKind, value: Boolean) {
        toggleCalls.add(kind to value)
    }
    override suspend fun backup(toggles: Map<ToggleKind, Boolean>) { backupCallCount++; backupToggles += toggles }
    override suspend fun restore(toggles: Map<ToggleKind, Boolean>) { restoreCallCount++; restoreToggles += toggles }
    override suspend fun exportFile(token: String) { exportCalls.add(token) }
    override suspend fun restoreFile(token: String) { restoreFileCalls.add(token) }
    override suspend fun resetDb(dbFileName: String) { resetDbCalls.add(dbFileName) }
}

class BackupControllerTest {
    /** F103: before DB init the service cannot persist, so load() keeps answering defaults. */
    @Test fun aToggleSurvivesAReloadThatStillReturnsTheDefaults() = runTest {
        val svc = FakeBackupService(initialToggles = mapOf(ToggleKind.RestoreDocuments to false, ToggleKind.RestoreDatabase to true))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()

        c.setToggle(ToggleKind.RestoreDocuments, true)
        c.load(); testScheduler.advanceUntilIdle()          // ON_RESUME after the file picker

        assertEquals(true, c.state.value.toggles[ToggleKind.RestoreDocuments])
    }

    @Test fun restoreDispatchesTheScreensToggles() = runTest {
        val svc = FakeBackupService(initialToggles = mapOf(ToggleKind.RestoreDocuments to false, ToggleKind.RestoreDatabase to true))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()
        c.setToggle(ToggleKind.RestoreDocuments, true)
        c.setToggle(ToggleKind.RestoreDatabase, false)

        c.restore(); testScheduler.advanceUntilIdle()

        assertEquals(false, svc.restoreToggles.single()[ToggleKind.RestoreDatabase])
        assertEquals(true, svc.restoreToggles.single()[ToggleKind.RestoreDocuments])
    }

    @Test fun backupDispatchesTheScreensToggles() = runTest {
        val svc = FakeBackupService(initialToggles = mapOf(ToggleKind.BackupDatabase to true))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()
        c.setToggle(ToggleKind.BackupDocuments, true)

        c.backup(); testScheduler.advanceUntilIdle()

        assertEquals(true, svc.backupToggles.single()[ToggleKind.BackupDocuments])
        assertEquals(true, svc.backupToggles.single()[ToggleKind.BackupDatabase])
    }

    @Test fun loadPopulatesStateFromService() = runTest {
        val svc = FakeBackupService(
            initialToggles = mapOf(ToggleKind.BackupDatabase to true, ToggleKind.RestoreDocuments to false),
            initialFiles = listOf(BackupFileRow("tok-1", "2026-07-24", "1 MB")),
            initialResettableDbs = listOf(ResetDbRow("bookmarks.sqlite3", "Bookmarks")),
            initialCrash = CrashInfo("2026-07-20", "boom"),
        )
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()
        assertEquals(mapOf(ToggleKind.BackupDatabase to true, ToggleKind.RestoreDocuments to false), c.state.value.toggles)
        assertEquals(listOf(BackupFileRow("tok-1", "2026-07-24", "1 MB")), c.state.value.backupFiles)
        assertEquals(listOf(ResetDbRow("bookmarks.sqlite3", "Bookmarks")), c.state.value.resettableDbs)
        assertEquals(CrashInfo("2026-07-20", "boom"), c.state.value.crash)
    }

    @Test fun setToggleFlipsStateAndPersists() = runTest {
        val svc = FakeBackupService(initialToggles = mapOf(ToggleKind.BackupDatabase to true))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()

        c.setToggle(ToggleKind.BackupApp, true)

        assertEquals(true, c.state.value.toggles[ToggleKind.BackupApp])
        // Untouched entries survive the merge.
        assertEquals(true, c.state.value.toggles[ToggleKind.BackupDatabase])
        assertEquals(listOf(ToggleKind.BackupApp to true), svc.toggleCalls)
    }

    @Test fun setToggleFalseIsAlsoRecorded() = runTest {
        val svc = FakeBackupService()
        val c = BackupController(svc, this)
        c.setToggle(ToggleKind.RestoreDatabase, false)
        assertEquals(false, c.state.value.toggles[ToggleKind.RestoreDatabase])
        assertEquals(listOf(ToggleKind.RestoreDatabase to false), svc.toggleCalls)
    }

    @Test fun backupDelegatesToService() = runTest {
        val svc = FakeBackupService()
        val c = BackupController(svc, this)
        c.backup(); testScheduler.advanceUntilIdle()
        assertEquals(1, svc.backupCallCount)
    }

    @Test fun backupDoesNotTriggerReload() = runTest {
        val svc = FakeBackupService(initialFiles = listOf(BackupFileRow("old", "d", "x")))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()
        assertEquals(1, svc.loadCallCount)

        c.backup(); testScheduler.advanceUntilIdle()

        assertEquals(1, svc.loadCallCount)
    }

    @Test fun restoreDelegatesToServiceAndReloads() = runTest {
        val svc = FakeBackupService(initialFiles = listOf(BackupFileRow("old", "d", "x")))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()
        assertEquals(1, c.state.value.backupFiles.size)

        svc.files = listOf(BackupFileRow("old", "d", "x"), BackupFileRow("new", "d2", "y"))
        c.restore(); testScheduler.advanceUntilIdle()

        assertEquals(1, svc.restoreCallCount)
        assertEquals(2, c.state.value.backupFiles.size)
        assertEquals(2, svc.loadCallCount)
    }

    @Test fun exportFileDelegatesToServiceAndReloads() = runTest {
        val svc = FakeBackupService(initialFiles = listOf(BackupFileRow("tok-1", "d", "x")))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()

        svc.files = emptyList()
        c.exportFile("tok-1"); testScheduler.advanceUntilIdle()

        assertEquals(listOf("tok-1"), svc.exportCalls)
        assertTrue(c.state.value.backupFiles.isEmpty())
        assertEquals(2, svc.loadCallCount)
    }

    @Test fun restoreFileDelegatesToServiceAndReloads() = runTest {
        val svc = FakeBackupService(initialFiles = listOf(BackupFileRow("tok-2", "d", "x")))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()

        svc.files = listOf(BackupFileRow("tok-2", "d2", "x2"))
        c.restoreFile("tok-2"); testScheduler.advanceUntilIdle()

        assertEquals(listOf("tok-2"), svc.restoreFileCalls)
        assertEquals("d2", c.state.value.backupFiles.single().displayDate)
        assertEquals(2, svc.loadCallCount)
    }

    @Test fun resetDbDelegatesToServiceAndReloads() = runTest {
        val svc = FakeBackupService(initialResettableDbs = listOf(ResetDbRow("bookmarks.sqlite3", "Bookmarks")))
        val c = BackupController(svc, this)
        c.load(); testScheduler.advanceUntilIdle()

        svc.resettableDbs = emptyList()
        c.resetDb("bookmarks.sqlite3"); testScheduler.advanceUntilIdle()

        assertEquals(listOf("bookmarks.sqlite3"), svc.resetDbCalls)
        assertTrue(c.state.value.resettableDbs.isEmpty())
        assertEquals(2, svc.loadCallCount)
    }
}
