package net.bible.service.cloudsync.webdav

import android.content.SharedPreferences

fun WebDavConfig.Companion.fromPreferences(prefs: SharedPreferences) = WebDavConfig(
    prefs.getString(WebDavPrefKeys.SERVER_URL, "") ?: "",
    prefs.getString(WebDavPrefKeys.USERNAME, "") ?: "",
    prefs.getString(WebDavPrefKeys.PASSWORD, "") ?: "",
    prefs.getString(WebDavPrefKeys.FOLDER_PATH, "") ?: "",
)

class PrefsWebDavStateStore(private val prefs: SharedPreferences) : WebDavStateStore {
    override var propagation: Propagation
        get() = prefs.getString(WebDavPrefKeys.PROPAGATION, null)
            ?.let { runCatching { Propagation.valueOf(it) }.getOrNull() } ?: Propagation.UNKNOWN
        set(v) = prefs.edit().putString(WebDavPrefKeys.PROPAGATION, v.name).apply()
    override var certPin: CertPin?
        get() = CertPin.decode(prefs.getString(WebDavPrefKeys.CERT_PIN, null))
        set(v) = prefs.edit().apply { if (v == null) remove(WebDavPrefKeys.CERT_PIN) else putString(WebDavPrefKeys.CERT_PIN, v.encode()) }.apply()
}
