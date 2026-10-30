package net.bible.desktop.webview2

import org.junit.Assume.assumeTrue
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The real loader and runtime, on Windows only (skipped elsewhere): `WebView2Loader.dll` must be on `jna.library.path`
 * (the `w2TestDist` run script sets it).
 */
class WebView2WindowsTest {
    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    @Test
    fun runtimeFoundAndEnvironmentStartsInAFreshUserDataFolder() {
        assumeTrue("Windows only", isWindows)
        val bundle = Files.createTempDirectory("w2-test-bundle")
        val userData = Files.createTempDirectory("w2-test-udf").resolve("webview2")
        val engine = WebView2Engine.start(bundle, userData, log = {}).getOrThrow()
        try {
            val major = engine.version.substringBefore('.').toInt()
            assertTrue(major >= RuntimeCheck.MIN_MAJOR, engine.version)
            assertTrue(Files.isDirectory(userData), "user data folder created")
        } finally {
            engine.shutdown()
        }
    }

    @Test
    fun secondEngineOnTheSameFolderStillStarts() {
        assumeTrue("Windows only", isWindows)
        // Same folder, same options, one process: WebView2 shares the browser process; nothing must fail.
        val bundle = Files.createTempDirectory("w2-test-bundle")
        val userData = Files.createTempDirectory("w2-test-udf").resolve("webview2")
        val first = WebView2Engine.start(bundle, userData, log = {}).getOrThrow()
        try {
            val second = WebView2Engine.start(bundle, userData, log = {}).getOrThrow()
            second.shutdown()
        } finally {
            first.shutdown()
        }
    }
}
