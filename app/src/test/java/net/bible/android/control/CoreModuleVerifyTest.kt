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
                // OrderedLauncher is bound with the AppCoroutineScope explicitly; verify() only sees its declared CoroutineScope parameter.
                kotlinx.coroutines.CoroutineScope::class,
                // ReadingPlanRepository/ReadingPlanTextFileDao take their platform lookups (DAO, user plan folder, add-on plans) as
                // lambdas, bound explicitly in the module (L1a: no platform defaults); verify() cannot see into a lambda.
                Function0::class,
                // DocumentControl.deleteFiles is a named fun interface bound explicitly in the module.
                net.bible.android.control.document.DocumentFileDeleter::class,
            )
        )
    }
}
