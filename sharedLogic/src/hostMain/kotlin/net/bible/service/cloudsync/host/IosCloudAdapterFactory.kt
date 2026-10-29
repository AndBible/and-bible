package net.bible.service.cloudsync.host

import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.SyncSettingsPlatform
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

/**
 * iOS cloud back ends (spec C9), in this order: iCloud Drive, Nextcloud (a [WebDavCloudAdapter]
 * preset), generic WebDAV. No Google Drive.
 *
 * [awaitReady] is awaited by the iCloud adapter's `signIn()` (the ubiquity container resolves
 * asynchronously and reads as null until then). The host binds ONE container and passes its
 * `awaitReady`; `iCloudStore` must reuse that same container on every call.
 *
 * `NEXT_CLOUD` and `WEBDAV` share the one WebDAV state store ([SyncSettingsPlatform.webDavState]),
 * so switching between them recalibrates from `UNKNOWN` and one certificate pin serves both. Known
 * limitation.
 */
class IosCloudAdapterFactory(
    private val platform: SyncSettingsPlatform,
    private val deviceId: String,
    private val displayName: (String) -> String,
    private val iCloudStore: () -> CloudFileStore,
    private val signInUi: WebDavSignInUi,
    private val tempDir: Path,
    private val clientFactory: (WebDavConfig, WebDavStateStore) -> WebDavClient,
    private val awaitReady: suspend () -> Unit = {},
) : CloudAdapterFactory {
    override val available: List<String> = listOf("ICLOUD_DRIVE", "NEXT_CLOUD", "WEBDAV")

    override fun displayName(id: String): String = displayName.invoke(id)

    override fun create(id: String): CloudAdapter = create(id, signInUi)

    override fun createForBackground(id: String): CloudAdapter = create(id, IosBackgroundSignInUi(signInUi))

    private fun create(id: String, ui: WebDavSignInUi): CloudAdapter = when (id) {
        "ICLOUD_DRIVE" -> FileTreeCloudAdapter(iCloudStore(), deviceId, tempDir, awaitReady = awaitReady)
        "NEXT_CLOUD" -> {
            val user = platform.credential("cloud_sync_username")
            val serverUrl = platform.credential("cloud_sync_server_url").trim()
            webDav(
                WebDavConfig(
                    nextcloudDavUrl(serverUrl, user),
                    user,
                    platform.credential("cloud_sync_password"),
                    platform.credential("cloud_sync_folder_path"),
                ), ui,
                beforeClient = {
                    if (!platform.plainHttpAllowed(serverUrl)) {
                        val refusal = PlainHttpRefusedException(serverUrl)
                        throw net.bible.service.cloudsync.CloudSyncUserFacingException(
                            ui.message(net.bible.service.cloudsync.webdav.WebDavMessage.HTTPS_REQUIRED), true, refusal,
                        )
                    }
                },
            )
        }
        "WEBDAV" -> webDav(
            WebDavConfig(
                platform.credential(WebDavPrefKeys.SERVER_URL),
                platform.credential(WebDavPrefKeys.USERNAME),
                platform.credential(WebDavPrefKeys.PASSWORD),
                platform.credential(WebDavPrefKeys.FOLDER_PATH),
            ), ui,
        )
        else -> throw IllegalStateException("Unknown cloud adapter id: $id")
    }

    private fun webDav(config: WebDavConfig, ui: WebDavSignInUi, beforeClient: () -> Unit = {}) =
        WebDavCloudAdapter(config, platform.webDavState, ui, deviceId, { c, s ->
            beforeClient()
            clientFactory(c, s)
        })
}
