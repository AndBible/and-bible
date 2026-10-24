package net.bible.service.download

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.download.CustomRepositoryData
import net.bible.sharedcore.download.ManifestResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class CustomRepositoryManifestTest {
    @Test fun preservesMeasuredOrgJsonVerdicts() = runBlocking {
        val fixtures = linkedMapOf(
            "sword" to """{"type":"sword-https","name":"fixture","host":"example.org","catalogDirectory":"/catalog","packageDirectory":"/packages"}""",
            "mybible" to """{"type":"mybible","url":"https://example.org/manifest.json","file_name":"fixture","description":"Fixture","modules":[]}""",
            "array" to "[1,2]",
            "text" to "\"text\"",
            "numeric" to """{"type":5}""",
            "trailingcomma" to """{"type":"sword-https",}""",
            "nonJSON" to "not json",
            "empty" to "",
        )
        for ((name, body) in fixtures) {
            val result = CustomRepositoryServiceImpl(object : ManifestFetcher {
                override suspend fun status(url: String): Int? = null
                override suspend fun body(url: String): String = body
            }).validateManifest("https://example.org/manifest.json", 42)
            val expected = when (name) {
                "sword" -> ManifestResult.Valid(CustomRepositoryData(
                    id = 42, name = "fixture", type = "sword-https", host = "example.org",
                    catalogDirectory = "/catalog", packageDirectory = "/packages",
                ))
                "mybible" -> ManifestResult.Valid(CustomRepositoryData(
                    id = 42, name = "fixture", description = "Fixture", type = "mybible-https",
                    manifestUrl = "https://example.org/manifest.json",
                ))
                else -> ManifestResult.Invalid
            }
            assertEquals(name, expected, result)
        }
        for (status in listOf(404, null)) {
            val result = CustomRepositoryServiceImpl(object : ManifestFetcher {
                override suspend fun status(url: String): Int? = status
                override suspend fun body(url: String): String? = null
            }).validateManifest("https://example.org/manifest.json", 42)
            assertEquals("status=$status", ManifestResult.Invalid, result)
        }
    }
}
