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

package net.bible.sharedcore.download

import kotlinx.coroutines.test.runTest
import kotlin.test.*

private class FakeService(var repos: MutableList<CustomRepositoryData> = mutableListOf()) : CustomRepositoryService {
    var duplicate = false
    override suspend fun list() = repos.toList()
    override suspend fun upsert(repo: CustomRepositoryData): Boolean {
        if (duplicate) return false
        repos.removeAll { it.id == repo.id && repo.id != 0L }
        repos.add(if (repo.id == 0L) repo.copy(id = (repos.maxOfOrNull { it.id } ?: 0) + 1) else repo)
        return true
    }
    override suspend fun delete(repo: CustomRepositoryData) { repos.removeAll { it.id == repo.id } }
    override suspend fun validateManifest(url: String, existingId: Long) =
        if (url.startsWith("https://")) ManifestResult.Valid(CustomRepositoryData(id = existingId, name = "r", manifestUrl = url, type = "sword-https"))
        else ManifestResult.Invalid
}

class CustomRepositoryControllerTest {
    @Test fun refreshMapsRowsFromService() = runTest {
        val svc = FakeService(mutableListOf(CustomRepositoryData(id = 1, name = "A", description = "d")))
        val c = CustomRepositoryController(svc, this)
        c.refresh(); testScheduler.advanceUntilIdle()
        assertEquals(listOf(RepoRow(1, "A", "d")), c.state.value.rows)
    }
    @Test fun applyResultInsertsThenRefreshes() = runTest {
        val svc = FakeService()
        val c = CustomRepositoryController(svc, this)
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(name = "New")))
        testScheduler.advanceUntilIdle()
        assertEquals(1, c.state.value.rows.size)
    }
    @Test fun applyResultDeleteRemoves() = runTest {
        val svc = FakeService(mutableListOf(CustomRepositoryData(id = 5, name = "X")))
        val c = CustomRepositoryController(svc, this)
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(id = 5, name = "X"), delete = true))
        testScheduler.advanceUntilIdle()
        assertTrue(c.state.value.rows.isEmpty())
    }
    @Test fun applyResultCancelIsNoOp() = runTest {
        val svc = FakeService(mutableListOf(CustomRepositoryData(id = 1, name = "A")))
        val c = CustomRepositoryController(svc, this)
        c.applyResult(RepositoryResult(cancel = true)); testScheduler.advanceUntilIdle()
        assertEquals(1, c.state.value.rows.size)
    }

    // F96 (fix batch 3 §2.2.4): only a real change may force the catalogue reload.
    @Test fun onChangedFiresAfterAnInsertAndADelete() = runTest {
        val svc = FakeService(mutableListOf(CustomRepositoryData(id = 5, name = "X")))
        val c = CustomRepositoryController(svc, this)
        var changes = 0
        c.onChanged = { changes++ }
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(name = "New")))
        testScheduler.advanceUntilIdle()
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(id = 5, name = "X"), delete = true))
        testScheduler.advanceUntilIdle()
        assertEquals(2, changes)
    }

    @Test fun onChangedDoesNotFireForACancelOrADuplicate() = runTest {
        val svc = FakeService(mutableListOf(CustomRepositoryData(id = 1, name = "A")))
        val c = CustomRepositoryController(svc, this)
        var changes = 0
        c.onChanged = { changes++ }
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(id = 1, name = "A"), cancel = true))
        svc.duplicate = true
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(name = "A")))
        testScheduler.advanceUntilIdle()
        assertEquals(0, changes)
    }
    @Test fun duplicateFiresCallback() = runTest {
        val svc = FakeService().apply { duplicate = true }
        val c = CustomRepositoryController(svc, this)
        var dup: String? = null; c.onDuplicate = { dup = it }
        c.applyResult(RepositoryResult(repository = CustomRepositoryData(name = "Dup")))
        testScheduler.advanceUntilIdle()
        assertEquals("Dup", dup)
    }
}
