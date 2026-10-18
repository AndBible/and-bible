package net.bible.service.cloudsync.ios

import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.filetree.FileTreeCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavPrefKeys
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.service.cloudsync.webdav.nextcloudDavUrl
import net.bible.sharedcore.platform.cloud.CloudFileStore
import net.bible.sharedcore.webdav.WebDavClient
import okio.Path
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * iOS cloud back ends (spec C9), in this order: iCloud Drive, Nextcloud (a [WebDavCloudAdapter]
 * preset), generic WebDAV. No Google Drive.
 *
 * `NEXT_CLOUD` and `WEBDAV` share the one WebDAV state store ([IosSyncSettingsPlatform.webDavState]),
 * so switching between them recalibrates from `UNKNOWN` and one certificate pin serves both. Known
 * limitation.
 */
class IosCloudAdapterFactory(
    private val platform: IosSyncSettingsPlatform,
    private val deviceId: String,
    private val displayName: (String) -> String,
    private val iCloudStore: () -> CloudFileStore,
    private val signInUi: WebDavSignInUi,
    private val tempDir: Path,
    private val clientFactory: (WebDavConfig, WebDavStateStore) -> WebDavClient =
        { c, s -> createIosWebDavClient(c, s, ::nowMs) },
) : CloudAdapterFactory {
    override val available: List<String> = listOf("ICLOUD_DRIVE", "NEXT_CLOUD", "WEBDAV")

    override fun displayName(id: String): String = displayName.invoke(id)

    override fun create(id: String): CloudAdapter = when (id) {
        "ICLOUD_DRIVE" -> FileTreeCloudAdapter(iCloudStore(), deviceId, tempDir)
        "NEXT_CLOUD" -> {
            val user = platform.credential("cloud_sync_username")
            webDav(
                WebDavConfig(
                    nextcloudDavUrl(platform.credential("cloud_sync_server_url"), user),
                    user,
                    platform.credential("cloud_sync_password"),
                    platform.credential("cloud_sync_folder_path"),
                ),
            )
        }
        "WEBDAV" -> webDav(
            WebDavConfig(
                platform.credential(WebDavPrefKeys.SERVER_URL),
                platform.credential(WebDavPrefKeys.USERNAME),
                platform.credential(WebDavPrefKeys.PASSWORD),
                platform.credential(WebDavPrefKeys.FOLDER_PATH),
            ),
        )
        else -> throw IllegalStateException("Unknown cloud adapter id: $id")
    }

    private fun webDav(config: WebDavConfig) =
        WebDavCloudAdapter(config, platform.webDavState, signInUi, deviceId, clientFactory)
}
