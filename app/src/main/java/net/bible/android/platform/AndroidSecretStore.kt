package net.bible.android.platform

import android.content.SharedPreferences
import net.bible.service.llm.SecretStore

/** Retains the legacy API-key preference storage and asynchronous disk writes. */
class AndroidSecretStore(private val prefs: SharedPreferences) : SecretStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
}
