package net.bible.sharedcore.platform.secrets

/** Thrown when the platform store fails for any reason other than "no such key". */
class SecretStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Device-local secret storage. [get] returns null only when the key is absent; any other failure throws
 * [SecretStoreException] (fail closed: a broken store must never read as "no key").
 */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
