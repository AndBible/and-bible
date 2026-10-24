package net.bible.sharedcore.platform.secrets

/**
 * Thrown by fail-closed platform stores when they fail for a reason other than "no such key".
 * The legacy Android SharedPreferences implementation does not guarantee this exception or fail-closed reads.
 */
class SecretStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Device-local secret storage. Fail-closed implementations return null only when the key is absent and throw
 * [SecretStoreException] for other failures; iOS implementations must follow this contract. The legacy Android
 * SharedPreferences implementation may propagate other exception types and can read a corrupt preference file as
 * an absent key because the platform may swallow file-read errors. Its behavior is preserved for compatibility.
 */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
