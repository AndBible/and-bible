package net.bible.sharedlogic

import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class SharedLogicModuleTest {
    @Test
    fun sharedLogicModuleLoadsInAnIsolatedKoinApplication() {
        val app = koinApplication { modules(sharedLogicModule) }
        assertNotNull(app.koin)
        app.close()
    }
}
