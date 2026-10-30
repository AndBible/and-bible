package net.bible.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CefNativesLocationTest {
    private fun nativesDir(parent: Path? = null): Path {
        val dir = if (parent == null) Files.createTempDirectory("natives") else Files.createDirectories(parent)
        Files.createFile(dir.resolve("libcef.so"))
        return dir
    }

    private fun props(vararg pairs: Pair<String, String>): (String) -> String? = mapOf(*pairs)::get

    @Test
    fun devPropertyWins() {
        val dev = nativesDir()
        val resources = Files.createTempDirectory("res")
        nativesDir(resources.resolve("kcef-natives"))
        val result = CefNativesLocation.resolve(
            props("kcef.installDir" to dev.toString(), "compose.application.resources.dir" to resources.toString()),
        )
        assertEquals(dev, result.getOrThrow())
    }

    @Test
    fun packagedResourcesDirUsed() {
        val resources = Files.createTempDirectory("res")
        val natives = nativesDir(resources.resolve("kcef-natives"))
        val result = CefNativesLocation.resolve(props("compose.application.resources.dir" to resources.toString()))
        assertEquals(natives, result.getOrThrow())
    }

    @Test
    fun missingBothFailsNamingProperties() {
        val result = CefNativesLocation.resolve(props())
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()!!.message!!
        assertTrue("kcef.installDir" in message, message)
        assertTrue("compose.application.resources.dir" in message, message)
    }

    @Test
    fun dirWithoutLibcefFails() {
        val empty = Files.createTempDirectory("empty")
        val result = CefNativesLocation.resolve(props("kcef.installDir" to empty.toString()))
        assertTrue(result.isFailure)
        assertTrue(empty.toString() in result.exceptionOrNull()!!.message!!)
    }

    @Test
    fun windowsNativesDirHasLibcefDll() {
        // KCEF's Windows layout: the JBR package's lib/ merged with bin/ in one directory.
        val dir = Files.createTempDirectory("natives-win")
        listOf("libcef.dll", "jcef.dll", "jcef_helper.exe", "chrome_elf.dll", "icudtl.dat").forEach { Files.createFile(dir.resolve(it)) }
        assertEquals(dir, CefNativesLocation.resolve(props("kcef.installDir" to dir.toString())).getOrThrow())
        val message = CefNativesLocation.resolve(props("kcef.installDir" to Files.createTempDirectory("e").toString())).exceptionOrNull()!!.message!!
        assertTrue("libcef.dll" in message && "libcef.so" in message, message)
    }
}
