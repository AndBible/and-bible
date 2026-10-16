package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.nextcloud.NextCloudAdapter
import net.bible.service.cloudsync.webdav.AndroidWebDavSignInUi
import net.bible.service.cloudsync.webdav.PrefsWebDavStateStore
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils

class AndroidCloudAdapterFactory(private val context: Context) : CloudAdapterFactory {
    override val available: List<String> get() = if (BuildVariant.DistributionChannel.isFdroid) listOf("NEXT_CLOUD", "WEBDAV") else listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV")
    override fun displayName(id: String): String = context.getString(when (id) {
        "GOOGLE_DRIVE" -> R.string.adapters_google_drive
        "NEXT_CLOUD" -> R.string.adapters_next_cloud
        "WEBDAV" -> R.string.adapters_webdav
        else -> error("Unknown cloud adapter: $id")
    })
    override fun create(id: String): CloudAdapter = when (id) {
        "GOOGLE_DRIVE" -> Class.forName("net.bible.service.cloudsync.googledrive.GoogleDriveCloudAdapter").getDeclaredConstructor().newInstance() as CloudAdapter
        "NEXT_CLOUD" -> CommonUtils.realSharedPreferences.let { prefs ->
            NextCloudAdapter(prefs.getString("cloud_sync_server_url", null), prefs.getString("cloud_sync_username", null), prefs.getString("cloud_sync_password", null), prefs.getString("cloud_sync_folder_path", null))
        }
        "WEBDAV" -> CommonUtils.realSharedPreferences.let { prefs ->
            WebDavCloudAdapter(WebDavConfig.fromPreferences(prefs), PrefsWebDavStateStore(prefs), AndroidWebDavSignInUi(context))
        }
        else -> error("Unknown cloud adapter: $id")
    }
}
