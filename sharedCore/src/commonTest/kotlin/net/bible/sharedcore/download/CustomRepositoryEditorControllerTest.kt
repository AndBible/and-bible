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

class CustomRepositoryEditorControllerTest {
    private fun svc() = object : CustomRepositoryService {
        override suspend fun list() = emptyList<CustomRepositoryData>()
        override suspend fun upsert(repo: CustomRepositoryData) = true
        override suspend fun delete(repo: CustomRepositoryData) {}
        override suspend fun validateManifest(url: String, existingId: Long) =
            if (url == "https://ok/") ManifestResult.Valid(
                CustomRepositoryData(id = existingId, name = "ok", description = "desc", packageDirectory = "/p", manifestUrl = url, type = "sword-https"))
            else ManifestResult.Invalid
    }
    @Test fun validUrlProducesValidStateAndCanSave() = runTest {
        val c = CustomRepositoryEditorController(svc(), this, RepositoryResult())
        c.setUrl("https://ok/"); testScheduler.advanceUntilIdle()
        assertEquals(Validation.Valid, c.state.value.validation)
        assertTrue(c.state.value.canSave)
        assertEquals("/p", c.state.value.packageDirectory)
    }
    @Test fun invalidUrlBlocksSave() = runTest {
        val c = CustomRepositoryEditorController(svc(), this, RepositoryResult())
        c.setUrl("http://bad"); testScheduler.advanceUntilIdle()
        assertEquals(Validation.Invalid, c.state.value.validation)
        assertFalse(c.state.value.canSave)
    }
    @Test fun editedPackageDirMakesDirtyAndFlowsToResult() = runTest {
        val c = CustomRepositoryEditorController(svc(), this, RepositoryResult())
        c.setUrl("https://ok/"); testScheduler.advanceUntilIdle()
        c.setPackageDir("/custom")
        assertTrue(c.state.value.isDirty)
        assertEquals("/custom", c.buildSaveResult().repository?.packageDirectory)
    }
    @Test fun existingRepoEnablesDeleteResult() = runTest {
        val c = CustomRepositoryEditorController(svc(), this, RepositoryResult(repository = CustomRepositoryData(id = 9, name = "e", manifestUrl = "https://ok/")))
        testScheduler.advanceUntilIdle()
        assertTrue(c.state.value.isExisting)
        assertTrue(c.buildDeleteResult().delete)
        assertEquals(9, c.buildDeleteResult().repository?.id)
    }
    @Test fun cancelResultCarriesCancelFlag() = runTest {
        val c = CustomRepositoryEditorController(svc(), this, RepositoryResult())
        assertTrue(c.buildCancelResult().cancel)
    }
}
