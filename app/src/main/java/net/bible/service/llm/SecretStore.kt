package net.bible.service.llm

import net.bible.android.database.IdType

/** LLM API keys. Android retains the existing preference keys; iOS uses Keychain. */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

fun apiKeyName(providerConfigId: IdType): String = "llm_api_key_$providerConfigId"
