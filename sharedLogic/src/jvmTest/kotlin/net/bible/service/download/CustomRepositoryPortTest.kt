package net.bible.service.download

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.download.ManifestResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A custom https repository on a non-443 port keeps `host:port` as its stored host. */
class CustomRepositoryPortTest {
    private val manifestUrl = "https://10.0.2.2:8443/manifest.json"
    private val manifest = """{"name":"Local","description":"d","type":"sword-https","host":"10.0.2.2:8443",
        "catalogDirectory":"/","packageDirectory":"/packages"}"""

    private fun host(result: ManifestResult) = assertIs<ManifestResult.Valid>(result).repository.host

    @Test fun manifestKeepsPort() = runTest {
        val service = CustomRepositoryServiceImpl(object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = if (url == manifestUrl) manifest else null
        })
        assertEquals("10.0.2.2:8443", host(service.validateManifest(manifestUrl, 0)))
    }

    @Test fun plainFallbackKeepsPort() = runTest {
        val service = CustomRepositoryServiceImpl(object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = null
        })
        assertEquals("10.0.2.2:8443", host(service.validateManifest(manifestUrl, 0)))
    }

    @Test fun plainFallbackDefaultPortHasNoPort() = runTest {
        val service = CustomRepositoryServiceImpl(object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = null
        })
        assertEquals("example.org", host(service.validateManifest("https://example.org/manifest.json", 0)))
    }

    @Test fun plainFallbackExplicit443HasNoPort() = runTest {
        val service = CustomRepositoryServiceImpl(object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = null
        })
        assertEquals("example.org", host(service.validateManifest("https://example.org:443/manifest.json", 0)))
    }

    @Test fun plainFallbackKeepsPortOfUnderscoreHost() = runTest {
        val service = CustomRepositoryServiceImpl(object : ManifestFetcher {
            override suspend fun status(url: String): Int? = 200
            override suspend fun body(url: String): String? = null
        })
        assertEquals("my_nas:8443", host(service.validateManifest("https://my_nas:8443/manifest.json", 0)))
    }
}
