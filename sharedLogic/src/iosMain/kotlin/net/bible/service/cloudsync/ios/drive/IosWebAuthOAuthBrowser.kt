package net.bible.service.cloudsync.ios.drive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import net.bible.service.cloudsync.drive.oauth.GoogleOAuthClient
import net.bible.service.cloudsync.drive.oauth.OAuthBrowser
import okio.IOException
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Google sign-in through `ASWebAuthenticationSession`. The redirect is the reversed iOS client id
 * (`com.googleusercontent.apps.<prefix>:/oauth2redirect`); the callback URL is returned untouched, so `error=`
 * handling stays in the common PKCE flow.
 *
 * @param start Starts the session for `url` and calls `completion` once; returns a handle that cancels it. Injected
 *   so tests need no UIKit.
 */
internal class IosWebAuthOAuthBrowser(
    private val client: GoogleOAuthClient,
    private val start: (url: NSURL, scheme: String, completion: (NSURL?, NSError?) -> Unit) -> () -> Unit =
        ::startWebAuthSession,
) : OAuthBrowser {

    override suspend fun authorize(buildUrl: (redirectUri: String) -> String): String {
        val prefix = client.clientId.removeSuffix(".apps.googleusercontent.com")
        val scheme = "com.googleusercontent.apps.$prefix"
        val redirectUri = "$scheme:/oauth2redirect"
        val urlString = buildUrl(redirectUri)
        val url = NSURL.URLWithString(urlString) ?: throw IOException("Invalid authorization URL")
        return suspendCancellableCoroutine { cont: CancellableContinuation<String> ->
            val cancel = start(url, scheme) { callback, error ->
                if (!cont.isActive) return@start
                when {
                    callback != null -> {
                        val s = callback.absoluteString
                        if (s != null) cont.resumeWith(Result.success(s))
                        else cont.resumeWith(Result.failure(IOException("Empty OAuth callback URL")))
                    }
                    error != null -> cont.resumeWith(Result.failure(mapAuthError(error)))
                    else -> cont.resumeWith(Result.failure(IOException("OAuth session returned neither URL nor error")))
                }
            }
            cont.invokeOnCancellation { cancel() }
        }
    }
}

private fun mapAuthError(error: NSError): Throwable =
    if (error.domain == ASWebAuthenticationSessionErrorDomain &&
        error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin
    ) {
        CloudSyncUserCancelledException()
    } else {
        IOException("OAuth sign-in failed: ${error.domain} ${error.code}: ${error.localizedDescription}")
    }

/** Supplies the key window of the foreground-active scene as the sheet's anchor. */
private class WebAuthAnchorProvider : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor {
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        val scene = scenes.firstOrNull { it.activationState == platform.UIKit.UISceneActivationStateForegroundActive }
            ?: scenes.firstOrNull()
        val window: UIWindow? = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
            ?: scene?.windows?.firstOrNull() as? UIWindow
        return window ?: UIWindow()
    }
}

/** Live sessions; `ASWebAuthenticationSession` and its (weakly held) anchor provider must outlive this call. */
private val liveSessions = mutableSetOf<Pair<ASWebAuthenticationSession, WebAuthAnchorProvider>>()

@OptIn(ExperimentalForeignApi::class)
internal fun startWebAuthSession(
    url: NSURL,
    scheme: String,
    completion: (NSURL?, NSError?) -> Unit,
): () -> Unit {
    var entry: Pair<ASWebAuthenticationSession, WebAuthAnchorProvider>? = null
    var cancelled = false
    dispatch_async(dispatch_get_main_queue()) {
        if (cancelled) return@dispatch_async
        val provider = WebAuthAnchorProvider()
        lateinit var session: ASWebAuthenticationSession
        session = ASWebAuthenticationSession(uRL = url, callbackURLScheme = scheme) { callback, error ->
            liveSessions.removeAll { it.first === session }
            completion(callback, error)
        }
        session.presentationContextProvider = provider
        session.prefersEphemeralWebBrowserSession = false
        val pair = session to provider
        entry = pair
        liveSessions.add(pair)
        if (!session.start()) {
            liveSessions.remove(pair)
            completion(null, NSError.errorWithDomain("IosWebAuthOAuthBrowser", 0, null))
        }
    }
    return {
        dispatch_async(dispatch_get_main_queue()) {
            cancelled = true
            entry?.first?.cancel()
        }
    }
}
