package net.bible.desktop.webkitgtk

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.bible.sharedcore.platform.desktop.DesktopPaths
import net.bible.sharedui.desktop.webengine.DesktopWebEngine
import net.bible.sharedui.desktop.webengine.DesktopWebPane

/** The provider contract without GTK: a fake engine stands in for [WebKitGtkEngine]. */
class WebKitGtkEngineProviderTest {
    private class Exited(val status: Int) : RuntimeException()

    private class FakeEngine : ExitableEngine {
        var exitedWith: Int? = null
        override fun bundleUrl(lang: String, dark: Boolean) = "fake"
        override fun createPane(id: String, url: String, documentStartScripts: List<String>, onMessage: (String) -> Unit): DesktopWebPane =
            error("not used")
        override fun exitProcess(status: Int): Nothing {
            exitedWith = status
            throw Exited(status)
        }
    }

    private val paths: DesktopPaths get() = DesktopPaths({ null }, "Linux", Path.of("/tmp/ab-provider-test"))

    /** #4006: GTK gets the JDK's integer scale, so the reparented web view and its AWT canvas agree. */
    @Test
    fun gdkScaleIsTheAwtScale() {
        val env = mutableMapOf<String, String>()
        applyGdkScale(2) { k, v -> env[k] = v }
        assertEquals(mapOf("GDK_SCALE" to "2"), env)
    }

    @Test
    fun usesWindowedRendering() {
        assertTrue(WebKitGtkEngineProvider({ _, _ -> FakeEngine() }, { throw Exited(it) }).windowedRendering)
    }

    @Test
    fun createReturnsTheFactoryEngineAndExitGoesThroughIt() {
        val fake = FakeEngine()
        val provider = WebKitGtkEngineProvider({ _, _ -> fake }, { throw AssertionError("fallback used") })
        val created: DesktopWebEngine = provider.create(Path.of("."), paths) {}
        assertSame(fake, created)
        provider.shutdown()
        val e = assertFailsWith<Exited> { provider.exitProcess(7) }
        assertEquals(7, e.status)
        assertEquals(7, fake.exitedWith)
    }

    @Test
    fun shutdownIsIdempotentAndDoesNotExit() {
        val fake = FakeEngine()
        val provider = WebKitGtkEngineProvider({ _, _ -> fake }, { throw AssertionError("fallback used") })
        provider.create(Path.of("."), paths) {}
        assertFalse(provider.isShutDown)
        provider.shutdown()
        provider.shutdown()
        assertTrue(provider.isShutDown)
        assertEquals(null, fake.exitedWith)
    }

    @Test
    fun jvmShutdownUsesTheGtkExitRatherThanRunningWebkitExitHandlersOnTheVmThread() {
        val fake = FakeEngine()
        val provider = WebKitGtkEngineProvider({ _, _ -> fake }, { throw AssertionError("JVM exit would deadlock during shutdown") })
        provider.create(Path.of("."), paths) {}
        assertEquals(143, assertFailsWith<Exited> { provider.afterJvmShutdown() }.status)
        assertEquals(143, fake.exitedWith)
    }

    @Test
    fun jvmShutdownWithoutAnEngineDoesNotStartAnExitRecursively() {
        WebKitGtkEngineProvider({ _, _ -> FakeEngine() }, { throw AssertionError("recursive JVM exit") }).afterJvmShutdown()
    }

    @Test
    fun exitWithoutAnEngineFallsBackToTheJvmExit() {
        val provider = WebKitGtkEngineProvider({ _, _ -> FakeEngine() }, { throw Exited(it) })
        assertEquals(3, assertFailsWith<Exited> { provider.exitProcess(3) }.status)
    }
}
