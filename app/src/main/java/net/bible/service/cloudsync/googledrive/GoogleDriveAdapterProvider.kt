package net.bible.service.cloudsync.googledrive

import io.ktor.client.HttpClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.platform.DriveAdapterProvider
import net.bible.service.cloudsync.CloudAdapter
import net.bible.service.cloudsync.TAG
import net.bible.service.cloudsync.drive.GoogleAuth
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drivebrowser.BrowserDriveAdapterProvider
import net.bible.service.cloudsync.drivebrowser.DriveAuthChoice
import net.bible.service.cloudsync.drivebrowser.DriveHttp
import net.bible.service.cloudsync.drivebrowser.chooseDriveAuth
import net.bible.service.common.BuildVariant
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.secrets.SecretStore
import org.koin.java.KoinJavaComponent

/**
 * Loaded reflectively by `AndroidCloudAdapterFactory` (needs the no-argument constructor all-default parameters give).
 * Lives in `googledrive/` so the F-Droid exclusion removes it together with the Play Services code.
 *
 * Chooses the sign-in per [chooseDriveAuth]: `AndroidGoogleAuth` when Play services are available, the browser sign-in
 * ([BrowserDriveAdapterProvider]) on devices without them, and the browser for good once a browser sign-in is stored.
 */
class GoogleDriveAdapterProvider(
    private val playServicesStatus: () -> Int = {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(BibleApplication.application)
    },
    private val secrets: () -> SecretStore = { KoinJavaComponent.get(SecretStore::class.java) },
    private val browser: () -> DriveAdapterProvider = { BrowserDriveAdapterProvider() },
    private val playAuth: () -> GoogleAuth = { AndroidGoogleAuth() },
) : DriveAdapterProvider {
    override fun create(): CloudAdapter = if (useBrowser()) browser().create() else createRest(playAuth(), sharedClient)

    override fun createForBackground(): CloudAdapter =
        if (useBrowser()) browser().createForBackground() else createRest(playAuth(), sharedClient)

    internal fun choice(): DriveAuthChoice {
        val status = playServicesStatus()
        val choice = chooseDriveAuth(
            playServicesAvailable = status == ConnectionResult.SUCCESS,
            browserTokenStored = BrowserDriveAdapterProvider.tokenStored(secrets()),
            appLinkAvailable = !BuildVariant.Appearance.isDiscrete,
        )
        Log.i(TAG, "Google Drive sign-in: $choice (Play services status $status)")
        return choice
    }

    private fun useBrowser() = choice() == DriveAuthChoice.BROWSER

    internal fun createRest(auth: GoogleAuth, client: HttpClient) = GoogleDriveRestAdapter(
        auth,
        client,
        signInRequiredMessage = { BibleApplication.application.getString(R.string.google_drive_signin_required) },
    )

    companion object {
        /** The process-wide client (one OkHttp engine), shared with the browser provider. */
        internal val sharedClient: HttpClient get() = DriveHttp.sharedClient
    }
}
