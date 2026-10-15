package net.bible.sharedcore.platform.secrets

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainSecretStoreTest {
    private val service = "i1.test." + NSUUID().UUIDString
    private val store = IosKeychainSecretStore(service)
    private val keys = mutableSetOf<String>()
    private var firstPut = true

    @AfterTest
    fun cleanup() {
        keys.forEach(store::remove)
    }

    private fun key(name: String = "secret"): String = name.also { keys += it }

    private fun put(key: String, value: String) {
        try {
            store.put(key, value)
        } catch (error: SecretStoreException) {
            if (firstPut && error.message == "Keychain put failed: -34018") {
                fail("Keychain needs an app host on this simulator: move IosKeychainSecretStoreTest to an iosApp test target (plan Task 9 fallback)")
            }
            throw error
        } finally {
            firstPut = false
        }
    }

    @Test fun absentKeyReturnsNull() {
        assertNull(store.get(key()))
    }

    @Test fun unicodeRoundTrip() {
        val key = key()
        put(key, "äö 🔑")
        assertEquals("äö 🔑", store.get(key))
    }

    @Test fun secondPutReplacesValue() {
        val key = key()
        put(key, "first")
        put(key, "second")
        assertEquals("second", store.get(key))
    }

    @Test fun removeMakesKeyAbsent() {
        val key = key()
        put(key, "value")
        store.remove(key)
        assertNull(store.get(key))
    }

    @Test fun removingAbsentKeySucceeds() {
        store.remove(key())
    }

    @Test fun emptyValueIsNotAbsence() {
        val key = key()
        put(key, "")
        assertEquals("", store.get(key))
    }

    @Test fun accountsAndServicesAreIsolated() {
        val key = key()
        put(key, "value")
        assertNull(store.get(key("other")))
        assertNull(IosKeychainSecretStore("i1.test." + NSUUID().UUIDString).get(key))
    }

    @Test fun invalidUtf8ThrowsInsteadOfReturningNullOrReplacementText() {
        val key = key()
        put(key, "initial")
        val bytes = byteArrayOf(0xc3.toByte(), 0x28)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
        val attributes = CFDictionaryCreateMutable(kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)!!
        val serviceRef = CFBridgingRetain(NSString.create(string = service))
        val keyRef = CFBridgingRetain(NSString.create(string = key))
        val dataRef = CFBridgingRetain(data)
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, keyRef)
            CFDictionarySetValue(attributes, kSecValueData, dataRef)
            assertEquals(errSecSuccess, SecItemUpdate(query, attributes))
            assertFailsWith<SecretStoreException> { store.get(key) }
        } finally {
            CFRelease(attributes)
            CFRelease(query)
            CFBridgingRelease(dataRef)
            CFBridgingRelease(keyRef)
            CFBridgingRelease(serviceRef)
        }
    }
}
