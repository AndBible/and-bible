package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.CloudAdapterFactory
import net.bible.service.cloudsync.drivebrowser.BrowserDriveAdapterProvider
import net.bible.service.cloudsync.nextcloud.NextCloudAdapter
import net.bible.service.cloudsync.webdav.AndroidWebDavSignInUi
import net.bible.service.cloudsync.webdav.PrefsWebDavStateStore
import net.bible.service.cloudsync.webdav.WebDavCloudAdapter
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.platform.AppEnvironment
import net.bible.service.cloudsync.webdav.createWebDavClient
import net.bible.service.cloudsync.webdav.fromPreferences
import org.koin.java.KoinJavaComponent

/**
 * @param driveSignIn how this build signs in to Google Drive ([AndroidDriveSignIn.current]).
 * @param loadPlayProvider loads the Play provider by name; never called for [AndroidDriveSignIn.BROWSER].
 */
class AndroidCloudAdapterFactory(
    private val context: Context,
    private val driveSignIn: AndroidDriveSignIn = AndroidDriveSignIn.current(),
    private val loadPlayProvider: () -> DriveAdapterProvider = ::loadPlayDriveProvider,
    private val browserProvider: () -> DriveAdapterProvider = { BrowserDriveAdapterProvider() },
) : CloudAdapterFactory {
    override val available: List<String> get() =
        if (driveSignIn == AndroidDriveSignIn.NONE) listOf("NEXT_CLOUD", "WEBDAV") else listOf("GOOGLE_DRIVE", "NEXT_CLOUD", "WEBDAV")
    override fun displayName(id: String): String {
        val resource = when (id) {
            "GOOGLE_DRIVE" -> R.string.adapters_google_drive
            "NEXT_CLOUD" -> R.string.adapters_next_cloud
            "WEBDAV" -> R.string.adapters_webdav
            else -> return id
        }
        return context.getString(resource)
    }
    override fun create(id: String): CloudAdapter = when (id) {
        "GOOGLE_DRIVE" -> driveProvider().create()
        "NEXT_CLOUD" -> CommonUtils.realSharedPreferences.let { prefs ->
            NextCloudAdapter(prefs.getString("cloud_sync_server_url", null), prefs.getString("cloud_sync_username", null), prefs.getString("cloud_sync_password", null), prefs.getString("cloud_sync_folder_path", null), context.getString(R.string.webdav_https_required))
        }
        "WEBDAV" -> CommonUtils.realSharedPreferences.let { prefs ->
            WebDavCloudAdapter(WebDavConfig.fromPreferences(prefs), PrefsWebDavStateStore(prefs), AndroidWebDavSignInUi(context), KoinJavaComponent.get<AppEnvironment>(AppEnvironment::class.java).deviceIdentifier, ::createWebDavClient)
        }
        else -> error("Unknown cloud adapter: $id")
    }

    override fun createForBackground(id: String): CloudAdapter =
        if (id == "GOOGLE_DRIVE") driveProvider().createForBackground() else create(id)

    private fun driveProvider(): DriveAdapterProvider = when (driveSignIn) {
        AndroidDriveSignIn.PLAY_PROVIDER -> loadPlayProvider()
        AndroidDriveSignIn.BROWSER -> browserProvider()
        AndroidDriveSignIn.NONE -> error("Google Drive is not available in this build")
    }
}

/** Loaded by name: the Play provider lives in googledrive/, which the F-Droid build excludes. */
private fun loadPlayDriveProvider(): DriveAdapterProvider =
    Class.forName("net.bible.service.cloudsync.googledrive.GoogleDriveAdapterProvider").getDeclaredConstructor().newInstance() as DriveAdapterProvider
