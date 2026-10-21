package net.bible.service.download

import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.download.ManifestResult
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomRepositoryHttpsPolicyTest {
    @Test fun validateManifestRefusesPlainHttp() = runTest {
        val service = CustomRepositoryServiceImpl(object : ManifestFetcher {
            override suspend fun status(url: String): Int? = error("Insecure URL reached network: $url")
            override suspend fun body(url: String): String? = error("Insecure URL reached network: $url")
        })
        for (url in listOf("http://example.org/manifest.json", "http://NAS.local/manifest.json")) {
            assertEquals(ManifestResult.Invalid, service.validateManifest(url, 0), url)
        }
    }
}
