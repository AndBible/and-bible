@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

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
        val t = NSNumber(bool = true)
        val diag = "objCType=${objCTypeOf(t)} class=${classNameOf(t)} cfTypeId=${cfTypeId(t)} cfBooleanTypeId=${platform.CoreFoundation.CFBooleanGetTypeID()}"
        assertEquals(true, convertDefaultsValue(t), diag)
        assertEquals(false, convertDefaultsValue(NSNumber(bool = false)), diag)
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
            val raw = NSUserDefaults.standardUserDefaults.dictionaryRepresentation()[key] as? NSNumber
            val diag = "objCType=${raw?.let { objCTypeOf(it) }} class=${raw?.let { classNameOf(it) }} cfTypeId=${raw?.let { cfTypeId(it) }}"
            assertEquals(true, IosLegacyPlatform(NoSecrets).userDefaults()[key], diag)
        } finally {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
        }
    }
}

private fun cfTypeId(n: NSNumber): ULong? {
    val ref = platform.Foundation.CFBridgingRetain(n)
    return try { ref?.let { platform.CoreFoundation.CFGetTypeID(it) } } finally { platform.Foundation.CFBridgingRelease(ref) }
}
