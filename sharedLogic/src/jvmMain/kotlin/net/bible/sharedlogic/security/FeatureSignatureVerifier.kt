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

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

internal actual fun verifyFeatureSignature(publicKeyDer: ByteArray, content: ByteArray, signature: ByteArray): Boolean {
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer)) as RSAPublicKey
    val verifier = Signature.getInstance("SHA1withRSA").apply { initVerify(key) }
    verifier.update(content)
    // Match Android's historical reader: consume exactly the modulus size and ignore any trailing signature bytes.
    val size = key.modulus.bitLength() / 8
    if (signature.size < size) return false
    return verifier.verify(signature.copyOf(size))
}
