package net.bible.service.cloudsync.googledrive

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import net.bible.android.platform.DriveAdapterProvider
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient

/**
 * Loaded reflectively by `AndroidCloudAdapterFactory`. Lives in `googledrive/` so the F-Droid
 * exclusion removes it together with the Play Services code.
 */
class GoogleDriveAdapterProvider : DriveAdapterProvider {
    override fun create(useShared: Boolean): CloudAdapter =
        if (useShared) GoogleDriveRestAdapter(AndroidGoogleAuth(), sharedClient) else GoogleDriveCloudAdapter()

    companion object {
        /** One process-wide client (one OkHttp engine); the factory builds a provider per create(). */
        internal val sharedClient: HttpClient by lazy { createGoogleDriveHttpClient(OkHttp.create()) }
    }
}
