package net.bible.desktop

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleViewBundleTest {
    @Test
    fun extractsIndexAndAssets() {
        val dir = Files.createTempDirectory("bvb")
        try {
            val index = BibleViewBundle.extractTo(dir)
            assertTrue(index.exists(), "index.html missing")
            val refs = Regex("""(?:src|href)="([^"]+)"""").findAll(index.readText()).map { it.groupValues[1] }
                .filter { !it.contains("://") && !it.startsWith("#") && !it.startsWith("data:") }.toList()
            assertTrue(refs.isNotEmpty(), "index.html references no local assets")
            for (ref in refs) {
                val path = ref.substringBefore('?').substringBefore('#').removePrefix("./")
                assertTrue(index.parent.resolve(path).exists(), "unresolved reference $ref")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun pocDocumentIsJsonObject() {
        assertTrue(Json.parseToJsonElement(BibleViewBundle.pocDocumentJson()) is JsonObject)
    }
}
