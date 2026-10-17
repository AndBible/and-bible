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

import net.bible.service.db.reset
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

    /** The custom-repository check takes what the text field holds: `URL` trimmed control/space ends and ignored path characters. */
    @Test fun customRepositoryUrlValidationIsAsLenientAsJavaNetUrl() {
        fun oldAccepts(x: String) = try { java.net.URL(x); true } catch (e: java.net.MalformedURLException) { false }
        fun newAccepts(x: String) = try { parseLenientUrl(x); true } catch (e: IllegalArgumentException) { false }
        val lenient = listOf(
            "https://x/y ", " https://x/y", "\t https://x/y\n", "https://x/my repo/manifest.json", "https://x/a|b{c}d^e\\f`g\"h<i>j",
            "https://x/p?q=a b#f g", "https://x/100%/a%zz", "https://x/a[1]",
        )
        for (x in lenient + listOf("https://x/y", "http://x", "ftp://x", "x/y", "", "htp://x", " ", "https:x y")) {
            assertEquals("input '$x'", oldAccepts(x), newAccepts(x))
        }
        assertTrue(lenient.all { oldAccepts(it) })
        // The pinned difference that remains: URL took an empty authority, KUri does not.
        assertTrue(oldAccepts("https://"))
        assertFalse(newAccepts("https://"))
    }

    @Test fun hostAndPathMatchJavaNetUrl() {
        for (x in listOf("https://x/y", "https://host.example:8443/cat/log?x=1#f", "https://u:p@h.org/a/b/", "https://x", "https://my_host.example/dir",
            "https://x/y ", " https://h.org/my repo/m.json", "https://h.org/a|b{c}?q=1 2")) {
            val url = java.net.URL(x)
            val parsed = parseLenientUrl(x)
            assertEquals("host of '$x'", url.host, urlHost(parsed.text, parsed.uri))
            assertEquals("path of '$x'", url.path, urlPath(parsed.text))
        }
    }

    @Test fun validateManifestStaysInvalidWhenOnlyTheUrlIsUnusable() = runBlocking {
        // Every probe succeeds, so only the URL check can make these Invalid
        val reachable = object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = null
        }
        val service = CustomRepositoryServiceImpl(reachable)
        for (x in listOf("https://", "http://x", "x/y", "")) {
            assertEquals("input '$x'", ManifestResult.Invalid, service.validateManifest(x, 0))
        }
        for (x in listOf("https://x.example/y ", "https://x.example/my repo/manifest.json")) {
            val valid = service.validateManifest(x, 0) as ManifestResult.Valid
            val url = java.net.URL(x)
            assertEquals(url.host, valid.repository.host)
            assertEquals(url.path, valid.repository.catalogDirectory)
            assertEquals(url.path + "/packages", valid.repository.packageDirectory)
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
