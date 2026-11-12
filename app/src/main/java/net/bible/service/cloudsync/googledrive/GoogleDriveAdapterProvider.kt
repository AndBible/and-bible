package net.bible.service.cloudsync.googledrive

import io.ktor.client.engine.okhttp.OkHttp
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.createGoogleDriveHttpClient

/**
 * Loaded reflectively by `AndroidCloudAdapterFactory`. Lives in `googledrive/` so the F-Droid
 * exclusion removes it together with the Play Services code.
 */
class GoogleDriveAdapterProvider {
    private val httpClient by lazy { createGoogleDriveHttpClient(OkHttp.create()) }

    fun create(useShared: Boolean): CloudAdapter =
        if (useShared) GoogleDriveRestAdapter(AndroidGoogleAuth(), httpClient) else GoogleDriveCloudAdapter()
}
