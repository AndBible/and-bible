package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.webdav.CertPin
import net.bible.service.cloudsync.webdav.Propagation
import net.bible.service.cloudsync.webdav.WebDavPrefKeys
import net.bible.service.cloudsync.webdav.WebDavStateStore
import platform.Foundation.NSUserDefaults

/** Credential keys that must never sit in NSUserDefaults; they go to the Keychain-backed SecretStore. */
val SECRET_CREDENTIAL_KEYS = setOf("cloud_sync_password", "webdav_sync_password")

/** Device-local string preferences in the standard NSUserDefaults domain, using Android's key names. */
class IosDevicePrefs(private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) {
    fun getString(key: String): String? = defaults.stringForKey(key)

    /** A null [value] removes the key. */
    fun setString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/** WebDAV adapter state with the same keys and encodings as Android's `PrefsWebDavStateStore`. */
class IosWebDavStateStore(private val prefs: IosDevicePrefs) : WebDavStateStore {
    override var propagation: Propagation
        get() = prefs.getString(WebDavPrefKeys.PROPAGATION)
            ?.let { runCatching { Propagation.valueOf(it) }.getOrNull() } ?: Propagation.UNKNOWN
        set(v) = prefs.setString(WebDavPrefKeys.PROPAGATION, v.name)
    override var certPin: CertPin?
        get() = CertPin.decode(prefs.getString(WebDavPrefKeys.CERT_PIN))
        set(v) = prefs.setString(WebDavPrefKeys.CERT_PIN, v?.encode())
}
