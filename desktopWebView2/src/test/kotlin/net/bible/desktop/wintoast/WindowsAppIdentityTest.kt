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
        assertEquals(listOf("DisplayName=AndBible", "IconUri=${dir.resolve("app/resources/andbible.ico")}", "CustomActivator={C8B03B6B-3F3A-4F8E-9A57-24A610C03A13}", "=\"${dir.resolve("AndBible.exe")}\" -ToastActivated", "process=$ANDBIBLE_AUMID"), calls)
    }

    @Test fun missingJpackagePathSkipsRegistryButKeepsProcessIdentity() {
        val calls = mutableListOf<String>()
        val result = applyWindowsIdentity(null, { false }, { calls.add("registry") }, { calls.add("process=$it") }, {})

        assertTrue(result.available)
        assertEquals(ANDBIBLE_AUMID, result.aumid)
        assertEquals(listOf("process=$ANDBIBLE_AUMID"), calls)
    }

    @Test fun zipStartPreservesExistingMsiIdentityWhenItsTargetsStillExist() {
        val registeredValues = mapOf(
            "IconUri" to "C:/Program Files/AndBible/app/resources/andbible.ico",
            "" to "\"C:/Program Files/AndBible/AndBible.exe\" -ToastActivated",
        )
        val writes = mutableListOf<RegistryValue>()
        val processIds = mutableListOf<String>()
        val result = applyWindowsIdentity(
            Paths.get("C:/Users/test/Downloads/AndBible"),
            { false },
            { writes += it },
            { processIds += it },
            {},
            read = { value -> registeredValues[value.name] },
            exists = { path -> path.startsWith("C:/Program Files/AndBible/") },
        )

        assertTrue(result.available)
        assertEquals(ANDBIBLE_AUMID, result.aumid)
        assertEquals(emptyList(), writes)
        assertEquals(listOf(ANDBIBLE_AUMID), processIds)
    }

    @Test fun missingRegistrationAllowsZipInstallToRegisterIdentity() {
        val writes = mutableListOf<RegistryValue>()
        val result = applyWindowsIdentity(dir, { false }, writes::add, {}, {}, exists = { true })

        assertTrue(result.available)
        assertEquals(4, writes.size)
        assertEquals(dir.resolve("app/resources/andbible.ico").toString(), writes.single { it.name == "IconUri" }.value)
    }

    @Test fun staleRegistrationAllowsZipInstallToReplaceIdentity() {
        val values = mapOf(
            "IconUri" to "C:/Old/AndBible/app/resources/andbible.ico",
            "" to "\"C:/Old/AndBible/AndBible.exe\" -ToastActivated",
        )
        val writes = mutableListOf<RegistryValue>()
        val result = applyWindowsIdentity(
            dir,
            { false },
            writes::add,
            {},
            {},
            read = { values[it.name] },
            exists = { it.startsWith(dir.toString()) },
        )

        assertTrue(result.available)
        assertEquals(4, writes.size)
        assertEquals(dir.resolve("app/resources/andbible.ico").toString(), writes.single { it.name == "IconUri" }.value)
    }

    @Test fun sameInstallRefreshesItsRegistration() {
        val values = mapOf(
            "IconUri" to dir.resolve("app/resources/andbible.ico").toString(),
            "" to "\"${dir.resolve("AndBible.exe")}\" -ToastActivated",
        )
        val writes = mutableListOf<RegistryValue>()
        applyWindowsIdentity(dir, { false }, writes::add, {}, {}, read = { values[it.name] }, exists = { true })

        assertEquals(4, writes.size)
    }

    @Test fun identityFailureIsLoggedAndUnavailable() {
        val errors = mutableListOf<String>()
        val result = applyWindowsIdentity(dir, { false }, { error("registry failed") }, {}, { errors.add(it) })
        assertFalse(result.available)
        assertNull(result.aumid)
        assertEquals(1, errors.size)
    }
}
