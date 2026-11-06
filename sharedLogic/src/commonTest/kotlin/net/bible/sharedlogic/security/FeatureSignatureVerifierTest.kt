package net.bible.sharedlogic.security

import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureSignatureVerifierTest {
    private val fs = FileSystem.SYSTEM
    private val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "sigtest-${Random.nextLong().toULong()}")
        .also { fs.createDirectories(it) }
    private val file = dir / "f.js"
    private val signature = dir / "f.js.sign"
    private val verifier = FeatureSignatureVerifier(FeatureSignatureVectors.publicKeyPem)

    private fun signed(content: String = "hello", bytes: ByteArray = FeatureSignatureVectors.signature): Pair<Path, Path> {
        fs.write(file) { writeUtf8(content) }
        fs.write(signature) { write(bytes) }
        return file to signature
    }

    @AfterTest fun cleanUp() { fs.deleteRecursively(dir) }

    @Test fun validSignatureAccepted() {
        val (f, s) = signed()
        assertTrue(verifier.verify(f, s))
    }

    @Test fun verifyBytesChecksTheGivenContentNotTheFileOnDisk() {
        val (_, s) = signed()
        fs.write(file) { writeUtf8("changed on disk") }
        assertTrue(verifier.verifyBytes("hello".encodeToByteArray(), s))
        assertFalse(verifier.verifyBytes("hellO".encodeToByteArray(), s))
        assertFalse(verifier.verifyBytes("hello".encodeToByteArray(), dir / "missing.sign"))
    }

    @Test fun emptySignedFileAccepted() {
        val (f, s) = signed("", FeatureSignatureVectors.emptySignature)
        assertTrue(verifier.verify(f, s))
    }

    @Test fun tamperedFileRefused() {
        val (f, s) = signed("hellO")
        assertFalse(verifier.verify(f, s))
    }

    @Test fun tamperedSignatureRefused() {
        val bytes = FeatureSignatureVectors.signature.also { it[0] = (it[0].toInt() xor 1).toByte() }
        val (f, s) = signed(bytes = bytes)
        assertFalse(verifier.verify(f, s))
    }

    @Test fun missingSignatureRefused() {
        val (f, s) = signed()
        fs.delete(s)
        assertFalse(verifier.verify(f, s))
    }

    @Test fun missingFileRefused() {
        val (f, s) = signed()
        fs.delete(f)
        assertFalse(verifier.verify(f, s))
    }

    @Test fun otherKeyRefused() {
        val (f, s) = signed()
        assertFalse(FeatureSignatureVerifier().verify(f, s))
    }

    @Test fun garbageKeyRefuses() {
        val (f, s) = signed()
        for (pem in listOf("not a key", "-----BEGIN PUBLIC KEY-----\nAQID\n-----END PUBLIC KEY-----")) {
            assertFalse(FeatureSignatureVerifier(pem).verify(f, s))
        }
    }

    @Test fun shortSignatureRefused() {
        for (bytes in listOf(ByteArray(0), FeatureSignatureVectors.signature.copyOf(255))) {
            val (f, s) = signed(bytes = bytes)
            assertFalse(verifier.verify(f, s))
        }
    }

    @Test fun signatureOfAnotherFileRefused() {
        val (f, s) = signed(bytes = FeatureSignatureVectors.emptySignature)
        assertFalse(verifier.verify(f, s))
    }

    @Test fun directoryRefused() {
        val (_, s) = signed()
        assertFalse(verifier.verify(dir, s))
    }

    @Test fun trailingSignatureBytesIgnoredLikeAndroid() {
        val (f, s) = signed(bytes = FeatureSignatureVectors.signature + byteArrayOf(1, 2, 3))
        assertTrue(verifier.verify(f, s))
    }
}
