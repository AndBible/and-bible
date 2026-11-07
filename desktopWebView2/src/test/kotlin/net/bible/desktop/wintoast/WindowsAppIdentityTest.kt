package net.bible.desktop.wintoast

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsAppIdentityTest {
    private val dir = Paths.get("C:/AndBible")

    @Test fun msixSkipsRegistryAndProcessId() {
        val calls = mutableListOf<String>()
        val result = applyWindowsIdentity(dir, { true }, { calls.add("registry") }, { calls.add("process") }, {})
        assertTrue(result.available)
        assertNull(result.aumid)
        assertTrue(calls.isEmpty())
    }

    @Test fun unpackagedWritesPlanAndSetsProcessId() {
        val calls = mutableListOf<String>()
        val result = applyWindowsIdentity(dir, { false }, { calls.add("${it.name}=${it.value}") }, { calls.add("process=$it") }, {})
        assertTrue(result.available)
        assertEquals(ANDBIBLE_AUMID, result.aumid)
        assertEquals(listOf("DisplayName=AndBible", "IconUri=${dir.resolve("app/resources/andbible.ico")}", "=\"${dir.resolve("AndBible.exe")}\" -ToastActivated", "process=$ANDBIBLE_AUMID"), calls)
    }

    @Test fun identityFailureIsLoggedAndUnavailable() {
        val errors = mutableListOf<String>()
        val result = applyWindowsIdentity(dir, { false }, { error("registry failed") }, {}, { errors.add(it) })
        assertFalse(result.available)
        assertNull(result.aumid)
        assertEquals(1, errors.size)
    }
}
