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

import okio.FileSystem
import okio.Path
import okio.use
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

internal actual fun verifyFeatureSignature(publicKeyDer: ByteArray, file: Path, signatureFile: Path): Boolean {
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer)) as RSAPublicKey
    val verifier = Signature.getInstance("SHA1withRSA").apply { initVerify(key) }
    FileSystem.SYSTEM.source(file).use { source ->
        val buffer = okio.Buffer()
        while (source.read(buffer, 16 * 1024) != -1L) verifier.update(buffer.readByteArray())
    }
    // Match Android: consume exactly the modulus size and ignore any trailing signature bytes.
    val signature = FileSystem.SYSTEM.read(signatureFile) { readByteArray((key.modulus.bitLength() / 8).toLong()) }
    return verifier.verify(signature)
}
