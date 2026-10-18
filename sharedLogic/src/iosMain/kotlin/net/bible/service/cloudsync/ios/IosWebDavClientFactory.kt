package net.bible.service.cloudsync.ios

import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import net.bible.service.cloudsync.webdav.PinDecision
import net.bible.service.cloudsync.webdav.WebDavConfig
import net.bible.service.cloudsync.webdav.WebDavSignInUi
import net.bible.service.cloudsync.webdav.WebDavMessage
import net.bible.service.cloudsync.webdav.WebDavStateStore
import net.bible.service.cloudsync.webdav.pinDecision
import net.bible.service.cloudsync.webdav.sha256HexUpper
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.webdav.DavCertificateInfo
import net.bible.sharedcore.webdav.DavUntrustedCertificateException
import net.bible.sharedcore.webdav.WebDavClient
import net.bible.sharedcore.webdav.createWebDavHttpClient
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.Foundation.CFBridgingRelease
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSData
import platform.Foundation.NSURLAuthenticationChallenge
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
import kotlinx.atomicfu.atomic

private const val TAG = "IosWebDavClient"

/**
 * The production iOS [WebDavClient]: ktor Darwin (NSURLSession) engine with pinned-certificate trust
 * for [WebDavConfig.host] only (spec C5). No User-Agent header is set, so the app does not identify
 * itself to the server.
 *
 * Server-trust challenges are evaluated with `SecTrustEvaluateWithError`; [pinDecision] then decides:
 * system-trusted chains use default handling, a chain whose leaf SHA-256 equals the stored pin for the
 * same host is accepted, anything else is cancelled and surfaced as [DavUntrustedCertificateException]
 * (never silently accepted). Basic/Digest challenges get default handling; [WebDavClient] answers them.
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
            val leaf = trust?.let { leafCertificate(it) }
            val der = leaf?.let { derBytes(it) }
            if (trust == null || leaf == null || der == null) {
                // No evidence to pin against: fail closed.
                lastRejected.value = DavCertificateInfo(space.host, "", "", "", 0, 0)
                completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                return@handleChallenge
            }
            val sha256 = sha256HexUpper(der)
            val systemTrusted = SecTrustEvaluateWithError(trust, null)
            when (pinDecision(systemTrusted, space.host, sha256, state.certPin)) {
                PinDecision.TRUST_SYSTEM -> completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                PinDecision.TRUST_PINNED ->
                    completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                PinDecision.REJECT -> {
                    val subject = SecCertificateCopySubjectSummary(leaf)?.let { CFBridgingRelease(it) as? String } ?: ""
                    lastRejected.value = DavCertificateInfo(space.host, sha256, subject, "", 0, 0)
                    Log.w(TAG, "Rejected untrusted certificate for ${space.host}")
                    completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                }
            }
        }
    }
    return WebDavClient(
        createWebDavHttpClient(engine, config.username, config.password),
        config.serverUrl, config.username, config.password, nowMs,
        mapPlatformError = { lastRejected.value?.let(::DavUntrustedCertificateException) },
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun leafCertificate(trust: SecTrustRef): SecCertificateRef? {
    val chain = SecTrustCopyCertificateChain(trust) ?: return null
    try {
        if (CFArrayGetCount(chain) < 1) return null
        return CFArrayGetValueAtIndex(chain, 0)?.reinterpret()
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
