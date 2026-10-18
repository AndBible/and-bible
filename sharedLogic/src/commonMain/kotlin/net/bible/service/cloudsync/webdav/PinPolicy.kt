package net.bible.service.cloudsync.webdav

import okio.ByteString.Companion.toByteString

/** Outcome of the TLS trust decision for a WebDAV server certificate. */
enum class PinDecision { TRUST_SYSTEM, TRUST_PINNED, REJECT }

/**
 * Decides whether to accept a server certificate: the system trust store first, otherwise only a
 * pin recorded for the same [host] whose SHA-256 equals [leafSha256] (case-insensitive).
 */
fun pinDecision(systemTrusted: Boolean, host: String, leafSha256: String, pin: CertPin?): PinDecision = when {
    systemTrusted -> PinDecision.TRUST_SYSTEM
    pin != null && pin.host == host && pin.sha256.equals(leafSha256, ignoreCase = true) -> PinDecision.TRUST_PINNED
    else -> PinDecision.REJECT
}

/** Upper-case hex SHA-256 of a DER certificate; same text as Android's `sha256Hex`. */
fun sha256HexUpper(der: ByteArray): String = der.toByteString().sha256().hex().uppercase()
