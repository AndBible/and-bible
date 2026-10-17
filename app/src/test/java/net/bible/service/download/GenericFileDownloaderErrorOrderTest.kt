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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.util.Random
import java.util.TreeSet

/** [GenericFileDownloader.errors] must list failed sources in the order the former `TreeSet<URI>` did. */
class GenericFileDownloaderErrorOrderTest {
    private fun assertSameOrderAsTreeSet(uris: Collection<String>) {
        val expected = TreeSet<URI>().apply { uris.forEach { add(URI(it)) } }.map { it.toString() }
        val actual = uris.map { URI(it) }.toSet().sortedWith(GenericFileDownloader.uriOrder).map { it.toString() }
        assertEquals(expected, actual)
    }

    @Test fun downloadedDataFilesAndManifests() = assertSameOrderAsTreeSet(listOf(
        "https://andbible.github.io/data/recommended.json",
        "https://andbible.github.io/data/bad.json",
        "https://andbible.github.io/data/default.json",
        "https://andbible.github.io/data/pseudo_books.json",
        "https://www.crosswire.org/ftpmirror/pub/sword/raw/mods.d.tar.gz",
        "https://www.ebible.org/sword/mods.d.tar.gz",
        "https://mybible.zone/repo/manifest.json",
        "https://app.mybible.zone/repo/manifest.json",
        "http://127.0.0.1:8080/manifest",
    ))

    @Test fun toStringOrderDiffersFromUriOrderForPrefixHosts() = assertSameOrderAsTreeSet(listOf(
        "https://a.com/x", "https://a.com.au/x", "https://a.com:8080/x", "https://a.com:80/x", "https://a.co/x",
    ))

    @Test fun schemeAndHostIgnoreCasePathAndQueryDoNot() = assertSameOrderAsTreeSet(listOf(
        "HTTPS://Example.org/a", "https://example.ORG/b", "https://example.org/A", "https://example.org/a?x=1",
        "https://example.org/a?x=0", "https://example.org/a#f", "http://example.org/a", "ftp://example.org/a",
        "https://user@example.org/a", "https://example.org", "https://example.org/",
    ))

    @Test fun randomised() {
        val random = Random(42)
        val schemes = listOf("http", "https", "HTTP", "ftp")
        val hosts = listOf("a.com", "A.com", "a.com.au", "b.org", "127.0.0.1", "h-1.example.net")
        val ports = listOf("", ":80", ":443", ":8080")
        val paths = listOf("", "/", "/a", "/a/b", "/A", "/a.json")
        val queries = listOf("", "?q=1", "?q=2")
        val uris = (1..300).map {
            "${schemes[random.nextInt(schemes.size)]}://${hosts[random.nextInt(hosts.size)]}${ports[random.nextInt(ports.size)]}" +
                "${paths[random.nextInt(paths.size)]}${queries[random.nextInt(queries.size)]}"
        }
        assertSameOrderAsTreeSet(uris)
    }
}
