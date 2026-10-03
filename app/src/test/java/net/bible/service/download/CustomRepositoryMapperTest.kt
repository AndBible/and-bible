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

package net.bible.service.download

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.CustomRepository
import net.bible.android.view.activity.download.RepositoryData
import net.bible.android.view.activity.download.toRepositoryData
import net.bible.android.view.activity.download.toRepositoryResult
import net.bible.sharedcore.download.RepositoryResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CustomRepositoryMapperTest {

    private fun buildEntity(id: Long = 7L) = CustomRepository(
        id = id,
        name = "Test Repo",
        description = "A test repository",
        type = "sword-https",
        host = "example.com",
        catalogDirectory = "/catalog",
        packageDirectory = "/catalog/packages",
        manifestUrl = "https://example.com/manifest.json",
    )

    // --- (a) entity <-> DTO round-trip, all 8 fields ------------------------------------------

    @Test
    fun `toData toEntity round-trips all 8 fields`() {
        val entity = buildEntity()
        assertEquals(entity, entity.toData().toEntity())
    }

    @Test
    fun `toData toEntity round-trips a blank new-repository entity`() {
        val entity = CustomRepository(name = "", type = "")
        assertEquals(entity, entity.toData().toEntity())
    }

    @Test
    fun `toData toEntity round-trips a null manifestUrl`() {
        val entity = buildEntity().copy(manifestUrl = null)
        assertEquals(entity, entity.toData().toEntity())
    }

    // --- (b) RepositoryResult -> classic RepositoryData JSON parity ----------------------------

    @Test
    fun `save result maps to the SAME data JSON as classic RepositoryData(CustomRepository)`() {
        val entity = buildEntity()
        val result = RepositoryResult(repository = entity.toData(), delete = false, cancel = false)

        val actualJson = result.toRepositoryData().toJSON()
        val classicJson = RepositoryData(repository = entity, delete = false, cancel = false).toJSON()

        assertEquals(classicJson, actualJson)
    }

    @Test
    fun `delete result maps to the SAME data JSON as classic`() {
        val entity = buildEntity()
        val result = RepositoryResult(repository = entity.toData(), delete = true, cancel = false)

        val actualJson = result.toRepositoryData().toJSON()
        val classicJson = RepositoryData(repository = entity, delete = true, cancel = false).toJSON()

        assertEquals(classicJson, actualJson)
    }

    @Test
    fun `cancel result with a null repository maps to the SAME data JSON as classic blank RepositoryData`() {
        val result = RepositoryResult(repository = null, delete = false, cancel = true)

        val actualJson = result.toRepositoryData().toJSON()
        val classicJson = RepositoryData(repository = null, delete = false, cancel = true).toJSON()

        assertEquals(classicJson, actualJson)
    }

    @Test
    fun `a brand-new blank editor result matches classic RepositoryData() default JSON`() {
        val actualJson = RepositoryResult().toRepositoryData().toJSON()
        val classicJson = RepositoryData().toJSON()

        assertEquals(classicJson, actualJson)
    }

    // --- incoming classic JSON -> RepositoryResult (the reverse direction the editor reads) ----

    @Test
    fun `incoming classic RepositoryData JSON maps back to an equal RepositoryResult`() {
        val entity = buildEntity()
        val classicJson = RepositoryData(repository = entity, delete = false, cancel = false).toJSON()

        val result = RepositoryData.fromJSON(classicJson).toRepositoryResult()

        assertEquals(RepositoryResult(repository = entity.toData(), delete = false, cancel = false), result)
    }
}
