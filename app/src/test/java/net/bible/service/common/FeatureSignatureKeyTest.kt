package net.bible.service.common

import net.bible.sharedlogic.security.FeatureSignatureVerifier
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Android verifies add-on feature files with the common [FeatureSignatureVerifier]; its key constant must be the only release key. */
class FeatureSignatureKeyTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Gradle runs unit tests in the module directory. */
    private val rawDir = File("src/main/res/raw")

    @Test fun noSecondCopyOfTheReleaseKeyInResources() {
        val pems = rawDir.listFiles { f -> f.extension.equals("pem", true) }.orEmpty().map { it.name }
        assertEquals("a PEM beside the common FeatureSignatureVerifier key would be a second source of truth", emptyList<String>(), pems)
        assertTrue("the guard must look at the real res/raw directory", rawDir.isDirectory)
    }

    @Test fun noResourceHoldsAnotherPublicKey() {
        // Catches a renamed copy of the key (any extension) as well.
        val withKey = rawDir.walkTopDown().filter { it.isFile }
            .filter { runCatching { it.readText() }.getOrDefault("").contains("BEGIN PUBLIC KEY") }.map { it.name }.toList()
        assertEquals(emptyList<String>(), withKey)
    }

    @Test fun androidVerifierUsesTheCommonKeyAndFailsClosed() {
        val file = tmp.newFile("f.js").apply { writeText("hello") }
        val sig = tmp.newFile("f.js.sign").apply { writeBytes(ByteArray(256) { it.toByte() }) }
        assertFalse(FeatureSignatureVerifier().verify(file.toOkioPath(), sig.toOkioPath()))
        assertFalse(FeatureSignatureVerifier().verify(file.toOkioPath(), File(tmp.root, "missing.sign").toOkioPath()))
    }
}
