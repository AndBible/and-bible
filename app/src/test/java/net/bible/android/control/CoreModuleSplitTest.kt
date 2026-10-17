package net.bible.android.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import java.io.File

/**
 * Guards the split of [buildCoreModule] into platform and shared bindings: the set of registered definitions must stay
 * exactly the one recorded in `core-module-definitions.txt`, whichever part a definition lives in.
 *
 * Registering definitions does not run their factories, so no Android context is needed here.
 */
@OptIn(KoinInternalApi::class)
class CoreModuleSplitTest {
    private fun definitionsOf(vararg modules: Module): List<String> {
        val app = koinApplication { allowOverride(false); modules(*modules) }
        try {
            return app.koin.instanceRegistry.instances.keys.sorted()
        } finally {
            app.close()
        }
    }

    private fun snapshot(): List<String> =
        checkNotNull(javaClass.getResourceAsStream("/core-module-definitions.txt")) { "snapshot resource missing" }
            .bufferedReader().readLines().filter { it.isNotBlank() }

    @Test
    fun definitionsAreUnchanged() {
        val actual = definitionsOf(buildCoreModule())
        // Regeneration mode (set the variable to the output path); never set in normal runs.
        System.getenv("CORE_MODULE_SNAPSHOT_WRITE")?.let { File(it).writeText(actual.joinToString("\n", postfix = "\n")) }
        assertEquals(snapshot(), actual)
    }

    @Test
    fun buildCoreModuleReturnsAFreshGraphEachCall() {
        val first = buildCoreModule()
        val second = buildCoreModule()
        assertNotSame(first, second)
        assertEquals(first.includedModules.size, second.includedModules.size)
        first.includedModules.zip(second.includedModules).forEach { (a, b) -> assertNotSame(a, b) }
    }

    @Test
    fun sharedBindingsAreOnlyThePlatformFreeOnes() {
        val platform = definitionsOf(buildPlatformBindings())
        val shared = definitionsOf(buildSharedBindings())
        assertTrue("platform and shared must be disjoint", platform.intersect(shared.toSet()).isEmpty())
        assertEquals(snapshot(), (platform + shared).sorted())
        assertTrue("shared part must not be empty", shared.isNotEmpty() && platform.isNotEmpty())
    }

    @Test
    fun markdownRendererIsAPlatformBinding() {
        assertTrue(definitionsOf(buildPlatformBindings()).any { it.startsWith("net.bible.sharedcore.platform.MarkdownRenderer::") })
        assertTrue(definitionsOf(buildSharedBindings()).none { it.startsWith("net.bible.sharedcore.platform.MarkdownRenderer::") })
    }
}
