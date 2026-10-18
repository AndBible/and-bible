@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package net.bible.service.legacymigration

import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private object NoSecrets : net.bible.sharedcore.platform.secrets.SecretStore {
    override fun put(key: String, value: String) = Unit
    override fun get(key: String): String? = null
    override fun remove(key: String) = Unit
}

class IosLegacyPlatformTest {
    @Test
    fun booleansAndNumbersAreDistinguished() {
        assertEquals(true, convertDefaultsValue(NSNumber(bool = true)))
        assertEquals(false, convertDefaultsValue(NSNumber(bool = false)))
        assertEquals(1L, convertDefaultsValue(NSNumber(long = 1L)))
        assertEquals(0L, convertDefaultsValue(NSNumber(long = 0L)))
        assertEquals(1.5, convertDefaultsValue(NSNumber(double = 1.5)))
    }

    @Test
    fun containersAreConvertedRecursively() {
        assertEquals(listOf("a", 2L, true), convertDefaultsValue(listOf("a", NSNumber(long = 2L), NSNumber(bool = true))))
        assertEquals(mapOf("k" to 3L), convertDefaultsValue(mapOf("k" to NSNumber(long = 3L))))
        assertNull(convertDefaultsValue(platform.Foundation.NSDate()))
    }

    @Test
    fun userDefaultsSnapshotReflectsWrites() {
        val key = "legacy_platform_test_flag"
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = key)
        try {
            assertEquals(true, IosLegacyPlatform(NoSecrets).userDefaults()[key])
        } finally {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
        }
    }
}
