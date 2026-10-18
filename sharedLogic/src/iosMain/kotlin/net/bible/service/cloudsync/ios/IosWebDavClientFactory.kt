package net.bible.service.cloudsync.ios

import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import net.bible.service.cloudsync.webdav.PinDecision
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.service.cloudsync.webdav.pinDecision
import net.bible.service.cloudsync.webdav.sha256HexUpper
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.DavException
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecCertificateCopySubjectSummary
import platform.Security.SecCertificateRef
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRef

private const val TAG = "IosWebDavClient"

/** What to answer a server-trust challenge with; Security/Foundation free so it is unit-testable. */
internal enum class ChallengeAction { DEFAULT_HANDLING, USE_PINNED_CREDENTIAL, CANCEL }

internal fun challengeAction(decision: PinDecision): ChallengeAction = when (decision) {
    PinDecision.TRUST_SYSTEM -> ChallengeAction.DEFAULT_HANDLING
    PinDecision.TRUST_PINNED -> ChallengeAction.USE_PINNED_CREDENTIAL
    PinDecision.REJECT -> ChallengeAction.CANCEL
}

internal const val URL_ERROR_DOMAIN = "NSURLErrorDomain"

/**
 * True for the NSURLSession errors a cancelled/failed server-trust challenge produces:
 * NSURLErrorCancelled (-999) and NSURLErrorServerCertificate* (-1201..-1206).
 */
internal fun isCertificateRejectionError(domain: String?, code: Long): Boolean =
    domain == URL_ERROR_DOMAIN && (code == -999L || code in -1206L..-1201L)

/**
 * Maps a request failure to [DavUntrustedCertificateException] only when it is a certificate-style
 * NSURLSession error AND a rejection is pending in [takeRejected] (which must clear it). Any other
 * failure (offline, DNS, timeout, ...) is left alone and does not consume the pending rejection.
 */
internal fun mapCertificateRejection(
    domain: String?,
    code: Long,
    takeRejected: () -> DavCertificateInfo?,
): DavException? {
    if (!isCertificateRejectionError(domain, code)) return null
    return takeRejected()?.let(::DavUntrustedCertificateException)
}

private fun Throwable.nsError(): NSError? {
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth++ < 10) {
        if (t is DarwinHttpRequestException) return t.origin
        t = t.cause
    }
    return null
}

/**
 * The production iOS [WebDavClient]: ktor Darwin (NSURLSession) engine with pinned-certificate trust
 * (spec C5). No User-Agent is set explicitly; ktor's default one is added when none is set. It does not name
 * AndBible, so the app still does not identify itself to the server.
 *
 * Server-trust challenges are evaluated with `SecTrustEvaluateWithError`; [pinDecision] then decides,
 * using the challenge's protection-space host (the host actually being connected to, so a pin only
 * applies to the host it was taken on; the adapter stores `CertPin(e.certificate.host, sha256)`).
 * System-trusted chains use default handling, a leaf whose SHA-256 equals the stored pin for that
 * host is accepted, anything else is cancelled and surfaced as [DavUntrustedCertificateException]
 * (never silently accepted). The rejection is only mapped for NSURLSession cancel/certificate errors
 * and is consumed once, so later unrelated failures stay plain errors. If the leaf certificate cannot
 * be read the challenge is cancelled without recording a rejection: it surfaces as a plain error.
 * Basic/Digest challenges get default handling; [WebDavClient] answers them.
 *
 * Phase-1 limitation: the reported issuer is empty and notBefore/notAfter are 0 because reading them
 * needs DER parsing (no public Security API); the trust prompt is a pending UI anyway.
 */
@OptIn(ExperimentalForeignApi::class)
fun createIosWebDavClient(config: WebDavConfig, state: WebDavStateStore, nowMs: () -> Long): WebDavClient {
    val lastRejected = atomic<DavCertificateInfo?>(null)
    val engine = Darwin.create {
        handleChallenge { _, _, challenge, completion ->
            val space = challenge.protectionSpace
            if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
                completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                return@handleChallenge
            }
            val trust = space.serverTrust
            val systemTrusted = trust != null && SecTrustEvaluateWithError(trust, null)
            val leaf = trust?.let { leafInfo(it) }
            if (trust == null || leaf == null) {
                Log.w(TAG, "Server certificate unreadable for ${space.host}; cancelling")
                completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                return@handleChallenge
            }
            val decision = pinDecision(systemTrusted, space.host, leaf.sha256, state.certPin)
            when (challengeAction(decision)) {
                ChallengeAction.DEFAULT_HANDLING -> {
                    lastRejected.value = null
                    completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                }
                ChallengeAction.USE_PINNED_CREDENTIAL -> {
                    lastRejected.value = null
                    completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                }
                ChallengeAction.CANCEL -> {
                    lastRejected.value = DavCertificateInfo(space.host, leaf.sha256, leaf.subject, "", 0, 0)
                    Log.w(TAG, "Rejected untrusted certificate for ${space.host}")
                    completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                }
            }
        }
    }
    return WebDavClient(
        createWebDavHttpClient(engine, config.username, config.password),
        config.serverUrl, config.username, config.password, nowMs,
        mapPlatformError = { e ->
            e.nsError()?.let { err ->
                mapCertificateRejection(err.domain, err.code) { lastRejected.getAndSet(null) }
            }
        },
    )
}

private class LeafInfo(val sha256: String, val subject: String)

/** SHA-256 and subject of the leaf, read while the chain is still alive. */
@OptIn(ExperimentalForeignApi::class)
private fun leafInfo(trust: SecTrustRef): LeafInfo? {
    val chain = SecTrustCopyCertificateChain(trust) ?: return null
    try {
        if (CFArrayGetCount(chain) < 1) return null
        val leaf: SecCertificateRef = CFArrayGetValueAtIndex(chain, 0)?.reinterpret() ?: return null
        val der = derBytes(leaf) ?: return null
        val subject = SecCertificateCopySubjectSummary(leaf)?.let { CFBridgingRelease(it) as? String } ?: ""
        return LeafInfo(sha256HexUpper(der), subject)
    } finally {
        CFRelease(chain)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun derBytes(cert: SecCertificateRef): ByteArray? {
    val data = SecCertificateCopyData(cert)?.let { CFBridgingRelease(it) as? NSData } ?: return null
    val out = ByteArray(data.length.toInt())
    if (out.isNotEmpty()) out.usePinned { platform.posix.memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
}

/**
 * Phase-1 sign-in UI: iOS has no certificate-trust prompt yet (spec C7), so an untrusted certificate
 * is declined. [messages] supplies the localized texts.
 */
class PendingWebDavSignInUi(private val messages: (WebDavMessage) -> String) : WebDavSignInUi {
    override suspend fun confirmCertificate(info: DavCertificateInfo): Boolean {
        Log.w("PendingWebDavSignInUi", "I2-pending: WebDAV certificate prompt")
        return false
    }

    override fun message(kind: WebDavMessage): String = messages(kind)
}
