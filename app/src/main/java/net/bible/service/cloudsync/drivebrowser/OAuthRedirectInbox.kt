package net.bible.service.cloudsync.drivebrowser

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.service.cloudsync.CloudSyncUserCancelledException
import java.util.concurrent.atomic.AtomicReference

/**
 * The process-wide hand-over point between the browser sign-in ([AppLinkOAuthBrowser], which waits) and the activity
 * that receives Google's redirect App Link (`StartupActivity`, which delivers). At most one attempt is pending; a new
 * one supersedes the old one, which then fails with [CloudSyncUserCancelledException].
 *
 * Play-free: lives outside `googledrive/` so every build flavor compiles it.
 */
object OAuthRedirectInbox {
    private const val HOST = "read.andbible.org"
    private const val PATH = "/oauth2/drive"

    /** One pending sign-in: its result, and whether AndBible came back to the front after the browser opened. */
    internal class Attempt {
        val result = CompletableDeferred<String>()

        /** Set once the browser intent has been sent; resumes before that are caused by the open itself. */
        @Volatile var browserOpened = false

        /** True once the host resumed after the browser opened (the user may have closed the browser). */
        val resumedAfterOpen = MutableStateFlow(false)
    }

    private val current = AtomicReference<Attempt?>(null)

    /** True for Google's redirect to [net.bible.service.cloudsync.drive.oauth.GoogleOAuthClients.ANDROID_WEB_REDIRECT_URI], success or error. */
    fun isOAuthRedirect(uri: Uri?): Boolean =
        uri != null && uri.scheme == "https" && uri.host == HOST && (uri.path == PATH || uri.path == "$PATH/")

    /** Hands [url] to the pending attempt. False when no attempt is waiting (stale redirect: process died, replay). */
    fun deliver(url: String): Boolean = current.get()?.result?.complete(url) ?: false

    /** Called whenever the reading host resumes; arms the "browser was closed" grace timer of a pending attempt. */
    fun hostResumed() {
        current.get()?.takeIf { it.browserOpened }?.resumedAfterOpen?.value = true
    }

    /** Whether an attempt is waiting for its redirect. */
    val hasPending: Boolean get() = current.get() != null

    internal fun register(attempt: Attempt) {
        current.getAndSet(attempt)?.result?.completeExceptionally(CloudSyncUserCancelledException())
    }

    internal fun clear(attempt: Attempt) {
        current.compareAndSet(attempt, null)
    }
}
