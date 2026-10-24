package net.bible.sharedcore.platform.secrets

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*

/** Device-local generic passwords. Only a missing item is treated as an absent secret. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosKeychainSecretStore(
    private val service: String = (NSBundle.mainBundle.bundleIdentifier ?: "org.andbible.ios") + ".secrets",
) : SecretStore {
    override fun put(key: String, value: String) {
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding)
            ?: throw SecretStoreException("Keychain put failed: invalid UTF-8 value")
        withQuery(key) { query ->
            withDictionary { attributes ->
                setObject(attributes, kSecValueData, data)
                var status = SecItemUpdate(query, attributes)
                if (status == errSecItemNotFound) {
                    setObject(query, kSecValueData, data)
                    CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                    status = SecItemAdd(query, null)
                }
                if (status != errSecSuccess) throw SecretStoreException("Keychain put failed: $status")
            }
        }
    }

    override fun get(key: String): String? = withQuery(key) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            result.value = null
            val status = SecItemCopyMatching(query, result.ptr)
            try {
                when (status) {
                    errSecItemNotFound -> null
                    errSecSuccess -> {
                        val payload = result.value
                        if (payload == null || CFGetTypeID(payload) != CFDataGetTypeID()) {
                            throw SecretStoreException("Keychain get failed: invalid payload")
                        }
                        val data = CFBridgingRelease(CFRetain(payload)) as? NSData
                            ?: throw SecretStoreException("Keychain get failed: invalid payload")
                        val value = NSString.create(data = data, encoding = NSUTF8StringEncoding)
                            ?: throw SecretStoreException("Keychain get failed: invalid UTF-8 payload")
                        value.toString()
                    }
                    else -> throw SecretStoreException("Keychain get failed: $status")
                }
            } finally {
                result.value?.let { CFRelease(it) }
            }
        }
    }

    override fun remove(key: String) {
        withQuery(key) { query ->
            val status = SecItemDelete(query)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw SecretStoreException("Keychain remove failed: $status")
            }
        }
    }

    private fun <T> withQuery(key: String, block: (CFMutableDictionaryRef) -> T): T = withDictionary { query ->
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        setObject(query, kSecAttrService, NSString.create(string = service))
        setObject(query, kSecAttrAccount, NSString.create(string = key))
        block(query)
    }

    private fun <T> withDictionary(block: (CFMutableDictionaryRef) -> T): T {
        val dictionary = CFDictionaryCreateMutable(
            kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: throw SecretStoreException("Keychain dictionary allocation failed")
        try {
            return block(dictionary)
        } finally {
            CFRelease(dictionary)
        }
    }

    private fun setObject(dictionary: CFMutableDictionaryRef, key: CFTypeRef?, value: Any) {
        val retained = CFBridgingRetain(value)
        try {
            // The CFType dictionary retains its own reference to the inserted object.
            CFDictionarySetValue(dictionary, key, retained)
        } finally {
            CFBridgingRelease(retained)
        }
    }
}
