package net.bible.sharedui.strings

import net.bible.sharedui.strings.keyed.formatTemplate

import kotlin.test.Test
import kotlin.test.assertEquals

class FmtTest {
    private fun fmt(template: String, vararg args: Any?): String = formatTemplate(template, args)
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

    @Test fun positionalAndUnpositionalCountersAreIndependentLikeJava() {
        // %2${d}s reads argument 2; the ordinary specs still start at argument 1 and advance on their own.
        assertEquals("5 a 5", fmt("%2${d}s %s %d", "a", 5))
        assertEquals("b: a/b", fmt("%2${d}s: %s/%s", "a", "b"))
        assertEquals("x 1 of 1 (x)", fmt("%s %d of %2${d}d (%1${d}s)", "x", 1, 9))
    }

    @Test fun unsupportedConversionsAndFlagsStayVerbatim() {
        assertEquals("\n %b 1 %e %,d %+d", fmt("%n %b %x %e %,d %+d", 1, 2, 3, 4, 5, 6))
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

}
