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

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.ManifestResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A duplicate custom repository name is reported as `false`, not thrown. With the bundled SQLite driver (D1 Task 17)
 * the unique-index violation arrives as a plain `SQLiteException`, no longer the framework's
 * `SQLiteConstraintException` the service used to catch: before the fix this upsert crashed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CustomRepositoryServiceImplTest {
    @Before fun setUp() = DatabaseContainer.reset()
    @After fun tearDown() = DatabaseContainer.reset()

    @Test fun aDuplicateNameIsRejectedWithFalseOnTheProductionDriver() = runBlocking {
        val service = CustomRepositoryServiceImpl(org.koin.java.KoinJavaComponent.get(ManifestFetcher::class.java))
        val repo = CustomRepositoryData(name = "d1-dup-repo", type = "HTTPS", host = "example.org")
        assertTrue(service.upsert(repo))
        assertFalse(service.upsert(repo))
        assertEquals(1, service.list().count { it.name == "d1-dup-repo" })
    }

    @Test fun onlyConstraintErrorsCountAsViolations() {
        assertTrue(isConstraintViolation(SQLiteException("Error code: 2067, message: UNIQUE constraint failed: CustomRepository.name")))
        assertFalse(isConstraintViolation(SQLiteException("Error code: 26, message: file is not a database")))
    }

    /** `java.net.URL(x)` accepted only an absolute URL with a known protocol; [parseKnownUrl] keeps that. */
    @Test fun urlValidationMatchesJavaNetUrl() {
        fun oldAccepts(x: String) = try { java.net.URL(x); true } catch (e: java.net.MalformedURLException) { false }
        fun newAccepts(x: String) = try { parseKnownUrl(x); true } catch (e: IllegalArgumentException) { false }
        for (x in listOf("https://x/y", "http://x", "ftp://x", "x/y", "", "htp://x")) {
            assertEquals("input '$x'", oldAccepts(x), newAccepts(x))
        }
        assertEquals(listOf(true, true, true, false, false, false), listOf("https://x/y", "http://x", "ftp://x", "x/y", "", "htp://x").map(::newAccepts))
        // The one pinned difference: URL took an empty authority, KUri does not (validateManifest still ends Invalid).
        assertTrue(oldAccepts("https://"))
        assertFalse(newAccepts("https://"))
    }

    @Test fun hostAndPathMatchJavaNetUrl() {
        for (x in listOf("https://x/y", "https://host.example:8443/cat/log?x=1#f", "https://u:p@h.org/a/b/", "https://x", "https://my_host.example/dir")) {
            val url = java.net.URL(x)
            val uri = parseKnownUrl(x)
            assertEquals("host of $x", url.host, urlHost(x, uri))
            assertEquals("path of $x", url.path, uri.getRawPath())
        }
    }

    @Test fun validateManifestStaysInvalidForUnusableUrls() = runBlocking {
        val none = object : ManifestFetcher {
            override suspend fun status(url: String): Int? = null
            override suspend fun body(url: String): String? = null
        }
        val service = CustomRepositoryServiceImpl(none)
        for (x in listOf("https://", "https://x y/z", "http://x", "x/y", "")) {
            assertEquals("input '$x'", ManifestResult.Invalid, service.validateManifest(x, 0))
        }
    }

    @Test fun validateManifestBuildsHostAndPathFromAReachableCatalog() = runBlocking {
        val reachable = object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = null
        }
        val valid = CustomRepositoryServiceImpl(reachable).validateManifest("https://my_host.example/cat/dir", 7) as ManifestResult.Valid
        assertEquals("my_host.example", valid.repository.host)
        assertEquals("/cat/dir", valid.repository.catalogDirectory)
        assertEquals("/cat/dir/packages", valid.repository.packageDirectory)
    }
}
