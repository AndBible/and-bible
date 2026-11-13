package net.bible.service.cloudsync.drivebrowser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.android.BibleApplication
import net.bible.android.activity.BuildConfig
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.TAG
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients
import net.bible.service.cloudsync.drive.oauth.OAuthBrowser
import net.bible.sharedcore.log.Log

/**
 * [OAuthBrowser] for Android without Play services: opens Google's consent page in the user's browser and waits for the
 * redirect to [GoogleOAuthClients.ANDROID_WEB_REDIRECT_URI], which Android hands to AndBible as a verified App Link and
 * `StartupActivity` passes on through [OAuthRedirectInbox].
 *
 * Ends with [CloudSyncUserCancelledException] after [timeoutMs], when superseded by a newer sign-in, or when AndBible is
 * back in front ([OAuthRedirectInbox.hostResumed]) and no redirect arrived within [closedGraceMs]: the user closed the
 * browser without allowing.
 */
class AppLinkOAuthBrowser(
    private val openUrl: (String) -> Unit = ::openInBrowser,
    private val timeoutMs: Long = 5 * 60_000,
    private val closedGraceMs: Long = 3_000,
) : OAuthBrowser {
    override suspend fun authorize(buildUrl: (redirectUri: String) -> String): String {
        val url = buildUrl(GoogleOAuthClients.ANDROID_WEB_REDIRECT_URI)
        val attempt = OAuthRedirectInbox.Attempt()
        OAuthRedirectInbox.register(attempt)
        try {
            return withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    openUrl(url)
                    attempt.browserOpened = true
                    val closedWatch = launch {
                        attempt.resumedAfterOpen.first { it }
                        delay(closedGraceMs)
                        attempt.result.completeExceptionally(CloudSyncUserCancelledException())
                    }
                    try {
                        attempt.result.await()
                    } finally {
                        closedWatch.cancel()
                    }
                }
            } ?: throw CloudSyncUserCancelledException()
        } finally {
            OAuthRedirectInbox.clear(attempt)
        }
    }

    companion object {
        /** Production opener: a plain `ACTION_VIEW` (no Custom Tabs library), from the current activity if any. */
        fun openInBrowser(url: String) {
            if (BuildConfig.DEBUG) Log.i(TAG, "Drive browser sign-in URL: $url")
            val context: Context = CurrentActivityHolder.currentActivity ?: BibleApplication.application
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "No browser for the Drive sign-in", e)
                throw CloudSyncUserCancelledException()
            }
        }
    }
}
