package net.bible.android.view.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Spec 2026-10-08 API 36 §5: with predictive back on, these entry points are never called by the platform. */
class PredictiveBackGuardTest {
    private val mainSources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun stripComments(text: String): String {
        val noBlockComments = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL).replace(text, "")
        return noBlockComments.lines().joinToString("\n") { line ->
            val commentAt = line.indexOf("//")
            if (commentAt >= 0) line.substring(0, commentAt) else line
        }
    }

    @Test fun noOnBackPressedOverrideIsLeft() {
        val offenders = mainSources.filter { Regex("override\\s+fun\\s+onBackPressed").containsMatchIn(stripComments(it.readText())) }
        assertEquals(emptyList<String>(), offenders.map { it.path })
    }

    @Test fun noKeycodeBackIsLeft() {
        val offenders = mainSources.filter { stripComments(it.readText()).contains("KEYCODE_BACK") }
        assertEquals(emptyList<String>(), offenders.map { it.path })
    }

    @Test fun theManifestNoLongerOptsOutOfPredictiveBack() {
        val manifest = File("src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(manifest.contains("enableOnBackInvokedCallback"))
    }

    @Test fun theGuardSeesTheSourceTree() {
        assert(mainSources.size > 100) { "scanned ${mainSources.size} files: wrong working directory?" }
    }
}
