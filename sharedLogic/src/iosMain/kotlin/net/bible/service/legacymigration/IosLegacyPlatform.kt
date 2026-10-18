@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package net.bible.service.legacymigration

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import net.bible.sharedcore.platform.secrets.SecretStore
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.CoreFoundation.CFBooleanGetTypeID
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemCopyMatching
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.posix.memcpy

/** Thrown when the old app's Keychain cannot be read for a reason other than "no such item". Never carries secret values. */
class LegacyKeychainException(message: String) : RuntimeException(message)

/**
 * iOS [LegacyPlatform]. Paths come from `NSFileManager`'s search paths (absolute); [databasesDir] is the same
 * `Application Support/databases` directory `IosAppEnvironment.databasePath` uses (it is not created here).
 *
 * The Swift app's Keychain items are plain generic passwords (service + account, no access group, not synchronizable),
 * so the same plain query finds them.
 */
class IosLegacyPlatform(override val secrets: SecretStore) : LegacyPlatform {
    override val fileSystem: FileSystem = FileSystem.SYSTEM
    override val applicationSupport: Path = directory(NSApplicationSupportDirectory)
    override val documents: Path = directory(NSDocumentDirectory)
    override val databasesDir: Path = applicationSupport / "databases"

    override fun userDefaults(): Map<String, Any?> {
        val raw = NSUserDefaults.standardUserDefaults.dictionaryRepresentation()
        val result = LinkedHashMap<String, Any?>()
        for ((key, value) in raw) {
            if (key is String) result[key] = convertDefaultsValue(value)
        }
        return result
    }

    override fun legacyKeychain(service: String, account: String): String? {
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: throw LegacyKeychainException("Legacy Keychain query allocation failed")
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            put(query, kSecAttrService, NSString.create(string = service))
            put(query, kSecAttrAccount, NSString.create(string = account))
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            return memScoped {
                val result = alloc<CFTypeRefVar>()
                result.value = null
                val status = SecItemCopyMatching(query, result.ptr)
                try {
                    when (status) {
                        errSecItemNotFound -> null
                        errSecSuccess -> {
                            val payload = result.value
                            if (payload == null || CFGetTypeID(payload) != CFDataGetTypeID()) {
                                throw LegacyKeychainException("Legacy Keychain read failed: invalid payload")
                            }
                            val data = CFBridgingRelease(CFRetain(payload)) as? NSData
                                ?: throw LegacyKeychainException("Legacy Keychain read failed: invalid payload")
                            NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
                                ?: throw LegacyKeychainException("Legacy Keychain read failed: invalid UTF-8 payload")
                        }
                        else -> throw LegacyKeychainException("Legacy Keychain read failed: status $status")
                    }
                } finally {
                    result.value?.let { CFRelease(it) }
                }
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun put(dictionary: platform.CoreFoundation.CFMutableDictionaryRef, key: platform.CoreFoundation.CFTypeRef?, value: Any) {
        val retained = CFBridgingRetain(value)
        try {
            CFDictionarySetValue(dictionary, key, retained)
        } finally {
            CFBridgingRelease(retained)
        }
    }

    private fun directory(kind: NSSearchPathDirectory): Path =
        (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).first() as String).toPath()
}

/**
 * Converts one `UserDefaults` value to the Kotlin types `LegacyPlatform.userDefaults` promises: a Core Foundation
 * boolean becomes [Boolean] (checked by CF type id, since an `NSNumber` 1 is not a boolean), other numbers
 * [Long] (integral) or [Double], strings stay, `NSData` becomes [ByteArray], arrays become lists and nested
 * dictionaries maps; anything else (dates, ...) becomes null.
 */
internal fun convertDefaultsValue(value: Any?): Any? = when (value) {
    null -> null
    is String -> value
    is NSNumber -> {
        val ref = CFBridgingRetain(value)
        val isBool = try {
            ref != null && CFGetTypeID(ref) == CFBooleanGetTypeID()
        } finally {
            CFBridgingRelease(ref)
        }
        if (isBool) value.boolValue
        else when (value.objCType?.toKString()?.firstOrNull()) {
            'f', 'd' -> value.doubleValue
            else -> value.longLongValue
        }
    }
    is NSData -> ByteArray(value.length.toInt()).also { bytes ->
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), value.bytes, value.length) }
    }
    is List<*> -> value.map { convertDefaultsValue(it) }
    is Map<*, *> -> value.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to convertDefaultsValue(v) } }.toMap()
    else -> null
}
