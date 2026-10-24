package net.bible.sharedui.probe

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import net.bible.sharedcore.platform.secrets.IosKeychainSecretStore
import net.bible.sharedcore.platform.secrets.SecretStoreException
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Security.SecItemUpdate
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecValueData

/**
 * The [IosKeychainSecretStore] cases, run by the app-hosted `iosAppUnitTests` XCTest target. A bare
 * Kotlin/Native test binary has no application identity, so the simulator's Keychain refuses it with
 * -34018 (errSecMissingEntitlement); inside the app it behaves as in production.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
object IosKeychainSelfTest {
    private const val KEY = "secret"
    private const val OTHER_KEY = "other"

    /** Null when every case passes, else one line per failed case. */
    fun run(): String? {
        val failures = mutableListOf<String>()
        fun case(name: String, block: (store: IosKeychainSecretStore, service: String) -> Unit) {
            val service = "i1.test." + NSUUID().UUIDString
            val store = IosKeychainSecretStore(service)
            try {
                block(store, service)
            } catch (error: Throwable) {
                failures += "$name: $error"
            } finally {
                listOf(KEY, OTHER_KEY).forEach { runCatching { store.remove(it) } }
            }
        }
        fun expect(actual: Any?, expected: Any?) {
            if (actual != expected) throw AssertionError("expected <$expected>, actual <$actual>")
        }

        case("absentKeyReturnsNull") { store, _ -> expect(store.get(KEY), null) }
        case("unicodeRoundTrip") { store, _ ->
            store.put(KEY, "äö 🔑")
            expect(store.get(KEY), "äö 🔑")
        }
        case("secondPutReplacesValue") { store, _ ->
            store.put(KEY, "first")
            store.put(KEY, "second")
            expect(store.get(KEY), "second")
        }
        case("removeMakesKeyAbsent") { store, _ ->
            store.put(KEY, "value")
            store.remove(KEY)
            expect(store.get(KEY), null)
        }
        case("removingAbsentKeySucceeds") { store, _ -> store.remove(KEY) }
        case("emptyValueIsNotAbsence") { store, _ ->
            store.put(KEY, "")
            expect(store.get(KEY), "")
        }
        case("accountsAndServicesAreIsolated") { store, _ ->
            store.put(KEY, "value")
            expect(store.get(OTHER_KEY), null)
            expect(IosKeychainSecretStore("i1.test." + NSUUID().UUIDString).get(KEY), null)
        }
        case("invalidUtf8ThrowsInsteadOfReturningNullOrReplacementText") { store, service ->
            store.put(KEY, "initial")
            overwriteWithInvalidUtf8(service, KEY)
            val error = runCatching { store.get(KEY) }.exceptionOrNull()
            if (error !is SecretStoreException) throw AssertionError("expected SecretStoreException, got ${error ?: "a value"}")
        }
        return failures.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    /** Writes bytes that are not UTF-8 straight into the item, bypassing the store's own encoding. */
    private fun overwriteWithInvalidUtf8(service: String, key: String) {
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
            val status = SecItemUpdate(query, attributes)
            if (status != errSecSuccess) throw AssertionError("SecItemUpdate failed: $status")
        } finally {
            CFBridgingRelease(dataRef)
            CFBridgingRelease(keyRef)
            CFBridgingRelease(serviceRef)
            CFRelease(attributes)
            CFRelease(query)
        }
    }
}
