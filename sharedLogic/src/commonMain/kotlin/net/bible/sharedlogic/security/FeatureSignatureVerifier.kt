package net.bible.sharedlogic.security

import okio.ByteString.Companion.decodeBase64
import okio.FileSystem
import okio.Path

/**
 * Checks the detached binary `.sign` of an add-on feature using the existing SHA1withRSA format.
 * Missing/unreadable files, malformed keys and invalid signatures fail closed on every platform.
 */
class FeatureSignatureVerifier(publicKeyPem: String = bundledPublicKeyPem()) {
    private val publicKeyDer = runCatching {
        publicKeyPem.lineSequence().filter { it.isNotBlank() && !it.startsWith("-----") }
            .joinToString("").decodeBase64()?.toByteArray()
    }.getOrNull()

    /** Reads [file] once and verifies those bytes; callers that serve the file should use [verifyBytes] and serve the same bytes. */
    fun verify(file: Path, signatureFile: Path): Boolean = try {
        val fs = FileSystem.SYSTEM
        if (!fs.metadata(fs.canonicalize(file)).isRegularFile) false
        else verifyBytes(fs.read(file) { readByteArray() }, signatureFile)
    } catch (_: Exception) {
        false
    }

    /** Verifies already-read [content] against the detached [signatureFile], so a verified body can be served without a second read. */
    fun verifyBytes(content: ByteArray, signatureFile: Path): Boolean = try {
        val key = publicKeyDer
        val fs = FileSystem.SYSTEM
        val size = fs.metadata(signatureFile).size
        if (key == null || size == null || size > MAX_SIGNATURE_BYTES) false
        else verifyFeatureSignature(key, content, fs.read(signatureFile) { readByteArray() })
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val MAX_SIGNATURE_BYTES = 8192L

        /** The one AndBible release key, used by Android, desktop and iOS. */
        fun bundledPublicKeyPem(): String = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzF0Mn43rUT3HPrNvYd1I
GBd43PUFQ5hEKzBQpbIDmTb8NIt3oAQwlmQxKmPF/KlpmVcIl9RitqhT4x7Ab1+G
fk1rF+1quK3hMqSMBeZZqyO0tpyLJClZXlrjYgwnCHmXEscy7l7Y4Hzn1aNHeRwr
YLb+SgV2S7YqeHoHzcQCZ/CiRrZsoPHvdR1yLT05rgxjOz6Zd27uiO3mj8feIvRh
kYhpBncNG/0ze1AiQVrGqM+lJ9tK3Lt7mJ283WXBMIlrU3maISS+sUTA2ZTsyRa0
HYzM8pkX/EHOdvXbK4Hq4n9+7hZVzXWdwlLFlysNEESiNYvvrqdCJ3z3XlEKSvXe
6QIDAQAB
-----END PUBLIC KEY-----"""
    }
}

/** Platform crypto over the detached [signature] (bytes beyond the modulus size are ignored); the caller converts all I/O and format failures to false. */
internal expect fun verifyFeatureSignature(publicKeyDer: ByteArray, content: ByteArray, signature: ByteArray): Boolean
