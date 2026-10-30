/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

package net.bible.sharedlogic.security

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies the detached `.sign` signature of a feature file (pure JVM port of Android `CommonUtils.verifySignature`:
 * SHA1withRSA over the file's bytes, signature read from [signatureFile]). Any problem (missing or unreadable
 * signature, malformed key, mismatch) is "not valid"; this never throws for bad input.
 *
 * [publicKeyPem] is the PEM text of the RSA public key; the default is the app's bundled `publickey.pem`
 * (the same key as Android's `R.raw.publickey`).
 */
class FeatureSignatureVerifier(publicKeyPem: String = bundledPublicKeyPem()) {
    private val publicKey: RSAPublicKey? = runCatching {
        val body = publicKeyPem.lineSequence().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(body))) as RSAPublicKey
    }.getOrNull()

    fun verify(file: Path, signatureFile: Path): Boolean {
        val key = publicKey ?: return false
        return try {
            if (!Files.isReadable(signatureFile) || !Files.isRegularFile(file)) return false
            val signature = Signature.getInstance("SHA1withRSA")
            signature.initVerify(key)
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    signature.update(buffer, 0, read)
                }
            }
            // Android reads exactly modulus-size bytes of the signature file.
            val size = key.modulus.bitLength() / 8
            val bytes = Files.newInputStream(signatureFile).use { it.readNBytes(size) }
            signature.verify(bytes)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun bundledPublicKeyPem(): String =
            FeatureSignatureVerifier::class.java.getResourceAsStream("/net/bible/sharedlogic/security/publickey.pem")!!
                .use { it.readBytes().decodeToString() }
    }
}
