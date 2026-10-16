package net.bible.android.view.activity.page

import net.bible.sharedcore.bibleview.BibleViewEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Prevent Android events from bypassing the typed bridge or naming an unknown Vue event. */
class BibleViewEventNamesParityTest {
    @Test fun emittedNamesAreKnownToTheEventBus() {
        val eventBus = File("bibleview-js/src/eventbus.ts").readText()
        val known = Regex("""\|?\s*"([a-z_]+)"""").findAll(eventBus.substringAfter("type EventTypeNames =").substringBefore("export const"))
            .map { it.groupValues[1] }.toSet()
        assertTrue(known.size > 30)
        assertEquals(emptySet<String>(), BibleViewEvents.names - known)
    }

    @Test fun noHandWrittenEmitsRemain() {
        val src = File("src/main/java").walk().filter { it.extension == "kt" }
            .filter { hasRawBibleViewCall(it.readText()) }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), src)
    }

    @Test fun rawCallMatcherAllowsWhitespaceButNotTypedOrUnrelatedCalls() {
        listOf(
            "bibleView.emit('raw')",
            "bibleView.emit( \"raw\")",
            "bibleView.emit(\n  'raw')",
            "bibleView.emit(\n  \"raw\")",
            "bibleView.response( 1, null)",
            "bibleView.response(\n  1, null)",
        ).also { rawCalls ->
            assertEquals(emptyList<String>(), rawCalls.filterNot(::hasRawBibleViewCall))
        }

        listOf(
            "BibleViewEvents.emit(\"typed\")",
            "bibleView.emitTyped(\"typed\")",
            "other.response(1, null)",
        ).forEach { source ->
            assertTrue("Expected non-raw call to be allowed: $source", !hasRawBibleViewCall(source))
        }
    }

    private fun hasRawBibleViewCall(source: String): Boolean =
        Regex("""bibleView\.(emit\s*\(\s*['"]|response\s*\()""").containsMatchIn(source)
}
