package net.bible.service.llm

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.test.KoinFactorySnapshot
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The on-disk model cache of [DynamicModelService] must behave as it did on `java.io.File`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DynamicModelServiceCacheTest {
    private lateinit var dir: File
    private lateinit var envSnapshot: KoinFactorySnapshot

    private val modelsJson = """{"data":[{"id":"b/model-b","name":"B","pricing":{"prompt":"0.000001","completion":"0.000002"}},{"id":"a/model-a"}]}"""

    @Before fun setUp() {
        dir = File.createTempFile("modelcache", "").also { it.delete(); it.mkdirs() }
        val koin = GlobalContext.get()
        envSnapshot = KoinFactorySnapshot.of<AppEnvironment>()
        val realEnv = koin.get<AppEnvironment>()
        koin.loadModules(listOf(module {
            single<AppEnvironment> { object : AppEnvironment by realEnv { override val cacheDir = dir.path } }
        }))
        resetServiceState()
    }

    @After fun tearDown() {
        try {
            resetServiceState()
        } finally {
            envSnapshot.restore()
            dir.deleteRecursively()
        }
    }

    /** The object keeps its in-memory cache across tests; clear it through reflection. */
    private fun resetServiceState() {
        val cls = DynamicModelService::class.java
        cls.getDeclaredField("cache").apply { isAccessible = true }.get(DynamicModelService).let { (it as MutableMap<*, *>).clear() }
        cls.getDeclaredField("allDiskCachesLoaded").apply { isAccessible = true }.setBoolean(DynamicModelService, false)
    }

    /** The private disk write of a successful fetch (the HTTP part needs a transport the object caches globally). */
    private fun saveToDisk(providerId: String, json: String) {
        DynamicModelService::class.java.getDeclaredMethod("saveToDisk", String::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(DynamicModelService, providerId, json)
    }

    @Test fun savedModelsCacheFileLivesUnderCacheDirWithProviderName() {
        saveToDisk("OPENAI", modelsJson)
        val file = File(dir, "models_cache_OPENAI.json")
        assertTrue(file.exists())
        assertEquals(modelsJson, file.readText())
        assertEquals(listOf("models_cache_OPENAI.json"), dir.list()!!.toList())
        // Overwrites rather than appends.
        saveToDisk("OPENAI", "{}")
        assertEquals("{}", file.readText())
        assertFalse(DynamicModelService.needsRefresh("OPENAI"))
    }

    @Test fun cachedModelsAreLoadedFromDiskOnFirstAccess() {
        File(dir, "models_cache_CUSTOM-1.json").writeText(modelsJson)
        val models = DynamicModelService.getCachedModels("CUSTOM-1")
        assertEquals(listOf("a/model-a", "b/model-b"), models!!.map { it.id })
        assertNull(DynamicModelService.getCachedModels("MISSING"))
    }

    @Test fun unreadableOrEmptyCacheYieldsNull() {
        File(dir, "models_cache_BAD.json").writeText("not json")
        assertNull(DynamicModelService.getCachedModels("BAD"))
        File(dir, "models_cache_EMPTY.json").writeText("""{"data":[]}""")
        assertNull(DynamicModelService.getCachedModels("EMPTY"))
    }

    @Test fun expiryUsesTheFileModificationTime() {
        val file = File(dir, "models_cache_P.json").apply { writeText(modelsJson) }
        val eightDaysAgo = (System.currentTimeMillis() / 1000 - 8 * 24 * 3600) * 1000
        assertTrue(file.setLastModified(eightDaysAgo))
        assertEquals(eightDaysAgo, DynamicModelService.cacheTimestamp("P"))
        assertTrue(DynamicModelService.needsRefresh("P"))
        val oneDayAgo = (System.currentTimeMillis() / 1000 - 24 * 3600) * 1000
        assertTrue(file.setLastModified(oneDayAgo))
        resetServiceState()
        assertEquals(oneDayAgo, DynamicModelService.cacheTimestamp("P"))
        assertFalse(DynamicModelService.needsRefresh("P"))
        assertEquals(0L, DynamicModelService.cacheTimestamp("NONE"))
        assertTrue(DynamicModelService.needsRefresh("NONE"))
    }

    @Test fun allDiskCachesListingMatchesPrefixAndSuffixOnly() {
        File(dir, "models_cache_A.json").writeText(modelsJson)
        File(dir, "models_cache_B.json").writeText(modelsJson.replace("model-a", "model-c"))
        File(dir, "other.json").writeText(modelsJson)
        File(dir, "models_cache_C.txt").writeText(modelsJson)
        // Pricing lookup walks every cache file: model-b is in both A and B.
        assertNotNull(DynamicModelService.getPricingForModel("b/model-b"))
        assertNull(DynamicModelService.getPricingForModel("nonexistent"))
        // Remove the files: only what the listing loaded into memory is still served.
        dir.listFiles()!!.forEach { it.delete() }
        assertNotNull(DynamicModelService.getCachedModels("A"))
        assertNotNull(DynamicModelService.getCachedModels("B"))
        assertNull(DynamicModelService.getCachedModels("C"))
        assertNull(DynamicModelService.getCachedModels("other"))
    }

    @Test fun missingCacheDirectoryIsNotAnError() {
        dir.deleteRecursively()
        assertNull(DynamicModelService.getPricingForModel("x"))
        assertNull(DynamicModelService.getCachedModels("A"))
        assertEquals(0L, DynamicModelService.cacheTimestamp("A"))
        // Writing to a missing directory fails quietly, as before.
        saveToDisk("A", modelsJson)
        assertNull(DynamicModelService.getCachedModels("A"))
    }
}
