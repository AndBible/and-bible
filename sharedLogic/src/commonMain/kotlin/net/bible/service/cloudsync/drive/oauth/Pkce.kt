package net.bible.service.cloudsync.drive.oauth

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** PKCE (RFC 7636) helpers. */
internal object Pkce {
    /** 64 base64url characters from 48 random bytes (three random UUIDs). */
    fun verifier(): String = randomBase64Url(3)

    /** `base64url(sha256(verifier))` without padding (method S256). */
    fun challenge(verifier: String): String = verifier.encodeUtf8().sha256().base64Url().trimEnd('=')

    /** Opaque anti-CSRF value echoed back in the redirect. */
    fun state(): String = randomBase64Url(2)

    @OptIn(ExperimentalUuidApi::class)
    private fun randomBase64Url(uuids: Int): String {
        val bytes = ByteArray(16 * uuids)
        repeat(uuids) { Uuid.random().toByteArray().copyInto(bytes, it * 16) }
        return bytes.toByteString().base64Url().trimEnd('=')
    }
}
