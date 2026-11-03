package net.bible.sharedcore.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LogFormatTest {
    /** `2026-11-04 12:34:56.789 [thread] D/Tag: message`, the shape every platform's report log uses. */
    private val linePattern = Regex("""^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3}) \[([^\]]+)\] ([VDIWE])/([^:]+): (.*)""")

    @Test fun lineHasTimestampThreadLevelAndTag() {
        val line = formatLogLine("2026-11-04 12:34:56.789", "main", LogLevel.DEBUG, "IosSwordEnvironment", "Main JSword path: x", null, emptyList())
        val m = assertNotNull(linePattern.find(line), line)
        assertEquals("main", m.groupValues[8])
        assertEquals("D", m.groupValues[9])
        assertEquals("IosSwordEnvironment", m.groupValues[10])
        assertEquals("Main JSword path: x", m.groupValues[11])
    }

    @Test fun stackTraceIsKeptAndNoTrailingNewline() {
        val line = formatLogLine("2026-11-04 12:34:56.789", "t", LogLevel.ERROR, "T", "boom", IllegalStateException("kaput"), emptyList())
        assertTrue("IllegalStateException: kaput" in line, line)
        assertTrue(line.lines().size > 1)
        assertFalse(line.endsWith("\n"))
    }

    @Test fun pathsUnderAliasesAreShortenedInMessageAndStackTrace() {
        val aliases = listOf("/Users/alice/Library/Containers/org.andbible.ios/Data" to "<container>", "/Users/alice" to "~")
        val line = formatLogLine("2026-11-04 12:34:56.789", "t", LogLevel.INFO, "T",
            "path /Users/alice/Library/Containers/org.andbible.ios/Data/Documents/x and /Users/alice/Desktop",
            IllegalStateException("at /Users/alice/Library/Containers/org.andbible.ios/Data/tmp"), aliases)
        assertFalse("alice" in line, line)
        assertTrue("<container>/Documents/x" in line, line)
        assertTrue("~/Desktop" in line, line)
    }

    @Test fun aliasOnlyMatchesWholePathSegments() {
        assertEquals("/home/bobby/x", shortenPaths("/home/bobby/x", listOf("/home/bob" to "~")))
        assertEquals("~/x ~", shortenPaths("/home/bob/x /home/bob", listOf("/home/bob" to "~")))
    }

    @Test fun reportFactsAreListedInInsertionOrderAndOverwritten() {
        val facts = ReportFacts()
        facts.set("Web engine", "a")
        facts.set("Locale", "fi-FI")
        facts.set("Web engine", "b")
        assertEquals(listOf("Web engine" to "b", "Locale" to "fi-FI"), facts.snapshot())
    }
}
