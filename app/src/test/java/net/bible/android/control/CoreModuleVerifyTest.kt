package net.bible.android.control

import android.app.Application
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class CoreModuleVerifyTest {
    /**
     * Verifies every definition in [coreModule] can resolve its dependencies.
     * The Koin analogue of Dagger's compile-time graph check. Fails loudly on a
     * missing/unresolvable binding.
     */
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `coreModule graph resolves`() {
        // androidContext() supplies Context/Application-typed constructor params;
        coreModule.verify(
            extraTypes = listOf(
                Application::class,
                android.content.Context::class,
            )
        )
    }
}
