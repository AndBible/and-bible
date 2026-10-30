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
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureSignatureVerifierTest {
    private val dir = Files.createTempDirectory("sigtest")
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val pem = "-----BEGIN PUBLIC KEY-----\n" +
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.public.encoded) + "\n-----END PUBLIC KEY-----\n"

    private fun signed(content: String): Pair<java.nio.file.Path, java.nio.file.Path> {
        val f = dir.resolve("f.js"); Files.writeString(f, content)
        val s = Signature.getInstance("SHA1withRSA").apply { initSign(pair.private); update(content.toByteArray()) }.sign()
        val sf = dir.resolve("f.js.sign"); Files.write(sf, s)
        return f to sf
    }

    @Test fun validSignatureAccepted() {
        val (f, sf) = signed("hello")
        assertTrue(FeatureSignatureVerifier(pem).verify(f, sf))
    }

    @Test fun tamperedFileRefused() {
        val (f, sf) = signed("hello")
        Files.writeString(f, "hellO")
        assertFalse(FeatureSignatureVerifier(pem).verify(f, sf))
    }

    @Test fun missingSignatureRefused() {
        val (f, sf) = signed("hello")
        Files.delete(sf)
        assertFalse(FeatureSignatureVerifier(pem).verify(f, sf))
    }

    @Test fun bundledPublicKeyParses() {
        val key = assertNotNull(FeatureSignatureVerifier().publicKey, "bundled publickey.pem must parse")
        assertEquals(2048, key.modulus.bitLength())
    }

    @Test fun otherKeyRefused() {
        val (f, sf) = signed("hello")
        assertFalse(FeatureSignatureVerifier().verify(f, sf))
    }

    @Test fun garbageKeyRefuses() {
        val (f, sf) = signed("hello")
        assertFalse(FeatureSignatureVerifier("not a key").verify(f, sf))
    }
}
