package net.bible.android.control.link

import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriAnalyzerTest {
    @Test
    fun dotlessStrongKeyIsParsedWithoutWarningAndAnalyzerCanBeReused() {
        val warnings = mutableListOf<String>()
        val oldSink = Log.sinkOverride
        Log.sinkOverride = { level, tag, message, _ ->
            if (level == LogLevel.WARN) warnings += "$tag:$message"
        }

        try {
            val analyzer = UriAnalyzer()
            assertTrue(analyzer.analyze("strong:H0835"))
            assertEquals(UriAnalyzer.DocType.HEBREW_DIC, analyzer.docType)
            assertEquals("H0835", analyzer.key)
            assertTrue(warnings.isEmpty(), "Valid Strong's links should not log parse warnings")

            warnings.clear()
            assertTrue(analyzer.analyze("strong:G3056"))
            assertEquals(UriAnalyzer.DocType.GREEK_DIC, analyzer.docType)
            assertEquals("G3056", analyzer.key)
            assertTrue(warnings.isEmpty(), "Valid Strong's links should not log parse warnings")
        } finally {
            Log.sinkOverride = oldSink
        }
    }

    @Test
    fun emptyStrongKeyReturnsFalse() {
        val analyzer = UriAnalyzer()
        assertFalse(analyzer.analyze("strong:"))
    }
}
