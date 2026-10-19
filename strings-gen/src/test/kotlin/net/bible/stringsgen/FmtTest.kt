package net.bible.stringsgen

import kotlin.test.Test
import kotlin.test.assertEquals

class FmtTest {
    private val d = '$'

    @Test fun unpositionalSpecsSubstituteInOrder() {
        assertEquals("5 documents", fmt("%d documents", 5))
        assertEquals("a-1-b", fmt("%s-%d-%s", "a", 1, "b"))
        assertEquals("Exported 3 prompts to CSV", fmt("Exported %d prompts to CSV", 3))
        assertEquals("1.5 MB", fmt("%.1f MB", 1.5))
        assertEquals("2.50", fmt("%1.2f", 2.5))
    }

    @Test fun positionalSpecsKeepTheirOwnIndex() {
        assertEquals("7 verses in 2 translations", fmt("%1${d}d verses in %2${d}d translations", 7, 2))
        assertEquals("b a", fmt("%2${d}s %1${d}s", "a", "b"))
        assertEquals("0xF8", fmt("0x%1${d}02X", 0xF8))
    }

    @Test fun literalPercentAndUnknownSpecsAreKept() {
        assertEquals("42% of Bible read", fmt("%s%% of Bible read", "42"))
        assertEquals("100% done", fmt("100% done"))
        assertEquals("50% a", fmt("50% a", 1))
        assertEquals("trailing %", fmt("trailing %"))
    }

    @Test fun missingArgumentsPrintAsEmpty() {
        assertEquals("x:", fmt("x:%s"))
        assertEquals("1 and ", fmt("%d and %d", 1))
    }

    @Test fun emittedCopyIsByteIdenticalToTheReference() {
        val source = java.io.File("src/main/kotlin/net/bible/stringsgen/IosStringsEmitter.kt").readText()
        val body = source.substringAfter("// FMT-BODY-START\n").substringBefore("// FMT-BODY-END")
            .trimEnd().removePrefix("internal ")
        assertEquals(body, FMT_FUNCTION_SOURCE)
    }
}
