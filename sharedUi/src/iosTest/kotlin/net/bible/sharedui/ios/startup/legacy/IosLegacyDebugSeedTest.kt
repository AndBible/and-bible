package net.bible.sharedui.ios.startup.legacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosLegacyDebugSeedTest {
    private val rich = """
        {
          "keychain" : [
            {"account" : "gdrive_password", "service" : "org.andbible.remote-sync", "value" : "pw"},
            {"account" : "no-value", "service" : "svc"}
          ],
          "userDefaults" : {
            "calculator_pin" : "4321",
            "discrete_mode" : true,
            "show_calculator" : false,
            "count" : 3,
            "ratio" : 0.5,
            "nothing" : null,
            "list" : [1, 2]
          }
        }
    """.trimIndent()

    @Test fun defaultsKeepTheirJsonTypes() {
        val byKey = parseLegacySeedSidecar(rich).defaults.associateBy { it.key }
        assertEquals(SeedDefault.Text("calculator_pin", "4321"), byKey["calculator_pin"], "a numeric-looking string stays text")
        assertEquals(SeedDefault.Bool("discrete_mode", true), byKey["discrete_mode"])
        assertEquals(SeedDefault.Bool("show_calculator", false), byKey["show_calculator"])
        assertEquals(SeedDefault.Integer("count", 3), byKey["count"])
        assertEquals(SeedDefault.Real("ratio", 0.5), byKey["ratio"])
        assertEquals(setOf("calculator_pin", "discrete_mode", "show_calculator", "count", "ratio"), byKey.keys, "null and arrays are skipped")
    }

    @Test fun incompleteKeychainItemsAreSkipped() {
        assertEquals(listOf(SeedKeychainItem("org.andbible.remote-sync", "gdrive_password", "pw")), parseLegacySeedSidecar(rich).keychain)
    }

    @Test fun missingSectionsAreEmptyAndBadJsonThrows() {
        val empty = parseLegacySeedSidecar("{}")
        assertTrue(empty.defaults.isEmpty() && empty.keychain.isEmpty())
        assertFailsWith<Exception> { parseLegacySeedSidecar("[]") }
        assertFailsWith<Exception> { parseLegacySeedSidecar("{") }
    }

    @Test fun onlyLegacyStoreFilesAreSeeded() {
        for (name in listOf("AndBible.store", "AndBible.store-wal", "AndBible.store-shm", "LocalStore.store", "LocalStore.store-shm")) {
            assertTrue(isLegacyStoreFile(name), name)
        }
        for (name in listOf("sidecar.json", "CloudStore.store", "AndBible", "databases")) assertTrue(!isLegacyStoreFile(name), name)
    }
}
