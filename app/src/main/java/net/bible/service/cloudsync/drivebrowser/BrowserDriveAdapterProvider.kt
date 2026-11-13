package net.bible.service.cloudsync.drivebrowser

import io.ktor.client.HttpClient
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.platform.DriveAdapterProvider
import net.bible.service.cloudsync.drive.GoogleDriveRestAdapter
import net.bible.service.cloudsync.drive.oauth.GoogleDriveSecretKeys
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients
import net.bible.service.cloudsync.drive.oauth.PkceGoogleAuth
import net.bible.sharedcore.platform.secrets.SecretStore
import org.koin.java.KoinJavaComponent

/**
 * Google Drive with the browser sign-in (OAuth 2.0 + PKCE with [GoogleOAuthClients.androidWeb], redirect through the
 * verified App Link): the F-Droid build, and Play builds on devices without Play services. Play-free.
 */
class BrowserDriveAdapterProvider(
    private val secrets: SecretStore = KoinJavaComponent.get(SecretStore::class.java),
    private val http: HttpClient = DriveHttp.sharedClient,
    private val openUrl: (String) -> Unit = AppLinkOAuthBrowser::openInBrowser,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : DriveAdapterProvider {
    override fun create(): GoogleDriveRestAdapter = adapter(interactive = true)

    /** Background sync restores the stored sign-in only; it never opens the browser. */
    override fun createForBackground(): GoogleDriveRestAdapter = adapter(interactive = false)

    internal fun auth(interactive: Boolean) =
        PkceGoogleAuth(GoogleOAuthClients.androidWeb, AppLinkOAuthBrowser(openUrl), secrets, http, nowMs, interactive)

    private fun adapter(interactive: Boolean) = GoogleDriveRestAdapter(
        auth(interactive),
        http,
        signInRequiredMessage = { BibleApplication.application.getString(R.string.google_drive_signin_required) },
    )

    companion object {
        /** Whether a browser sign-in is stored (the sticky rule of [chooseDriveAuth]). */
        fun tokenStored(secrets: SecretStore): Boolean = secrets.get(GoogleDriveSecretKeys.REFRESH_TOKEN) != null
    }
}
