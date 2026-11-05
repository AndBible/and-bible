package net.bible.service.llm

import java.lang.reflect.Proxy
import java.nio.file.Files
import androidx.room3.Room
import net.bible.android.database.AiSettingsDatabase
import net.bible.service.db.productionConfig
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.sharedcore.platform.OrderedLauncher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiLanguagePersistenceTest {
    @Test fun languageSurvivesStoreRecreationAndBlankRestoresDefault() = runTest {
        val directory = Files.createTempDirectory("ai-language-")
        val path = directory.resolve("ai.sqlite3").toString()
        val database = Room.databaseBuilder<AiSettingsDatabase>(name = path).productionConfig().build()
        val dao = database.globalAiSettingsDao()
        val job = SupervisorJob()
        val scope = AppCoroutineScope(coroutineContext + job)
        val env = Proxy.newProxyInstance(AppEnvironment::class.java.classLoader, arrayOf(AppEnvironment::class.java)) { _, m, _ ->
            when (m.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as AppEnvironment
        val launcher = OrderedLauncher(scope)
        fun store() = AiSettingsStore({ dao }, launcher, scope, env, lifetime = AiDatabaseLifetime(), invalidations = { emptyFlow() })
        try {
            val first = store()
            first.ensureLoaded()
            first.aiLanguage = "fi"
            first.awaitWrites()
            val reopened = store()
            reopened.ensureLoaded()
            assertEquals("fi", reopened.aiLanguage)
            reopened.aiLanguage = null
            reopened.awaitWrites()
            val reset = store()
            reset.ensureLoaded()
            assertNull(reset.aiLanguage)
        } finally {
            job.cancel()
            job.join()
            database.close()
            directory.toFile().deleteRecursively()
        }
    }
}
