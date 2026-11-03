package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.SyncSettingsPlatform
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.sharedcore.platform.secrets.SecretStore
import platform.Foundation.NSByteCountFormatter
import platform.Foundation.NSByteCountFormatterCountStyleFile
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * iOS sync settings: plain credentials in device-local [prefs] under Android's key names, the two
 * password keys ([SECRET_CREDENTIAL_KEYS]) in the Keychain-backed [secrets].
 */
class IosSyncSettingsPlatform(
    private val prefs: IosDevicePrefs,
    private val secrets: SecretStore,
    private val serverUrlHint: () -> String,
) : SyncSettingsPlatform {
    override fun credential(key: String): String =
        (if (key in SECRET_CREDENTIAL_KEYS) secrets.get(key) else prefs.getString(key)) ?: ""

    override fun setCredential(key: String, value: String) {
        if (key in SECRET_CREDENTIAL_KEYS) secrets.put(key, value) else prefs.setString(key, value)
    }

    override fun isValidUrl(value: String): Boolean {
        val url = NSURL.URLWithString(value) ?: return false
        val scheme = url.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !url.host.isNullOrEmpty()
    }

    override fun formatFileSize(bytes: Long): String =
        NSByteCountFormatter.stringFromByteCount(bytes, NSByteCountFormatterCountStyleFile)

    override fun formatSyncTime(timestamp: Long): String =
        NSDateFormatter().apply {
            dateFormat = "dd-MM-yyyy HH:mm:ss"
            locale = NSLocale.currentLocale
        }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0))

    override fun formatDecimal2(value: Double): String =
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = 2u
            maximumFractionDigits = 2u
            locale = NSLocale.currentLocale
        }.stringFromNumber(NSNumber(value)) ?: ""

    override val webDavState: WebDavStateStore get() = IosWebDavStateStore(prefs)
    override val webDavServerUrlHint: String get() = serverUrlHint()
}
